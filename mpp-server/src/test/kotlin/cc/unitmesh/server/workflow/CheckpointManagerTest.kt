package cc.unitmesh.server.workflow

import cc.unitmesh.server.workflow.models.WorkflowCheckpoint
import cc.unitmesh.server.workflow.models.WorkflowState
import cc.unitmesh.server.workflow.models.WorkflowStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CheckpointManagerTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun createCheckpoint(workflowId: String, sequenceNumber: Long): WorkflowCheckpoint {
        val state = WorkflowState(
            workflowId = workflowId,
            status = WorkflowStatus.RUNNING,
            currentIteration = (sequenceNumber / 10).toInt(),
            maxIterations = 100,
            lastEventSequence = sequenceNumber,
            agentSteps = emptyList(),
            agentEdits = emptyList(),
            pendingSignals = emptyList()
        )
        return WorkflowCheckpoint(
            id = "checkpoint-$sequenceNumber",
            workflowId = workflowId,
            sequenceNumber = sequenceNumber,
            state = json.encodeToString(state),
            createdAt = System.currentTimeMillis(),
            sizeBytes = 0
        )
    }

    @Test
    fun `test save and get latest checkpoint`() = runBlocking {
        val manager = InMemoryCheckpointManager()
        val workflowId = "test-workflow-1"

        manager.save(createCheckpoint(workflowId, 10))
        manager.save(createCheckpoint(workflowId, 20))
        manager.save(createCheckpoint(workflowId, 30))

        val latest = manager.getLatest(workflowId)
        assertNotNull(latest)
        assertEquals(30L, latest.sequenceNumber)
    }

    @Test
    fun `test get latest before sequence`() = runBlocking {
        val manager = InMemoryCheckpointManager()
        val workflowId = "test-workflow-2"

        manager.save(createCheckpoint(workflowId, 10))
        manager.save(createCheckpoint(workflowId, 20))
        manager.save(createCheckpoint(workflowId, 30))

        val checkpoint = manager.getLatestBefore(workflowId, 25)
        assertNotNull(checkpoint)
        assertEquals(20L, checkpoint.sequenceNumber)
    }

    @Test
    fun `test get all checkpoints`() = runBlocking {
        val manager = InMemoryCheckpointManager()
        val workflowId = "test-workflow-3"

        manager.save(createCheckpoint(workflowId, 30))
        manager.save(createCheckpoint(workflowId, 10))
        manager.save(createCheckpoint(workflowId, 20))

        val all = manager.getAll(workflowId)
        assertEquals(3, all.size)
        assertEquals(10L, all[0].sequenceNumber)
        assertEquals(20L, all[1].sequenceNumber)
        assertEquals(30L, all[2].sequenceNumber)
    }

    @Test
    fun `test prune old checkpoints`() = runBlocking {
        val manager = InMemoryCheckpointManager()
        val workflowId = "test-workflow-4"

        for (i in 1..10) {
            manager.save(createCheckpoint(workflowId, i.toLong() * 10))
        }

        assertEquals(10, manager.getAll(workflowId).size)

        manager.pruneOldCheckpoints(workflowId, keepCount = 3)

        val remaining = manager.getAll(workflowId)
        assertEquals(3, remaining.size)
        assertEquals(80L, remaining[0].sequenceNumber)
        assertEquals(90L, remaining[1].sequenceNumber)
        assertEquals(100L, remaining[2].sequenceNumber)
    }

    @Test
    fun `test delete all checkpoints`() = runBlocking {
        val manager = InMemoryCheckpointManager()
        val workflowId = "test-workflow-5"

        manager.save(createCheckpoint(workflowId, 10))
        manager.save(createCheckpoint(workflowId, 20))

        assertEquals(2, manager.getAll(workflowId).size)

        manager.deleteAll(workflowId)

        assertEquals(0, manager.getAll(workflowId).size)
        assertNull(manager.getLatest(workflowId))
    }

    @Test
    fun `test restore state from checkpoint`() = runBlocking {
        val manager = InMemoryCheckpointManager()
        val workflowId = "test-workflow-6"

        val checkpoint = createCheckpoint(workflowId, 50)
        manager.save(checkpoint)

        val state = manager.restoreState(checkpoint)
        assertEquals(workflowId, state.workflowId)
        assertEquals(WorkflowStatus.RUNNING, state.status)
        assertEquals(50L, state.lastEventSequence)
    }
}

