package cc.unitmesh.server.workflow.store

import cc.unitmesh.server.workflow.models.WorkflowSignal
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * SignalQueue - 信号队列接口
 * 
 * 负责管理工作流信号的发送和接收
 */
interface SignalQueue {
    /**
     * 入队信号
     */
    suspend fun enqueue(workflowId: String, signal: WorkflowSignal)
    
    /**
     * 轮询信号（非阻塞）
     */
    suspend fun poll(workflowId: String): WorkflowSignal?
    
    /**
     * 等待指定信号（阻塞）
     */
    suspend fun await(workflowId: String, signalName: String, timeoutMs: Long): WorkflowSignal
    
    /**
     * 获取未处理的信号
     */
    suspend fun getUnprocessedSignals(workflowId: String): List<WorkflowSignal>
    
    /**
     * 标记信号为已处理
     */
    suspend fun markAsProcessed(signalId: String)
}

/**
 * 内存实现的信号队列（用于开发和测试）
 */
class InMemorySignalQueue : SignalQueue {
    private val signals = ConcurrentHashMap<String, MutableList<WorkflowSignal>>()
    private val channels = ConcurrentHashMap<String, Channel<WorkflowSignal>>()
    
    override suspend fun enqueue(workflowId: String, signal: WorkflowSignal) {
        // 持久化到内存
        signals.computeIfAbsent(workflowId) { mutableListOf() }.add(signal)
        
        // 通知等待者
        channels[workflowId]?.trySend(signal)
    }
    
    override suspend fun poll(workflowId: String): WorkflowSignal? {
        val workflowSignals = signals[workflowId] ?: return null
        return workflowSignals.firstOrNull { !it.processed }
    }
    
    override suspend fun await(workflowId: String, signalName: String, timeoutMs: Long): WorkflowSignal {
        // 先检查是否已有信号
        val existing = signals[workflowId]?.firstOrNull { 
            it.signalName == signalName && !it.processed 
        }
        if (existing != null) {
            markAsProcessed(existing.id)
            return existing
        }
        
        // 创建 Channel 等待
        val channel = channels.getOrPut(workflowId) { Channel(Channel.BUFFERED) }
        
        return withTimeout(timeoutMs) {
            while (true) {
                val signal = channel.receive()
                if (signal.signalName == signalName) {
                    markAsProcessed(signal.id)
                    return@withTimeout signal
                }
            }
            @Suppress("UNREACHABLE_CODE")
            throw IllegalStateException("Unreachable")
        }
    }
    
    override suspend fun getUnprocessedSignals(workflowId: String): List<WorkflowSignal> {
        return signals[workflowId]?.filter { !it.processed } ?: emptyList()
    }
    
    override suspend fun markAsProcessed(signalId: String) {
        signals.values.forEach { list ->
            list.replaceAll { signal ->
                if (signal.id == signalId) {
                    signal.copy(processed = true, processedAt = System.currentTimeMillis())
                } else {
                    signal
                }
            }
        }
    }
}
