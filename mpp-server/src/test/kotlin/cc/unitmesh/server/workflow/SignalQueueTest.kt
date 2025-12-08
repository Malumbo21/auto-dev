package cc.unitmesh.server.workflow

import cc.unitmesh.server.workflow.models.WorkflowSignal
import cc.unitmesh.server.workflow.store.InMemorySignalQueue
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SignalQueueTest {

    @Test
    fun `test enqueue and poll signal`() = runBlocking {
        val queue = InMemorySignalQueue()
        val workflowId = "test-workflow-1"

        val signal = WorkflowSignal(
            id = "signal-1",
            workflowId = workflowId,
            signalName = "user_approval",
            signalData = """{"approved": true}""",
            receivedAt = System.currentTimeMillis()
        )

        queue.enqueue(workflowId, signal)

        val polled = queue.poll(workflowId)
        assertNotNull(polled)
        assertEquals("user_approval", polled.signalName)
    }

    @Test
    fun `test poll returns null when no signals`() = runBlocking {
        val queue = InMemorySignalQueue()
        val result = queue.poll("non-existent-workflow")
        assertNull(result)
    }

    @Test
    fun `test get unprocessed signals`() = runBlocking {
        val queue = InMemorySignalQueue()
        val workflowId = "test-workflow-2"

        queue.enqueue(workflowId, WorkflowSignal(
            id = "signal-1",
            workflowId = workflowId,
            signalName = "signal_a",
            signalData = "{}",
            receivedAt = System.currentTimeMillis()
        ))

        queue.enqueue(workflowId, WorkflowSignal(
            id = "signal-2",
            workflowId = workflowId,
            signalName = "signal_b",
            signalData = "{}",
            receivedAt = System.currentTimeMillis()
        ))

        val unprocessed = queue.getUnprocessedSignals(workflowId)
        assertEquals(2, unprocessed.size)
    }

    @Test
    fun `test mark signal as processed`() = runBlocking {
        val queue = InMemorySignalQueue()
        val workflowId = "test-workflow-3"

        queue.enqueue(workflowId, WorkflowSignal(
            id = "signal-1",
            workflowId = workflowId,
            signalName = "test_signal",
            signalData = "{}",
            receivedAt = System.currentTimeMillis()
        ))

        queue.markAsProcessed("signal-1")

        val unprocessed = queue.getUnprocessedSignals(workflowId)
        assertEquals(0, unprocessed.size)
    }

    @Test
    fun `test await signal with existing signal`() = runBlocking {
        val queue = InMemorySignalQueue()
        val workflowId = "test-workflow-4"

        // Pre-enqueue a signal
        queue.enqueue(workflowId, WorkflowSignal(
            id = "signal-1",
            workflowId = workflowId,
            signalName = "approval",
            signalData = """{"approved": true}""",
            receivedAt = System.currentTimeMillis()
        ))

        // Await should return immediately
        val signal = withTimeout(1000) {
            queue.await(workflowId, "approval", 5000)
        }

        assertEquals("approval", signal.signalName)
    }

    @Test
    fun `test await signal with delayed signal`() = runBlocking {
        val queue = InMemorySignalQueue()
        val workflowId = "test-workflow-5"

        // Start awaiting in background
        val awaiter = async {
            queue.await(workflowId, "delayed_signal", 5000)
        }

        // Delay and then send signal
        delay(100)
        queue.enqueue(workflowId, WorkflowSignal(
            id = "signal-1",
            workflowId = workflowId,
            signalName = "delayed_signal",
            signalData = """{"data": "test"}""",
            receivedAt = System.currentTimeMillis()
        ))

        val signal = withTimeout(2000) { awaiter.await() }
        assertEquals("delayed_signal", signal.signalName)
    }
}

