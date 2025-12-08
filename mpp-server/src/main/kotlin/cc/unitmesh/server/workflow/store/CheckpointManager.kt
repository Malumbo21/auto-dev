package cc.unitmesh.server.workflow.store

import cc.unitmesh.server.workflow.models.WorkflowCheckpoint
import cc.unitmesh.server.workflow.models.WorkflowState

/**
 * CheckpointManager - 检查点管理器
 * 
 * 负责保存和恢复工作流状态快照，加速恢复过程
 */
interface CheckpointManager {
    /**
     * 保存检查点
     */
    suspend fun save(checkpoint: WorkflowCheckpoint)
    
    /**
     * 获取最新的检查点
     */
    suspend fun getLatest(workflowId: String): WorkflowCheckpoint?
    
    /**
     * 获取指定序列号之前的最新检查点
     */
    suspend fun getLatestBefore(workflowId: String, sequenceNumber: Long): WorkflowCheckpoint?
    
    /**
     * 获取所有检查点
     */
    suspend fun getAll(workflowId: String): List<WorkflowCheckpoint>
    
    /**
     * 删除旧检查点（保留最近 N 个）
     */
    suspend fun pruneOldCheckpoints(workflowId: String, keepCount: Int = 5)
    
    /**
     * 删除工作流的所有检查点
     */
    suspend fun deleteAll(workflowId: String)
    
    /**
     * 从检查点恢复状态
     */
    suspend fun restoreState(checkpoint: WorkflowCheckpoint): WorkflowState
}
