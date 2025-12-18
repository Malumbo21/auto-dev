package cc.unitmesh.xiuper.fs.policy

import cc.unitmesh.xiuper.fs.FsErrorCode
import cc.unitmesh.xiuper.fs.FsPath
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class FsAuditEvent(
    val operation: FsOperation,
    val path: FsPath,
    val backend: String,
    val status: FsOperationStatus,
    val latencyMs: Long,
    val timestamp: Instant = Clock.System.now(),
    val metadata: Map<String, String> = emptyMap()
)

enum class FsOperation {
    STAT,
    LIST,
    READ,
    WRITE,
    DELETE,
    MKDIR,
    COMMIT
}

sealed class FsOperationStatus {
    data object Success : FsOperationStatus()
    data class Failure(val errorCode: FsErrorCode, val message: String) : FsOperationStatus()
}

interface FsAuditCollector {
    fun collect(event: FsAuditEvent)
    
    companion object {
        val NoOp = object : FsAuditCollector {
            override fun collect(event: FsAuditEvent) {}
        }
        
        val Console = object : FsAuditCollector {
            override fun collect(event: FsAuditEvent) {
                println("[FS-AUDIT] ${event.operation} ${event.path.value} -> ${event.status} (${event.latencyMs}ms)")
            }
        }
    }
}
