package cc.unitmesh.server.workflow.store

import cc.unitmesh.server.workflow.models.WorkflowCheckpoint
import cc.unitmesh.server.workflow.models.WorkflowState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.DriverManager
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * SQLite 实现的 CheckpointManager
 */
class SqliteCheckpointManager(
    private val dbPath: String = SqliteEventStore.getDefaultDbPath()
) : CheckpointManager {
    
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    
    private val connection: Connection by lazy {
        Class.forName("org.sqlite.JDBC")
        val conn = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        createTable(conn)
        conn
    }
    
    private fun createTable(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS workflow_checkpoints (
                    id TEXT PRIMARY KEY,
                    workflow_id TEXT NOT NULL,
                    sequence_number INTEGER NOT NULL,
                    state TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    size_bytes INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_workflow_checkpoints_workflow_id ON workflow_checkpoints(workflow_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_workflow_checkpoints_sequence ON workflow_checkpoints(workflow_id, sequence_number DESC)")
        }
    }
    
    override suspend fun save(checkpoint: WorkflowCheckpoint) {
        val sql = """
            INSERT OR REPLACE INTO workflow_checkpoints (id, workflow_id, sequence_number, state, created_at, size_bytes)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
        
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, checkpoint.id)
            stmt.setString(2, checkpoint.workflowId)
            stmt.setLong(3, checkpoint.sequenceNumber)
            stmt.setString(4, checkpoint.state)
            stmt.setLong(5, checkpoint.createdAt)
            stmt.setInt(6, checkpoint.state.length)
            stmt.executeUpdate()
        }
        
        logger.debug { "Saved checkpoint ${checkpoint.id} for workflow ${checkpoint.workflowId} at sequence ${checkpoint.sequenceNumber}" }
    }
    
    override suspend fun getLatest(workflowId: String): WorkflowCheckpoint? {
        val sql = "SELECT * FROM workflow_checkpoints WHERE workflow_id = ? ORDER BY sequence_number DESC LIMIT 1"
        
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, workflowId)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) {
                    WorkflowCheckpoint(
                        id = rs.getString("id"),
                        workflowId = rs.getString("workflow_id"),
                        sequenceNumber = rs.getLong("sequence_number"),
                        state = rs.getString("state"),
                        createdAt = rs.getLong("created_at"),
                        sizeBytes = rs.getInt("size_bytes")
                    )
                } else null
            }
        }
    }
    
    override suspend fun getLatestBefore(workflowId: String, sequenceNumber: Long): WorkflowCheckpoint? {
        val sql = "SELECT * FROM workflow_checkpoints WHERE workflow_id = ? AND sequence_number < ? ORDER BY sequence_number DESC LIMIT 1"
        
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, workflowId)
            stmt.setLong(2, sequenceNumber)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) {
                    WorkflowCheckpoint(
                        id = rs.getString("id"),
                        workflowId = rs.getString("workflow_id"),
                        sequenceNumber = rs.getLong("sequence_number"),
                        state = rs.getString("state"),
                        createdAt = rs.getLong("created_at"),
                        sizeBytes = rs.getInt("size_bytes")
                    )
                } else null
            }
        }
    }
    
    override suspend fun getAll(workflowId: String): List<WorkflowCheckpoint> {
        val sql = "SELECT * FROM workflow_checkpoints WHERE workflow_id = ? ORDER BY sequence_number DESC"
        val checkpoints = mutableListOf<WorkflowCheckpoint>()
        
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, workflowId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    checkpoints.add(WorkflowCheckpoint(
                        id = rs.getString("id"),
                        workflowId = rs.getString("workflow_id"),
                        sequenceNumber = rs.getLong("sequence_number"),
                        state = rs.getString("state"),
                        createdAt = rs.getLong("created_at"),
                        sizeBytes = rs.getInt("size_bytes")
                    ))
                }
            }
        }
        
        return checkpoints
    }
    
    override suspend fun pruneOldCheckpoints(workflowId: String, keepCount: Int) {
        // 获取要保留的检查点 ID
        val keepSql = "SELECT id FROM workflow_checkpoints WHERE workflow_id = ? ORDER BY sequence_number DESC LIMIT ?"
        val keepIds = mutableListOf<String>()
        
        connection.prepareStatement(keepSql).use { stmt ->
            stmt.setString(1, workflowId)
            stmt.setInt(2, keepCount)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    keepIds.add(rs.getString("id"))
                }
            }
        }
        
        if (keepIds.isEmpty()) return
        
        // 删除其他检查点
        val placeholders = keepIds.joinToString(",") { "?" }
        val deleteSql = "DELETE FROM workflow_checkpoints WHERE workflow_id = ? AND id NOT IN ($placeholders)"
        
        connection.prepareStatement(deleteSql).use { stmt ->
            stmt.setString(1, workflowId)
            keepIds.forEachIndexed { index, id ->
                stmt.setString(index + 2, id)
            }
            val deleted = stmt.executeUpdate()
            if (deleted > 0) {
                logger.info { "Pruned $deleted old checkpoints for workflow $workflowId" }
            }
        }
    }
    
    override suspend fun deleteAll(workflowId: String) {
        val sql = "DELETE FROM workflow_checkpoints WHERE workflow_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, workflowId)
            stmt.executeUpdate()
        }
        logger.info { "Deleted all checkpoints for workflow $workflowId" }
    }
    
    override suspend fun restoreState(checkpoint: WorkflowCheckpoint): WorkflowState {
        return json.decodeFromString<WorkflowState>(checkpoint.state)
    }
}
