package cc.unitmesh.xiuper.fs.db

import cc.unitmesh.xiuper.fs.*
import kotlinx.datetime.Clock

class DbFsBackend(
    private val database: XiuperFsDatabase,
) : FsBackend, CapabilityAwareBackend {
    override val capabilities: BackendCapabilities = BackendCapabilities(
        supportsMkdir = true,
        supportsDelete = true
    )

    override suspend fun stat(path: FsPath): FsStat {
        val normalized = FsPath.of(path.value)
        if (normalized.value == "/") {
            return FsStat(path = FsPath("/"), isDirectory = true)
        }

        val node = database.fsNodeQueries.selectByPath(normalized.value).executeAsOneOrNull()
            ?: throw FsException(FsErrorCode.ENOENT, "No such path: ${normalized.value}")

        val isDir = node.isDir != 0L
        val size = if (isDir) null else (node.content?.size?.toLong() ?: 0L)
        return FsStat(path = normalized, isDirectory = isDir, size = size)
    }

    override suspend fun list(path: FsPath): List<FsEntry> {
        val normalized = FsPath.of(path.value)
        if (normalized.value != "/") {
            val node = database.fsNodeQueries.selectByPath(normalized.value).executeAsOneOrNull()
                ?: throw FsException(FsErrorCode.ENOENT, "No such path: ${normalized.value}")
            if (node.isDir == 0L) throw FsException(FsErrorCode.ENOTDIR, "Not a directory: ${normalized.value}")
        }

        val prefix = if (normalized.value == "/") "/" else normalized.value + "/"
        val like = prefix + "%"
        val nodes = database.fsNodeQueries.selectChildren(like).executeAsList()

        val children = LinkedHashMap<String, FsEntry>()
        for (node in nodes) {
            val p = node.path
            if (!p.startsWith(prefix)) continue
            val remainder = p.removePrefix(prefix)
            val firstSegment = remainder.substringBefore('/')
            if (firstSegment.isEmpty()) continue

            if (children.containsKey(firstSegment)) continue

            val childIsDir = remainder.contains('/') || (node.isDir != 0L)
            val entry = if (childIsDir) {
                FsEntry.Directory(name = firstSegment)
            } else {
                FsEntry.File(name = firstSegment, size = node.content?.size?.toLong())
            }
            children[firstSegment] = entry
        }

        return children.values.toList()
    }

    override suspend fun read(path: FsPath, options: ReadOptions): ReadResult {
        val normalized = FsPath.of(path.value)
        val node = database.fsNodeQueries.selectByPath(normalized.value).executeAsOneOrNull()
            ?: throw FsException(FsErrorCode.ENOENT, "No such path: ${normalized.value}")

        if (node.isDir != 0L) throw FsException(FsErrorCode.EISDIR, "Is a directory: ${normalized.value}")
        return ReadResult(bytes = node.content ?: ByteArray(0))
    }

    override suspend fun write(path: FsPath, content: ByteArray, options: WriteOptions): WriteResult {
        val normalized = FsPath.of(path.value)
        if (normalized.value == "/") throw FsException(FsErrorCode.EISDIR, "Cannot write to directory: /")

        val parent = normalized.parent() ?: FsPath("/")
        if (!isDirectory(parent)) throw FsException(FsErrorCode.ENOENT, "Parent does not exist: ${parent.value}")

        val existing = database.fsNodeQueries.selectByPath(normalized.value).executeAsOneOrNull()
        if (existing?.isDir != null && existing.isDir != 0L) {
            throw FsException(FsErrorCode.EISDIR, "Is a directory: ${normalized.value}")
        }

        database.fsNodeQueries.upsertNode(
            path = normalized.value,
            isDir = 0L,
            content = content,
            mtimeEpochMillis = Clock.System.now().toEpochMilliseconds(),
        )

        return WriteResult(ok = true)
    }

    override suspend fun mkdir(path: FsPath) {
        val normalized = FsPath.of(path.value)
        if (normalized.value == "/") throw FsException(FsErrorCode.EEXIST, "Already exists: /")

        if (exists(normalized)) throw FsException(FsErrorCode.EEXIST, "Already exists: ${normalized.value}")

        val parent = normalized.parent() ?: FsPath("/")
        if (!isDirectory(parent)) throw FsException(FsErrorCode.ENOENT, "Parent does not exist: ${parent.value}")

        database.fsNodeQueries.upsertNode(
            path = normalized.value,
            isDir = 1L,
            content = null,
            mtimeEpochMillis = Clock.System.now().toEpochMilliseconds(),
        )
    }

    override suspend fun delete(path: FsPath) {
        val normalized = FsPath.of(path.value)
        if (normalized.value == "/") throw FsException(FsErrorCode.EACCES, "Cannot delete root")

        val node = database.fsNodeQueries.selectByPath(normalized.value).executeAsOneOrNull()
            ?: throw FsException(FsErrorCode.ENOENT, "No such path: ${normalized.value}")

        if (node.isDir != 0L) {
            // Check if directory has any children
            // Use executeAsList().firstOrNull() to avoid exception when multiple children exist
            val anyChild = database.fsNodeQueries
                .selectChildren(normalized.value + "/%")
                .executeAsList()
                .firstOrNull()
            if (anyChild != null) throw FsException(FsErrorCode.ENOTEMPTY, "Directory not empty: ${normalized.value}")
        }

        database.fsNodeQueries.deleteByPath(normalized.value)
    }

    override suspend fun commit(path: FsPath): WriteResult {
        // SQLDelight driver handles transactions; backend APIs are per-op atomic.
        return WriteResult(ok = true)
    }

    private fun exists(path: FsPath): Boolean {
        if (path.value == "/") return true
        return database.fsNodeQueries.selectByPath(path.value).executeAsOneOrNull() != null
    }

    private fun isDirectory(path: FsPath): Boolean {
        if (path.value == "/") return true
        val node = database.fsNodeQueries.selectByPath(path.value).executeAsOneOrNull() ?: return false
        return node.isDir != 0L
    }
}

/**
 * Create a [DbFsBackend] with migration support.
 * Applies schema migrations if needed and initializes root directory.
 */
fun DbFsBackend(driverFactory: DatabaseDriverFactory): DbFsBackend {
    val database = createDatabase(driverFactory)
    // ensure root exists
    database.fsNodeQueries.upsertNode(
        path = "/",
        isDir = 1L,
        content = null,
        mtimeEpochMillis = Clock.System.now().toEpochMilliseconds(),
    )
    return DbFsBackend(database)
}
