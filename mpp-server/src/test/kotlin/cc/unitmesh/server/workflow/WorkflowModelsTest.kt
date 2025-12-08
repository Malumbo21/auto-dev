package cc.unitmesh.server.workflow

import cc.unitmesh.server.workflow.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowModelsTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun `test WorkflowEvent serialization`() {
        val event = WorkflowEvent(
            id = "event-123",
            workflowId = "workflow-456",
            sequenceNumber = 1,
            eventType = "ToolCall",
            eventData = """{"toolName": "read-file", "params": "/path/to/file"}""",
            timestamp = 1699999999000L
        )

        val serialized = json.encodeToString(event)
        val deserialized = json.decodeFromString<WorkflowEvent>(serialized)

        assertEquals(event.id, deserialized.id)
        assertEquals(event.workflowId, deserialized.workflowId)
        assertEquals(event.sequenceNumber, deserialized.sequenceNumber)
        assertEquals(event.eventType, deserialized.eventType)
        assertEquals(event.eventData, deserialized.eventData)
    }

    @Test
    fun `test WorkflowCheckpoint serialization`() {
        val checkpoint = WorkflowCheckpoint(
            id = "checkpoint-1",
            workflowId = "workflow-1",
            sequenceNumber = 100,
            state = """{"status": "RUNNING"}""",
            createdAt = System.currentTimeMillis(),
            sizeBytes = 1024
        )

        val serialized = json.encodeToString(checkpoint)
        val deserialized = json.decodeFromString<WorkflowCheckpoint>(serialized)

        assertEquals(checkpoint.id, deserialized.id)
        assertEquals(checkpoint.sequenceNumber, deserialized.sequenceNumber)
        assertEquals(checkpoint.sizeBytes, deserialized.sizeBytes)
    }

    @Test
    fun `test WorkflowState serialization`() {
        val state = WorkflowState(
            workflowId = "workflow-1",
            status = WorkflowStatus.RUNNING,
            currentIteration = 5,
            maxIterations = 100,
            lastEventSequence = 50,
            agentSteps = listOf(
                SerializableAgentStep(
                    step = 1,
                    action = "read-file",
                    tool = "read-file",
                    success = true,
                    result = "File content..."
                )
            ),
            agentEdits = listOf(
                SerializableAgentEdit(
                    file = "/src/main.kt",
                    operation = "modify",
                    content = "new content"
                )
            ),
            pendingSignals = listOf("user_approval")
        )

        val serialized = json.encodeToString(state)
        val deserialized = json.decodeFromString<WorkflowState>(serialized)

        assertEquals(state.workflowId, deserialized.workflowId)
        assertEquals(state.status, deserialized.status)
        assertEquals(state.currentIteration, deserialized.currentIteration)
        assertEquals(1, deserialized.agentSteps.size)
        assertEquals("read-file", deserialized.agentSteps[0].action)
    }

    @Test
    fun `test WorkflowStatus values`() {
        assertEquals(6, WorkflowStatus.entries.size)
        assertTrue(WorkflowStatus.entries.contains(WorkflowStatus.PENDING))
        assertTrue(WorkflowStatus.entries.contains(WorkflowStatus.RUNNING))
        assertTrue(WorkflowStatus.entries.contains(WorkflowStatus.COMPLETED))
        assertTrue(WorkflowStatus.entries.contains(WorkflowStatus.FAILED))
        assertTrue(WorkflowStatus.entries.contains(WorkflowStatus.CANCELLED))
        assertTrue(WorkflowStatus.entries.contains(WorkflowStatus.PAUSED))
    }

    @Test
    fun `test WorkflowState initial factory method`() {
        val state = WorkflowState.initial("test-workflow")

        assertEquals("test-workflow", state.workflowId)
        assertEquals(WorkflowStatus.PENDING, state.status)
        assertEquals(0, state.currentIteration)
        assertEquals(0L, state.lastEventSequence)
        assertTrue(state.agentSteps.isEmpty())
        assertTrue(state.agentEdits.isEmpty())
        assertTrue(state.pendingSignals.isEmpty())
    }

    @Test
    fun `test StartWorkflowRequest serialization`() {
        val request = StartWorkflowRequest(
            projectId = "project-1",
            task = "Fix the bug in main.kt",
            userId = "user-1",
            maxIterations = 50
        )

        val serialized = json.encodeToString(request)
        val deserialized = json.decodeFromString<StartWorkflowRequest>(serialized)

        assertEquals(request.projectId, deserialized.projectId)
        assertEquals(request.task, deserialized.task)
        assertEquals(request.maxIterations, deserialized.maxIterations)
    }

    @Test
    fun `test WorkflowSignal serialization`() {
        val signal = WorkflowSignal(
            id = "signal-1",
            workflowId = "workflow-1",
            signalName = "user_approval",
            signalData = """{"approved": true, "comment": "Looks good"}""",
            receivedAt = System.currentTimeMillis(),
            processed = false
        )

        val serialized = json.encodeToString(signal)
        val deserialized = json.decodeFromString<WorkflowSignal>(serialized)

        assertEquals(signal.id, deserialized.id)
        assertEquals(signal.signalName, deserialized.signalName)
        assertEquals(signal.processed, deserialized.processed)
    }
}

