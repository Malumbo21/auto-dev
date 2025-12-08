package cc.unitmesh.server.workflow.store

import cc.unitmesh.server.workflow.models.WorkflowEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.Connection
import java.sql.DriverManager
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * SQLite 实现的 EventStore
 */
class SqliteEventStore(
    private val dbPath: String = getDefaultDbPath()
) : EventStore {
    
    private val connection: Connection by lazy {
        initializeDatabase()
    }
    
    companion object {
        fun getDefaultDbPath(): String {
            val homeDir = System.getProperty("user.home")
            val autodevDir = File(homeDir, ".autodev")
            if (!autodevDir.exists()) {
                autodevDir.mkdirs()
            }
            return File(autodevDir, "workflows.db").absolutePath
        }
    }
    
    private fun initializeDatabase(): Connection {
        Class.forName("org.sqlite.JDBC")
        val conn = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        
        // 读取并执行 schema
        val schemaStream = javaClass.getResourceAsStream("/db/workflow_schema.sql")
        if (schemaStream != null) {
            val schema = schemaStream.bufferedReader().readText()
            conn.createStatement().use { stmt ->
                schema.split(";").filter { it.isNotBlank() }.forEach { sql ->
                    stmt.execute(sql.trim())
                }
            }
            logger.info { "Database initialized at $dbPath" }
        } else {
            logger.warn { "Schema file not found, creating tables manually" }
            createTablesManually(conn)
        }
        
        return conn
    }
    
    private fun createTablesManually(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS workflow_events (
                    id TEXT PRIMARY KEY,
                    workflow_id TEXT NOT NULL,
                    sequence_number INTEGER NOT NULL,
                    event_type TEXT NOT NULL,
                    event_data TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    checkpoint_id TEXT,
                    UNIQUE(workflow_id, sequence_number)
                )
            """.trimIndent())
            
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_workflow_events_workflow_id ON workflow_events(workflow_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_workflow_events_sequence ON workflow_events(workflow_id, sequence_number)")
        }
    }
    
    override suspend fun appendEvent(event: WorkflowEvent): Long {
        val sql = """
            INSERT INTO workflow_events (id, workflow_id, sequence_number, event_type, event_data, timestamp, checkpoint_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, event.id)
            stmt.setString(2, event.workflowId)
            stmt.setLong(3, event.sequenceNumber)
            stmt.setString(4, event.eventType)
            stmt.setString(5, event.eventData)
            stmt.setLong(6, event.timestamp)
            stmt.setString(7, event.checkpointId)
            stmt.executeUpdate()
        }
        
        logger.debug { "Appended event ${event.id} to workflow ${event.workflowId} with sequence ${event.sequenceNumber}" }
        return event.sequenceNumber
    }
    
    override suspend fun appendEvents(events: List<WorkflowEvent>): List<Long> {
        return events.map { appendEvent(it) }
    }
    
    override suspend fun getEvents(
        workflowId: String,
        fromSequence: Long,
        toSequence: Long?
    ): List<WorkflowEvent> {
        val sql = if (toSequence != null) {
            "SELECT * FROM workflow_events WHERE workflow_id = ? AND sequence_number >= ? AND sequence_number <= ? ORDER BY sequence_number"
        } else {
            "SELECT * FROM workflow_events WHERE workflow_id = ? AND sequence_number >= ? ORDER BY sequence_number"
        }
        
        val events = mutableListOf<WorkflowEvent>()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, workflowId)
            stmt.setLong(2, fromSequence)
            if (toSequence != null) {
                stmt.setLong(3, toSequence)
            }
            
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    events.add(WorkflowEvent(
                        id = rs.getString("id"),
                        workflowId = rs.getString("workflow_id"),
                        sequenceNumber = rs.getLong("sequence_number"),
                        eventType = rs.getString("event_type"),
                        eventData = rs.getString("event_data"),
                        timestamp = rs.getLong("timestamp"),
                        checkpointId = rs.getString("checkpoint_id")
                    ))
                }
            }
        }
        
        return events
    }
    
    override suspend fun getLatestSequence(workflowId: String): Long {
        val sql = "SELECT MAX(sequence_number) FROM workflow_events WHERE workflow_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, workflowId)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getLong(1) else 0L
            }
        }
    }
    
    override suspend fun getEventsByType(workflowId: String, eventType: String): List<WorkflowEvent> {
        val sql = "SELECT * FROM workflow_events WHERE workflow_id = ? AND event_type = ? ORDER BY sequence_number"
        val events = mutableListOf<WorkflowEvent>()
        
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, workflowId)
            stmt.setString(2, eventType)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    events.add(WorkflowEvent(
                        id = rs.getString("id"),
                        workflowId = rs.getString("workflow_id"),
                        sequenceNumber = rs.getLong("sequence_number"),
                        eventType = rs.getString("event_type"),
                        eventData = rs.getString("event_data"),
                        timestamp = rs.getLong("timestamp"),
                        checkpointId = rs.getString("checkpoint_id")
                    ))
                }
            }
        }
        
        return events
    }
    
    override suspend fun deleteEvents(workflowId: String) {
        val sql = "DELETE FROM workflow_events WHERE workflow_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, workflowId)
            stmt.executeUpdate()
        }
        logger.info { "Deleted all events for workflow $workflowId" }
    }
    
    override suspend fun getEventCount(workflowId: String): Long {
        val sql = "SELECT COUNT(*) FROM workflow_events WHERE workflow_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, workflowId)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getLong(1) else 0L
            }
        }
    }
}
