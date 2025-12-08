package cc.unitmesh.server.workflow.models

import kotlinx.serialization.Serializable

/**
 * 工作流状态枚举
 */
@Serializable
enum class WorkflowStatus {
    PENDING,      // 等待执行
    RUNNING,      // 执行中
    PAUSED,       // 暂停（等待信号）
    COMPLETED,    // 完成
    FAILED,       // 失败
    CANCELLED     // 取消
}

/**
 * 工作流事件 - 事件溯源的核心
 */
@Serializable
data class WorkflowEvent(
    val id: String,                  // UUID
    val workflowId: String,          // 工作流 ID
    val sequenceNumber: Long,        // 序列号（从 1 开始）
    val eventType: String,           // 事件类型
    val eventData: String,           // JSON 序列化的事件数据
    val timestamp: Long,             // 时间戳
    val checkpointId: String? = null // 关联的检查点 ID（如果有）
)

/**
 * 工作流检查点 - 状态快照
 */
@Serializable
data class WorkflowCheckpoint(
    val id: String,                  // UUID
    val workflowId: String,
    val sequenceNumber: Long,        // 对应的事件序列号
    val state: String,               // JSON 序列化的状态
    val createdAt: Long,
    val sizeBytes: Int = 0
)

/**
 * 工作流信号 - 外部与工作流交互
 */
@Serializable
data class WorkflowSignal(
    val id: String,                  // UUID
    val workflowId: String,
    val signalName: String,          // 信号名称
    val signalData: String,          // JSON 序列化的信号数据
    val receivedAt: Long,
    val processed: Boolean = false,
    val processedAt: Long? = null
)

/**
 * 工作流元数据
 */
@Serializable
data class WorkflowMetadata(
    val workflowId: String,
    val projectId: String,
    val task: String,
    val status: WorkflowStatus,
    val ownerId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val metadata: String? = null,    // 额外的 JSON 元数据
    val parentWorkflowId: String? = null,
    val version: String? = null
)

/**
 * 工作流状态 - 用于检查点和恢复
 */
@Serializable
data class WorkflowState(
    val workflowId: String,
    val status: WorkflowStatus,
    val currentIteration: Int,
    val maxIterations: Int,
    val conversationHistory: List<SerializableMessage> = emptyList(),
    val agentSteps: List<SerializableAgentStep> = emptyList(),
    val agentEdits: List<SerializableAgentEdit> = emptyList(),
    val pendingSignals: List<String> = emptyList(),
    val customState: Map<String, String> = emptyMap(),
    val lastEventSequence: Long = 0
) {
    companion object {
        fun initial(workflowId: String, maxIterations: Int = 100): WorkflowState {
            return WorkflowState(
                workflowId = workflowId,
                status = WorkflowStatus.PENDING,
                currentIteration = 0,
                maxIterations = maxIterations
            )
        }
    }
}

/**
 * 可序列化的消息（用于对话历史）
 */
@Serializable
data class SerializableMessage(
    val role: String,
    val content: String
)

/**
 * 可序列化的 Agent 步骤
 */
@Serializable
data class SerializableAgentStep(
    val step: Int,
    val action: String,
    val tool: String? = null,
    val params: Map<String, String> = emptyMap(),
    val result: String? = null,
    val success: Boolean
)

/**
 * 可序列化的 Agent 编辑
 */
@Serializable
data class SerializableAgentEdit(
    val file: String,
    val operation: String,
    val content: String? = null
)

/**
 * 启动工作流请求
 */
@Serializable
data class StartWorkflowRequest(
    val projectId: String,
    val task: String,
    val userId: String,
    val maxIterations: Int = 100,
    val metadata: Map<String, String>? = null,
    val gitUrl: String? = null,
    val branch: String? = null
)

/**
 * 启动工作流响应
 */
@Serializable
data class StartWorkflowResponse(
    val workflowId: String,
    val status: WorkflowStatus,
    val createdAt: Long
)
