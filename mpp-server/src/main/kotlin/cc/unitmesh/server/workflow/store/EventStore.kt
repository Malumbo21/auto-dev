package cc.unitmesh.server.workflow.store

import cc.unitmesh.server.workflow.models.WorkflowEvent

/**
 * EventStore - 事件溯源存储接口
 * 
 * 负责持久化所有工作流事件，支持事件重放和恢复
 */
interface EventStore {
    /**
     * 追加事件到事件流
     * @return 分配的序列号
     */
    suspend fun appendEvent(event: WorkflowEvent): Long
    
    /**
     * 批量追加事件
     */
    suspend fun appendEvents(events: List<WorkflowEvent>): List<Long>
    
    /**
     * 获取工作流的所有事件
     * @param workflowId 工作流 ID
     * @param fromSequence 起始序列号（包含）
     * @param toSequence 结束序列号（包含），null 表示到最新
     */
    suspend fun getEvents(
        workflowId: String, 
        fromSequence: Long = 0,
        toSequence: Long? = null
    ): List<WorkflowEvent>
    
    /**
     * 获取工作流的最新序列号
     */
    suspend fun getLatestSequence(workflowId: String): Long
    
    /**
     * 获取指定类型的事件
     */
    suspend fun getEventsByType(
        workflowId: String, 
        eventType: String
    ): List<WorkflowEvent>
    
    /**
     * 删除工作流的所有事件（用于清理）
     */
    suspend fun deleteEvents(workflowId: String)
    
    /**
     * 获取事件数量
     */
    suspend fun getEventCount(workflowId: String): Long
}
