package cc.unitmesh.xiuper.fs

interface XiuperFileSystem {
    suspend fun stat(path: FsPath): FsStat
    suspend fun list(path: FsPath): List<FsEntry>
    suspend fun read(path: FsPath, options: ReadOptions = ReadOptions()): ReadResult
    suspend fun write(path: FsPath, content: ByteArray, options: WriteOptions = WriteOptions()): WriteResult
    suspend fun delete(path: FsPath)
    suspend fun mkdir(path: FsPath)

    /**
     * Explicit commit hook for backends that model fsync/commit triggers.
     *
     * For REST-FS it can be used to commit staged writes when commitMode == OnExplicitCommit.
     */
    suspend fun commit(path: FsPath): WriteResult = WriteResult(ok = true)
}

interface FsBackend {
    suspend fun stat(path: FsPath): FsStat
    suspend fun list(path: FsPath): List<FsEntry>
    suspend fun read(path: FsPath, options: ReadOptions): ReadResult
    suspend fun write(path: FsPath, content: ByteArray, options: WriteOptions): WriteResult
    suspend fun delete(path: FsPath)
    suspend fun mkdir(path: FsPath)
    suspend fun commit(path: FsPath): WriteResult = WriteResult(ok = true)
}

data class Mount(
    val mountPoint: FsPath,
    val backend: FsBackend,
    val readOnly: Boolean = false
)

class XiuperVfs(
    mounts: List<Mount>
) : XiuperFileSystem {
    private val normalizedMounts: List<Mount> = mounts
        .map { it.copy(mountPoint = FsPath.of(it.mountPoint.value)) }
        .sortedByDescending { it.mountPoint.value.length }

    override suspend fun stat(path: FsPath): FsStat {
        val (mount, inner) = resolve(path)
        return mount.backend.stat(inner)
    }

    override suspend fun list(path: FsPath): List<FsEntry> {
        val (mount, inner) = resolve(path)
        return mount.backend.list(inner)
    }

    override suspend fun read(path: FsPath, options: ReadOptions): ReadResult {
        val (mount, inner) = resolve(path)
        return mount.backend.read(inner, options)
    }

    override suspend fun write(path: FsPath, content: ByteArray, options: WriteOptions): WriteResult {
        val (mount, inner) = resolve(path)
        if (mount.readOnly) throw FsException(FsErrorCode.EACCES, "Mount is read-only: ${mount.mountPoint.value}")
        return mount.backend.write(inner, content, options)
    }

    override suspend fun delete(path: FsPath) {
        val (mount, inner) = resolve(path)
        if (mount.readOnly) throw FsException(FsErrorCode.EACCES, "Mount is read-only: ${mount.mountPoint.value}")
        mount.backend.delete(inner)
    }

    override suspend fun mkdir(path: FsPath) {
        val (mount, inner) = resolve(path)
        if (mount.readOnly) throw FsException(FsErrorCode.EACCES, "Mount is read-only: ${mount.mountPoint.value}")
        mount.backend.mkdir(inner)
    }

    override suspend fun commit(path: FsPath): WriteResult {
        val (mount, inner) = resolve(path)
        if (mount.readOnly) throw FsException(FsErrorCode.EACCES, "Mount is read-only: ${mount.mountPoint.value}")
        return mount.backend.commit(inner)
    }

    private fun resolve(path: FsPath): Pair<Mount, FsPath> {
        val normalized = FsPath.of(path.value)
        val mount = normalizedMounts.firstOrNull { isUnderMount(normalized, it.mountPoint) }
            ?: throw FsException(FsErrorCode.ENOENT, "No mount for path: ${normalized.value}")

        val inner = stripMount(normalized, mount.mountPoint)
        return mount to inner
    }

    private fun isUnderMount(path: FsPath, mountPoint: FsPath): Boolean {
        if (mountPoint.value == "/") return true
        return path.value == mountPoint.value || path.value.startsWith(mountPoint.value + "/")
    }

    private fun stripMount(path: FsPath, mountPoint: FsPath): FsPath {
        if (mountPoint.value == "/") return path
        if (path.value == mountPoint.value) return FsPath("/")
        val inner = path.value.removePrefix(mountPoint.value)
        return FsPath.of(inner)
    }
}
