package cc.unitmesh.xiuper.fs.memory

import cc.unitmesh.xiuper.fs.*

/**
 * Reference backend for the POSIX-subset contract.
 *
 * - Pure in-memory tree.
 * - Strict parent-exists semantics (no implicit mkdir -p).
 * - write() is create-or-truncate.
 * - delete() removes file or empty directory.
 */
class InMemoryFsBackend : FsBackend, CapabilityAwareBackend {
    override val capabilities: BackendCapabilities = BackendCapabilities(
        supportsMkdir = true,
        supportsDelete = true
    )

    private sealed interface Node {
        data class Dir(val children: MutableMap<String, Node>) : Node
        data class File(var bytes: ByteArray) : Node
    }

    private val root: Node.Dir = Node.Dir(mutableMapOf())

    override suspend fun stat(path: FsPath): FsStat {
        val normalized = FsPath.of(path.value)
        val node = getNode(normalized) ?: throw FsException(FsErrorCode.ENOENT, "No such path: ${normalized.value}")
        return when (node) {
            is Node.Dir -> FsStat(normalized, isDirectory = true)
            is Node.File -> FsStat(normalized, isDirectory = false, size = node.bytes.size.toLong())
        }
    }

    override suspend fun list(path: FsPath): List<FsEntry> {
        val normalized = FsPath.of(path.value)
        val node = getNode(normalized) ?: throw FsException(FsErrorCode.ENOENT, "No such path: ${normalized.value}")
        val dir = node as? Node.Dir ?: throw FsException(FsErrorCode.ENOTDIR, "Not a directory: ${normalized.value}")
        return dir.children.map { (name, child) ->
            when (child) {
                is Node.Dir -> FsEntry.Directory(name)
                is Node.File -> FsEntry.File(name, size = child.bytes.size.toLong())
            }
        }
    }

    override suspend fun read(path: FsPath, options: ReadOptions): ReadResult {
        val normalized = FsPath.of(path.value)
        val node = getNode(normalized) ?: throw FsException(FsErrorCode.ENOENT, "No such path: ${normalized.value}")
        val file = node as? Node.File ?: throw FsException(FsErrorCode.EISDIR, "Is a directory: ${normalized.value}")
        // Return defensive copy to prevent external mutation
        return ReadResult(bytes = file.bytes.copyOf())
    }

    override suspend fun write(path: FsPath, content: ByteArray, options: WriteOptions): WriteResult {
        val normalized = FsPath.of(path.value)
        if (normalized.value == "/") throw FsException(FsErrorCode.EISDIR, "Is a directory: /")

        val (parentDir, name) = resolveParentDir(normalized)
            ?: throw FsException(FsErrorCode.ENOENT, "No such parent: ${normalized.parent()?.value ?: "/"}")

        val existing = parentDir.children[name]
        when (existing) {
            is Node.Dir -> throw FsException(FsErrorCode.EISDIR, "Is a directory: ${normalized.value}")
            is Node.File -> {
                // Store defensive copy to prevent external mutation
                existing.bytes = content.copyOf()
                return WriteResult(ok = true)
            }
            null -> {
                // Store defensive copy to prevent external mutation
                parentDir.children[name] = Node.File(content.copyOf())
                return WriteResult(ok = true)
            }
        }
    }

    override suspend fun delete(path: FsPath) {
        val normalized = FsPath.of(path.value)
        if (normalized.value == "/") throw FsException(FsErrorCode.EACCES, "Cannot delete root")

        val parent = normalized.parent() ?: FsPath("/")
        val parentNode = getNode(parent) ?: throw FsException(FsErrorCode.ENOENT, "No such path: ${normalized.value}")
        val parentDir = parentNode as? Node.Dir ?: throw FsException(FsErrorCode.ENOTDIR, "Not a directory: ${parent.value}")

        val name = normalized.segments().lastOrNull() ?: throw FsException(FsErrorCode.EINVAL, "Invalid path: ${normalized.value}")
        val existing = parentDir.children[name] ?: throw FsException(FsErrorCode.ENOENT, "No such path: ${normalized.value}")

        when (existing) {
            is Node.File -> parentDir.children.remove(name)
            is Node.Dir -> {
                if (existing.children.isNotEmpty()) throw FsException(FsErrorCode.ENOTEMPTY, "Directory not empty: ${normalized.value}")
                parentDir.children.remove(name)
            }
        }
    }

    override suspend fun mkdir(path: FsPath) {
        val normalized = FsPath.of(path.value)
        if (normalized.value == "/") throw FsException(FsErrorCode.EEXIST, "Already exists: /")

        val (parentDir, name) = resolveParentDir(normalized)
            ?: throw FsException(FsErrorCode.ENOENT, "No such parent: ${normalized.parent()?.value ?: "/"}")

        if (parentDir.children.containsKey(name)) throw FsException(FsErrorCode.EEXIST, "Already exists: ${normalized.value}")
        parentDir.children[name] = Node.Dir(mutableMapOf())
    }

    override suspend fun commit(path: FsPath): WriteResult = WriteResult(ok = true)

    private fun getNode(path: FsPath): Node? {
        if (path.value == "/") return root
        var current: Node = root
        for (seg in path.segments()) {
            val dir = current as? Node.Dir ?: return null
            current = dir.children[seg] ?: return null
        }
        return current
    }

    private fun resolveParentDir(path: FsPath): Pair<Node.Dir, String>? {
        val parent = path.parent() ?: return null
        val name = path.segments().lastOrNull() ?: return null

        val parentNode = getNode(parent) ?: return null
        val parentDir = parentNode as? Node.Dir
            ?: throw FsException(FsErrorCode.ENOTDIR, "Not a directory: ${parent.value}")

        return parentDir to name
    }
}
