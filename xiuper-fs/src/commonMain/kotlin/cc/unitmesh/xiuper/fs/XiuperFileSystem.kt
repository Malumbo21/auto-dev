package cc.unitmesh.xiuper.fs

import cc.unitmesh.xiuper.fs.policy.*
import kotlinx.datetime.Clock

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
    val readOnly: Boolean = false,
    val policy: MountPolicy = MountPolicy.AllowAll
)

class XiuperVfs(
    mounts: List<Mount>,
    private val auditCollector: FsAuditCollector = FsAuditCollector.NoOp
) : XiuperFileSystem {
    private val normalizedMounts: List<Mount> = mounts
        .map { it.copy(mountPoint = FsPath.of(it.mountPoint.value)) }
        .sortedByDescending { it.mountPoint.value.length }

    override suspend fun stat(path: FsPath): FsStat {
        return withAudit(FsOperation.STAT, path) { mount, inner ->
            mount.backend.stat(inner)
        }
    }

    override suspend fun list(path: FsPath): List<FsEntry> {
        return withAudit(FsOperation.LIST, path) { mount, inner ->
            mount.backend.list(inner)
        }
    }

    override suspend fun read(path: FsPath, options: ReadOptions): ReadResult {
        return withAudit(FsOperation.READ, path) { mount, inner ->
            mount.backend.read(inner, options)
        }
    }

    override suspend fun write(path: FsPath, content: ByteArray, options: WriteOptions): WriteResult {
        return withAudit(FsOperation.WRITE, path) { mount, inner ->
            checkWritePolicy(mount, path)
            mount.backend.write(inner, content, options)
        }
    }

    override suspend fun delete(path: FsPath) {
        withAudit(FsOperation.DELETE, path) { mount, inner ->
            checkWritePolicy(mount, path)
            mount.policy.checkDelete(path)?.let { throw it }
            mount.backend.delete(inner)
        }
    }

    override suspend fun mkdir(path: FsPath) {
        withAudit(FsOperation.MKDIR, path) { mount, inner ->
            checkWritePolicy(mount, path)
            mount.backend.mkdir(inner)
        }
    }

    override suspend fun commit(path: FsPath): WriteResult {
        return withAudit(FsOperation.COMMIT, path) { mount, inner ->
            checkWritePolicy(mount, path)
            mount.backend.commit(inner)
        }
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
    
    private suspend fun checkWritePolicy(mount: Mount, path: FsPath) {
        if (mount.readOnly) {
            throw FsException(FsErrorCode.EACCES, "Mount is read-only: ${mount.mountPoint.value}")
        }
        mount.policy.checkWrite(path)?.let { throw it }
    }
    
    private suspend fun <T> withAudit(
        operation: FsOperation,
        path: FsPath,
        block: suspend (Mount, FsPath) -> T
    ): T {
        val (mount, inner) = resolve(path)
        val startTime = Clock.System.now()
        
        return try {
            val result = block(mount, inner)
            val endTime = Clock.System.now()
            val latency = (endTime - startTime).inWholeMilliseconds
            
            auditCollector.collect(
                FsAuditEvent(
                    operation = operation,
                    path = path,
                    backend = mount.backend::class.simpleName ?: "Unknown",
                    status = FsOperationStatus.Success,
                    latencyMs = latency
                )
            )
            
            result
        } catch (e: FsException) {
            val endTime = Clock.System.now()
            val latency = (endTime - startTime).inWholeMilliseconds
            
            auditCollector.collect(
                FsAuditEvent(
                    operation = operation,
                    path = path,
                    backend = mount.backend::class.simpleName ?: "Unknown",
                    status = FsOperationStatus.Failure(e.code, e.message ?: "Unknown error"),
                    latencyMs = latency
                )
            )
            
            throw e
        }
    }

    private fun stripMount(path: FsPath, mountPoint: FsPath): FsPath {
        if (mountPoint.value == "/") return path
        if (path.value == mountPoint.value) return FsPath("/")
        val inner = path.value.removePrefix(mountPoint.value)
        return FsPath.of(inner)
    }
}
