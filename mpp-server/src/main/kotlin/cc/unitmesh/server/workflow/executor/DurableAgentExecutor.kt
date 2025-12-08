package cc.unitmesh.server.workflow.executor

import cc.unitmesh.agent.AgentEvent
import cc.unitmesh.server.workflow.models.*
import cc.unitmesh.server.workflow.store.EventStore
import cc.unitmesh.server.workflow.store.CheckpointManager
import cc.unitmesh.server.workflow.store.SignalQueue
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val logger = KotlinLogging.logger {}

class DurableAgentExecutor(
    private val eventStore: EventStore,
    private val checkpointManager: CheckpointManager,
    private val signalQueue: SignalQueue,
    private val checkpointInterval: Int = 10
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun executeWithDurability(
        workflowId: String,
        projectPath: String,
        task: String,
        agentExecutor: suspend (onEvent: suspend (AgentEvent) -> Unit) -> Unit
    ): Flow<AgentEvent> = flow {
        logger.info { "Starting durable execution for workflow $workflowId" }
        var sequenceNumber = eventStore.getLatestSequence(workflowId)
        var eventCount = 0

        sequenceNumber++
        val startEvent = WorkflowEvent(
            id = UUID.randomUUID().toString(),
            workflowId = workflowId,
            sequenceNumber = sequenceNumber,
            eventType = "ExecutionStarted",
            eventData = json.encodeToString(mapOf("projectPath" to projectPath, "task" to task)),
            timestamp = System.currentTimeMillis()
        )
        eventStore.appendEvent(startEvent)

        try {
            agentExecutor { agentEvent ->
                sequenceNumber++
                eventCount++
                val workflowEvent = convertToWorkflowEvent(workflowId, sequenceNumber, agentEvent)
                eventStore.appendEvent(workflowEvent)
                emit(agentEvent)
                if (eventCount % checkpointInterval == 0) {
                    saveCheckpoint(workflowId, sequenceNumber)
                }
            }

            sequenceNumber++
            val completeEvent = WorkflowEvent(
                id = UUID.randomUUID().toString(),
                workflowId = workflowId,
                sequenceNumber = sequenceNumber,
                eventType = "ExecutionCompleted",
                eventData = json.encodeToString(mapOf("success" to true, "totalEvents" to eventCount)),
                timestamp = System.currentTimeMillis()
            )
            eventStore.appendEvent(completeEvent)
            saveCheckpoint(workflowId, sequenceNumber)
            logger.info { "Workflow $workflowId completed with $eventCount events" }
        } catch (e: Exception) {
            logger.error(e) { "Workflow $workflowId failed" }
            sequenceNumber++
            val failEvent = WorkflowEvent(
                id = UUID.randomUUID().toString(),
                workflowId = workflowId,
                sequenceNumber = sequenceNumber,
                eventType = "ExecutionFailed",
                eventData = json.encodeToString(mapOf("error" to (e.message ?: "Unknown error"))),
                timestamp = System.currentTimeMillis()
            )
            eventStore.appendEvent(failEvent)
            emit(AgentEvent.Error(e.message ?: "Unknown error"))
            throw e
        }
    }

    suspend fun awaitSignal(workflowId: String, signalName: String, timeoutMs: Long): WorkflowSignal {
        logger.info { "Workflow $workflowId waiting for signal: $signalName" }
        return signalQueue.await(workflowId, signalName, timeoutMs)
    }

    suspend fun sendSignal(workflowId: String, signalName: String, data: Map<String, Any>) {
        val signal = WorkflowSignal(
            id = UUID.randomUUID().toString(),
            workflowId = workflowId,
            signalName = signalName,
            signalData = json.encodeToString(data),
            receivedAt = System.currentTimeMillis()
        )
        signalQueue.enqueue(workflowId, signal)
        logger.info { "Signal $signalName sent to workflow $workflowId" }
    }

    private suspend fun saveCheckpoint(workflowId: String, sequenceNumber: Long) {
        val state = WorkflowState(
            workflowId = workflowId,
            status = WorkflowStatus.RUNNING,
            currentIteration = 0,
            maxIterations = 100,
            lastEventSequence = sequenceNumber,
            agentSteps = emptyList(),
            agentEdits = emptyList(),
            pendingSignals = emptyList()
        )
        val checkpoint = WorkflowCheckpoint(
            id = UUID.randomUUID().toString(),
            workflowId = workflowId,
            sequenceNumber = sequenceNumber,
            state = json.encodeToString(state),
            createdAt = System.currentTimeMillis(),
            sizeBytes = 0
        )
        checkpointManager.save(checkpoint)
        logger.debug { "Saved checkpoint for workflow $workflowId at sequence $sequenceNumber" }
    }

    private fun convertToWorkflowEvent(workflowId: String, seq: Long, event: AgentEvent): WorkflowEvent {
        val (eventType, eventData) = when (event) {
            is AgentEvent.IterationStart -> "IterationStart" to 
                json.encodeToString(mapOf("current" to event.current, "max" to event.max))
            is AgentEvent.LLMResponseChunk -> "LLMResponseChunk" to 
                json.encodeToString(mapOf("chunk" to event.chunk))
            is AgentEvent.ToolCall -> "ToolCall" to 
                json.encodeToString(mapOf("toolName" to event.toolName, "params" to event.params))
            is AgentEvent.ToolResult -> "ToolResult" to 
                json.encodeToString(mapOf("toolName" to event.toolName, "success" to event.success))
            is AgentEvent.CloneLog -> "CloneLog" to 
                json.encodeToString(mapOf("message" to event.message, "isError" to event.isError))
            is AgentEvent.CloneProgress -> "CloneProgress" to 
                json.encodeToString(mapOf("stage" to event.stage, "progress" to event.progress))
            is AgentEvent.Error -> "Error" to json.encodeToString(mapOf("message" to event.message))
            is AgentEvent.Complete -> "Complete" to 
                json.encodeToString(mapOf("success" to event.success, "message" to event.message))
        }
        return WorkflowEvent(UUID.randomUUID().toString(), workflowId, seq, eventType, eventData, System.currentTimeMillis())
    }
}

data class ResumeState(val workflowState: WorkflowState, val replayEvents: List<WorkflowEvent>, val lastSequence: Long)

