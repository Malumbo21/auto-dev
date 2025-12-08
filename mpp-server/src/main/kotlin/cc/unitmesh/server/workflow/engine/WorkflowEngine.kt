package cc.unitmesh.server.workflow.engine

import cc.unitmesh.server.workflow.models.*
import cc.unitmesh.server.workflow.store.CheckpointManager
import cc.unitmesh.server.workflow.store.EventStore
import cc.unitmesh.server.workflow.store.SignalQueue
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * WorkflowEngine - 工作流引擎
 * 
 * 核心组件，负责：
 * - 启动新工作流
 * - 从检查点恢复工作流
 * - 处理 Signal/Query/Update
 * - 调度 Agent 执行
 */
class WorkflowEngine(
    private val eventStore: EventStore,
    private val checkpointManager: CheckpointManager,
    private val signalQueue: SignalQueue,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    
    // 活跃工作流：workflowId -> Job
    private val activeWorkflows = ConcurrentHashMap<String, Job>()
    
    // 工作流状态缓存
    private val workflowStates = ConcurrentHashMap<String, WorkflowState>()
    
    // 工作流元数据缓存
    private val workflowMetadata = ConcurrentHashMap<String, WorkflowMetadata>()
    
    // 事件广播流
    private val eventFlows = ConcurrentHashMap<String, MutableSharedFlow<WorkflowEvent>>()
    
    /**
     * 启动新工作流
     */
    suspend fun startWorkflow(request: StartWorkflowRequest): StartWorkflowResponse {
        val workflowId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        // 创建初始状态
        val initialState = WorkflowState.initial(workflowId, request.maxIterations)
        workflowStates[workflowId] = initialState
        
        // 创建元数据
        val metadata = WorkflowMetadata(
            workflowId = workflowId,
            projectId = request.projectId,
            task = request.task,
            status = WorkflowStatus.PENDING,
            ownerId = request.userId,
            createdAt = now,
            updatedAt = now,
            metadata = request.metadata?.let { json.encodeToString(it) }
        )
        workflowMetadata[workflowId] = metadata
        
        // 记录 WorkflowStarted 事件
        val startEvent = WorkflowEvent(
            id = UUID.randomUUID().toString(),
            workflowId = workflowId,
            sequenceNumber = 1,
            eventType = "WorkflowStarted",
            eventData = json.encodeToString(request),
            timestamp = now
        )
        eventStore.appendEvent(startEvent)
        
        // 创建事件广播流
        eventFlows[workflowId] = MutableSharedFlow(replay = 100)
        
        logger.info { "Created workflow $workflowId for task: ${request.task}" }
        
        return StartWorkflowResponse(
            workflowId = workflowId,
            status = WorkflowStatus.PENDING,
            createdAt = now
        )
    }
    
    /**
     * 执行工作流（由外部调用启动实际执行）
     */
    suspend fun executeWorkflow(
        workflowId: String,
        executor: suspend (WorkflowState, WorkflowEventRecorder) -> WorkflowState
    ) {
        val state = workflowStates[workflowId] 
            ?: throw IllegalStateException("Workflow $workflowId not found")
        
        val job = scope.launch {
            try {
                // 更新状态为 RUNNING
                updateWorkflowStatus(workflowId, WorkflowStatus.RUNNING)
                
                // 创建事件记录器
                val recorder = WorkflowEventRecorder(workflowId, eventStore, eventFlows[workflowId])
                
                // 执行工作流逻辑
                val finalState = executor(state, recorder)
                
                // 更新最终状态
                workflowStates[workflowId] = finalState
                updateWorkflowStatus(workflowId, WorkflowStatus.COMPLETED)
                
                // 记录完成事件
                recorder.recordEvent("WorkflowCompleted", mapOf("success" to true))
                
            } catch (e: CancellationException) {
                logger.info { "Workflow $workflowId was cancelled" }
                updateWorkflowStatus(workflowId, WorkflowStatus.CANCELLED)
            } catch (e: Exception) {
                logger.error(e) { "Workflow $workflowId failed" }
                updateWorkflowStatus(workflowId, WorkflowStatus.FAILED)
                
                // 记录失败事件
                val recorder = WorkflowEventRecorder(workflowId, eventStore, eventFlows[workflowId])
                recorder.recordEvent("WorkflowFailed", mapOf("error" to (e.message ?: "Unknown error")))
            } finally {
                activeWorkflows.remove(workflowId)
            }
        }
        
        activeWorkflows[workflowId] = job
    }
    
    /**
     * 恢复工作流
     */
    suspend fun resumeWorkflow(workflowId: String): WorkflowState {
        logger.info { "Resuming workflow $workflowId" }
        
        // 1. 获取最新检查点
        val checkpoint = checkpointManager.getLatest(workflowId)
        
        val state = if (checkpoint != null) {
            // 2. 从检查点恢复
            val baseState = checkpointManager.restoreState(checkpoint)
            
            // 3. 重放检查点之后的事件
            val events = eventStore.getEvents(workflowId, fromSequence = checkpoint.sequenceNumber + 1)
            applyEvents(baseState, events)
        } else {
            // 全新工作流或没有检查点
            val events = eventStore.getEvents(workflowId)
            if (events.isEmpty()) {
                throw IllegalStateException("Workflow $workflowId has no events")
            }
            applyEvents(WorkflowState.initial(workflowId), events)
        }
        
        workflowStates[workflowId] = state
        logger.info { "Workflow $workflowId resumed at iteration ${state.currentIteration}" }
        
        return state
    }
    
    /**
     * 发送信号到工作流
     */
    suspend fun sendSignal(workflowId: String, signalName: String, signalData: Map<String, String>) {
        val signal = WorkflowSignal(
            id = UUID.randomUUID().toString(),
            workflowId = workflowId,
            signalName = signalName,
            signalData = json.encodeToString(signalData),
            receivedAt = System.currentTimeMillis()
        )
        
        // 记录信号事件
        val event = WorkflowEvent(
            id = UUID.randomUUID().toString(),
            workflowId = workflowId,
            sequenceNumber = eventStore.getLatestSequence(workflowId) + 1,
            eventType = "SignalReceived",
            eventData = json.encodeToString(signal),
            timestamp = System.currentTimeMillis()
        )
        eventStore.appendEvent(event)
        
        // 入队信号
        signalQueue.enqueue(workflowId, signal)
        
        logger.info { "Signal $signalName sent to workflow $workflowId" }
    }
    
    /**
     * 查询工作流状态
     */
    fun queryState(workflowId: String): WorkflowState? {
        return workflowStates[workflowId]
    }
    
    /**
     * 查询工作流元数据
     */
    fun queryMetadata(workflowId: String): WorkflowMetadata? {
        return workflowMetadata[workflowId]
    }
    
    /**
     * 订阅工作流事件
     */
    fun subscribeToEvents(workflowId: String): Flow<WorkflowEvent> {
        return eventFlows.getOrPut(workflowId) { 
            MutableSharedFlow(replay = 100) 
        }.asSharedFlow()
    }
    
    /**
     * 取消工作流
     */
    fun cancelWorkflow(workflowId: String) {
        activeWorkflows[workflowId]?.cancel()
        logger.info { "Workflow $workflowId cancellation requested" }
    }
    
    /**
     * 检查工作流是否活跃
     */
    fun isActive(workflowId: String): Boolean {
        return activeWorkflows[workflowId]?.isActive == true
    }
    
    private suspend fun updateWorkflowStatus(workflowId: String, status: WorkflowStatus) {
        workflowStates[workflowId]?.let { state ->
            workflowStates[workflowId] = state.copy(status = status)
        }
        workflowMetadata[workflowId]?.let { metadata ->
            workflowMetadata[workflowId] = metadata.copy(
                status = status,
                updatedAt = System.currentTimeMillis(),
                completedAt = if (status == WorkflowStatus.COMPLETED || status == WorkflowStatus.FAILED) {
                    System.currentTimeMillis()
                } else null
            )
        }
    }
    
    private fun applyEvents(initialState: WorkflowState, events: List<WorkflowEvent>): WorkflowState {
        var state = initialState
        
        events.forEach { event ->
            state = when (event.eventType) {
                "StepCompleted" -> {
                    val step = json.decodeFromString<SerializableAgentStep>(event.eventData)
                    state.copy(agentSteps = state.agentSteps + step)
                }
                "IterationCompleted" -> {
                    state.copy(currentIteration = state.currentIteration + 1)
                }
                "WorkflowStarted" -> {
                    state.copy(status = WorkflowStatus.RUNNING)
                }
                "WorkflowCompleted" -> {
                    state.copy(status = WorkflowStatus.COMPLETED)
                }
                "WorkflowFailed" -> {
                    state.copy(status = WorkflowStatus.FAILED)
                }
                "SignalReceived" -> {
                    val signal = json.decodeFromString<WorkflowSignal>(event.eventData)
                    state.copy(pendingSignals = state.pendingSignals + signal.signalName)
                }
                else -> state
            }.copy(lastEventSequence = event.sequenceNumber)
        }
        
        return state
    }
}

/**
 * 工作流事件记录器 - 用于在执行过程中记录事件
 */
class WorkflowEventRecorder(
    private val workflowId: String,
    private val eventStore: EventStore,
    private val eventFlow: MutableSharedFlow<WorkflowEvent>?
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    
    suspend fun recordEvent(eventType: String, data: Any) {
        val sequenceNumber = eventStore.getLatestSequence(workflowId) + 1
        val event = WorkflowEvent(
            id = UUID.randomUUID().toString(),
            workflowId = workflowId,
            sequenceNumber = sequenceNumber,
            eventType = eventType,
            eventData = json.encodeToString(data),
            timestamp = System.currentTimeMillis()
        )
        
        eventStore.appendEvent(event)
        eventFlow?.emit(event)
    }
}
