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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
            eventData = buildJsonObject { put("projectPath", projectPath); put("task", task) }.toString(),
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
                eventData = buildJsonObject { put("success", true); put("totalEvents", eventCount) }.toString(),
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
                eventData = buildJsonObject { put("error", e.message ?: "Unknown error") }.toString(),
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

    suspend fun sendSignal(workflowId: String, signalName: String, data: Map<String, String>) {
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
                buildJsonObject { put("current", event.current); put("max", event.max) }.toString()
            is AgentEvent.LLMResponseChunk -> "LLMResponseChunk" to
                buildJsonObject { put("chunk", event.chunk) }.toString()
            is AgentEvent.ToolCall -> "ToolCall" to
                buildJsonObject { put("toolName", event.toolName); put("params", event.params) }.toString()
            is AgentEvent.ToolResult -> "ToolResult" to
                buildJsonObject { put("toolName", event.toolName); put("success", event.success) }.toString()
            is AgentEvent.CloneLog -> "CloneLog" to
                buildJsonObject { put("message", event.message); put("isError", event.isError) }.toString()
            is AgentEvent.CloneProgress -> "CloneProgress" to
                buildJsonObject { put("stage", event.stage); put("progress", event.progress) }.toString()
            is AgentEvent.Error -> "Error" to buildJsonObject { put("message", event.message) }.toString()
            is AgentEvent.Complete -> "Complete" to
                buildJsonObject { put("success", event.success); put("message", event.message) }.toString()
        }
        return WorkflowEvent(UUID.randomUUID().toString(), workflowId, seq, eventType, eventData, System.currentTimeMillis())
    }
}

data class ResumeState(val workflowState: WorkflowState, val replayEvents: List<WorkflowEvent>, val lastSequence: Long)

