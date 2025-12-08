package cc.unitmesh.server.workflow

import cc.unitmesh.agent.AgentEvent
import cc.unitmesh.server.workflow.executor.DurableAgentExecutor
import cc.unitmesh.server.workflow.store.InMemorySignalQueue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DurableAgentExecutorTest {

    @Test
    fun `test execute with durability records events`() = runBlocking {
        val eventStore = InMemoryEventStore()
        val checkpointManager = InMemoryCheckpointManager()
        val signalQueue = InMemorySignalQueue()

        val executor = DurableAgentExecutor(
            eventStore = eventStore,
            checkpointManager = checkpointManager,
            signalQueue = signalQueue,
            checkpointInterval = 5
        )

        val workflowId = "test-workflow-1"
        val projectPath = "/test/project"
        val task = "Test task"

        val events = executor.executeWithDurability(
            workflowId = workflowId,
            projectPath = projectPath,
            task = task
        ) { onEvent ->
            // Simulate agent execution
            onEvent(AgentEvent.IterationStart(1, 10))
            onEvent(AgentEvent.LLMResponseChunk("Thinking..."))
            onEvent(AgentEvent.ToolCall("read-file", "/path/to/file"))
            onEvent(AgentEvent.ToolResult("read-file", true, "file content"))
            onEvent(AgentEvent.Complete(true, "Task completed", 1, emptyList(), emptyList()))
        }.toList()

        // Verify emitted events
        assertEquals(5, events.size)
        assertTrue(events[0] is AgentEvent.IterationStart)
        assertTrue(events[1] is AgentEvent.LLMResponseChunk)
        assertTrue(events[2] is AgentEvent.ToolCall)
        assertTrue(events[3] is AgentEvent.ToolResult)
        assertTrue(events[4] is AgentEvent.Complete)

        // Verify events were persisted
        val storedEvents = eventStore.getEvents(workflowId)
        assertTrue(storedEvents.size >= 7) // start + 5 agent events + complete
        assertEquals("ExecutionStarted", storedEvents.first().eventType)
        assertEquals("ExecutionCompleted", storedEvents.last().eventType)
    }

    @Test
    fun `test execute with durability handles errors`() = runBlocking {
        val eventStore = InMemoryEventStore()
        val checkpointManager = InMemoryCheckpointManager()
        val signalQueue = InMemorySignalQueue()

        val executor = DurableAgentExecutor(
            eventStore = eventStore,
            checkpointManager = checkpointManager,
            signalQueue = signalQueue
        )

        val workflowId = "test-workflow-2"

        var exceptionThrown = false
        try {
            executor.executeWithDurability(
                workflowId = workflowId,
                projectPath = "/test",
                task = "Failing task"
            ) { onEvent ->
                onEvent(AgentEvent.IterationStart(1, 10))
                throw RuntimeException("Simulated failure")
            }.toList()
        } catch (e: RuntimeException) {
            exceptionThrown = true
            assertEquals("Simulated failure", e.message)
        }

        assertTrue(exceptionThrown)

        // Verify failure event was recorded
        val storedEvents = eventStore.getEvents(workflowId)
        val failEvent = storedEvents.find { it.eventType == "ExecutionFailed" }
        assertTrue(failEvent != null)
    }

    @Test
    fun `test checkpoint is saved at intervals`() = runBlocking {
        val eventStore = InMemoryEventStore()
        val checkpointManager = InMemoryCheckpointManager()
        val signalQueue = InMemorySignalQueue()

        val executor = DurableAgentExecutor(
            eventStore = eventStore,
            checkpointManager = checkpointManager,
            signalQueue = signalQueue,
            checkpointInterval = 3 // Save checkpoint every 3 events
        )

        val workflowId = "test-workflow-3"

        executor.executeWithDurability(
            workflowId = workflowId,
            projectPath = "/test",
            task = "Test task"
        ) { onEvent ->
            // Emit 9 events to trigger 3 checkpoints
            for (i in 1..9) {
                onEvent(AgentEvent.LLMResponseChunk("chunk $i"))
            }
        }.toList()

        // Verify checkpoints were saved
        val checkpoints = checkpointManager.getAll(workflowId)
        assertTrue(checkpoints.size >= 3) // At least 3 interval checkpoints + final
    }

    @Test
    fun `test send and await signal`() = runBlocking {
        val eventStore = InMemoryEventStore()
        val checkpointManager = InMemoryCheckpointManager()
        val signalQueue = InMemorySignalQueue()

        val executor = DurableAgentExecutor(
            eventStore = eventStore,
            checkpointManager = checkpointManager,
            signalQueue = signalQueue
        )

        val workflowId = "test-workflow-4"

        // Send signal
        executor.sendSignal(workflowId, "user_approval", mapOf("approved" to "true"))

        // Await signal
        val signal = executor.awaitSignal(workflowId, "user_approval", 1000)
        assertEquals("user_approval", signal.signalName)
    }
}

