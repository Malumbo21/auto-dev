package cc.unitmesh.server.workflow

import cc.unitmesh.server.workflow.models.WorkflowCheckpoint
import cc.unitmesh.server.workflow.models.WorkflowState
import cc.unitmesh.server.workflow.store.CheckpointManager
import kotlinx.serialization.json.Json

/**
 * In-memory CheckpointManager implementation for testing
 */
class InMemoryCheckpointManager : CheckpointManager {
    private val checkpoints = mutableMapOf<String, MutableList<WorkflowCheckpoint>>()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun save(checkpoint: WorkflowCheckpoint) {
        checkpoints.getOrPut(checkpoint.workflowId) { mutableListOf() }.add(checkpoint)
    }

    override suspend fun getLatest(workflowId: String): WorkflowCheckpoint? {
        return checkpoints[workflowId]?.maxByOrNull { it.sequenceNumber }
    }

    override suspend fun getLatestBefore(workflowId: String, sequenceNumber: Long): WorkflowCheckpoint? {
        return checkpoints[workflowId]
            ?.filter { it.sequenceNumber < sequenceNumber }
            ?.maxByOrNull { it.sequenceNumber }
    }

    override suspend fun getAll(workflowId: String): List<WorkflowCheckpoint> {
        return checkpoints[workflowId]?.sortedBy { it.sequenceNumber } ?: emptyList()
    }

    override suspend fun pruneOldCheckpoints(workflowId: String, keepCount: Int) {
        val workflowCheckpoints = checkpoints[workflowId] ?: return
        if (workflowCheckpoints.size > keepCount) {
            val sorted = workflowCheckpoints.sortedByDescending { it.sequenceNumber }
            val toKeep = sorted.take(keepCount)
            checkpoints[workflowId] = toKeep.toMutableList()
        }
    }

    override suspend fun deleteAll(workflowId: String) {
        checkpoints.remove(workflowId)
    }

    override suspend fun restoreState(checkpoint: WorkflowCheckpoint): WorkflowState {
        return json.decodeFromString<WorkflowState>(checkpoint.state)
    }
}

