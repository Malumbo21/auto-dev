package cc.unitmesh.agent.database

import kotlinx.serialization.Serializable

/**
 * Database connection interface - unified interface for all database implementations
 * 
 * This is a cross-platform interface that all platforms (JVM, IDEA, WASM, etc.) need to implement.
 */
interface DatabaseConnection {
    /**
     * Execute a SQL query
     * 
     * @param sql SQL query statement (SELECT only)
     * @return Query result
     * @throws DatabaseException If execution fails
     */
    suspend fun executeQuery(sql: String): QueryResult

    /**
     * Get database schema information
     * 
     * @return DatabaseSchema containing all tables and columns
     * @throws DatabaseException If retrieval fails
     */
    suspend fun getSchema(): DatabaseSchema

    /**
     * Execute SQL query and return a single scalar value
     * 
     * @param sql SQL query statement
     * @return First column of first row, or null if no results
     */
    suspend fun queryScalar(sql: String): Any? {
        val result = executeQuery(sql)
        return if (result.isEmpty()) null else result.rows[0][0]
    }

    /**
     * Check if a table exists
     */
    suspend fun tableExists(tableName: String): Boolean {
        val schema = getSchema()
        return schema.getTable(tableName) != null
    }

    /**
     * Get row count of a table
     */
    suspend fun getTableRowCount(tableName: String): Long {
        val result = executeQuery("SELECT COUNT(*) as cnt FROM $tableName")
        return (result.rows[0][0] as? Number)?.toLong() ?: 0L
    }

    /**
     * Get sample rows from a table (for Schema Linking context)
     *
     * @param tableName Table name
     * @param limit Maximum number of rows to return (default 3)
     * @return Sample rows as QueryResult
     */
    suspend fun getSampleRows(tableName: String, limit: Int = 3): QueryResult {
        return try {
            executeQuery("SELECT * FROM `$tableName` LIMIT $limit")
        } catch (e: Exception) {
            QueryResult(emptyList(), emptyList(), 0)
        }
    }

    /**
     * Get distinct values for a column (for Value Matching in Schema Linking)
     *
     * @param tableName Table name
     * @param columnName Column name
     * @param limit Maximum number of distinct values to return (default 10)
     * @return List of distinct values as strings
     */
    suspend fun getDistinctValues(tableName: String, columnName: String, limit: Int = 10): List<String> {
        return try {
            val result = executeQuery("SELECT DISTINCT `$columnName` FROM `$tableName` LIMIT $limit")
            result.rows.map { it.firstOrNull() ?: "" }.filter { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Close database connection
     */
    suspend fun close()

    /**
     * Check if connection is valid
     */
    suspend fun isConnected(): Boolean {
        return try {
            executeQuery("SELECT 1")
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Database query result
 */
@Serializable
data class QueryResult(
    val columns: List<String>,
    val rows: List<List<String>>, // Simplified to String for serialization
    val rowCount: Int
) {
    /**
     * Check if result is empty
     */
    fun isEmpty(): Boolean = rows.isEmpty()

    /**
     * Convert result to CSV format (for LLM consumption)
     */
    fun toCsvString(): String {
        if (isEmpty()) return "No results"
        
        val sb = StringBuilder()
        sb.append(columns.joinToString(",")).append("\n")
        rows.forEach { row ->
            sb.append(row.joinToString(",") { it.ifEmpty { "NULL" } }).append("\n")
        }
        return sb.toString()
    }

    /**
     * Convert result to Markdown table format (for rendering with MarkdownTableRenderer)
     * Shows all rows without truncation
     */
    fun toTableString(): String {
        if (isEmpty()) return "No results"

        val sb = StringBuilder()

        // Header row
        sb.append("| ")
        sb.append(columns.joinToString(" | ") { escapeMarkdown(it) })
        sb.append(" |\n")

        // Separator row
        sb.append("| ")
        sb.append(columns.joinToString(" | ") { "---" })
        sb.append(" |\n")

        // Data rows
        rows.forEach { row ->
            sb.append("| ")
            sb.append(row.mapIndexed { idx, value ->
                val str = value.ifEmpty { "NULL" }
                escapeMarkdown(str)
            }.joinToString(" | "))
            sb.append(" |\n")
        }

        return sb.toString()
    }

    /**
     * Escape special Markdown characters in table cell content
     */
    private fun escapeMarkdown(text: String): String {
        return text
            .replace("|", "\\|")
            .replace("\n", " ")
            .replace("\r", "")
    }
}

/**
 * Database column schema
 */
@Serializable
data class ColumnSchema(
    val name: String,
    val type: String,
    val nullable: Boolean = true,
    val comment: String? = null,
    val isPrimaryKey: Boolean = false,
    val isForeignKey: Boolean = false,
    val defaultValue: String? = null
) {
    /**
     * Get natural language description (for Schema Linking)
     */
    fun getDescription(): String {
        val parts = mutableListOf<String>()
        parts.add("$name ($type)")
        if (isPrimaryKey) parts.add("PRIMARY KEY")
        if (!nullable) parts.add("NOT NULL")
        if (isForeignKey) parts.add("FOREIGN KEY")
        if (defaultValue != null) parts.add("DEFAULT: $defaultValue")
        if (comment != null) parts.add("Comment: $comment")
        return parts.joinToString(", ")
    }
}

/**
 * Database table schema
 */
@Serializable
data class TableSchema(
    val name: String,
    val columns: List<ColumnSchema>,
    val comment: String? = null,
    val tableType: String? = null
) {
    /**
     * Get natural language description (for Schema Linking)
     */
    fun getDescription(): String {
        val desc = StringBuilder()
        desc.append("Table: $name\n")
        if (comment != null) desc.append("Comment: $comment\n")
        
        desc.append("Columns:\n")
        columns.forEach { col ->
            desc.append("  - ${col.getDescription()}\n")
        }
        
        return desc.toString()
    }

    /**
     * Get primary key column names
     */
    fun getPrimaryKeyColumns(): List<String> {
        return columns.filter { it.isPrimaryKey }.map { it.name }
    }

    /**
     * Get foreign key column names
     */
    fun getForeignKeyColumns(): List<String> {
        return columns.filter { it.isForeignKey }.map { it.name }
    }
}

/**
 * Complete database schema
 */
@Serializable
data class DatabaseSchema(
    val databaseName: String? = null,
    val tables: List<TableSchema> = emptyList()
) {
    /**
     * Find table schema by name
     */
    fun getTable(tableName: String): TableSchema? {
        return tables.find { it.name.equals(tableName, ignoreCase = true) }
    }

    /**
     * Get all table names
     */
    fun getTableNames(): List<String> {
        return tables.map { it.name }
    }

    /**
     * Get natural language description (for Prompt)
     */
    fun getDescription(): String {
        val desc = StringBuilder()
        if (databaseName != null) desc.append("Database: $databaseName\n\n")
        
        tables.forEach { table ->
            desc.append(table.getDescription())
            desc.append("\n")
        }
        
        return desc.toString()
    }
}

/**
 * Database configuration
 */
@Serializable
data class DatabaseConfig(
    val host: String,
    val port: Int,
    val databaseName: String,
    val username: String,
    val password: String? = null,
    val dialect: String = "MySQL", // MySQL, MariaDB, PostgreSQL, etc.
    val additionalParams: Map<String, String> = emptyMap()
) {
    /**
     * Get JDBC URL (for JVM platform only)
     */
    fun getJdbcUrl(): String {
        return when (dialect.lowercase()) {
            "mysql", "mariadb" -> {
                "jdbc:mysql://$host:$port/$databaseName?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
            }
            "postgresql" -> {
                "jdbc:postgresql://$host:$port/$databaseName"
            }
            else -> {
                "jdbc:mysql://$host:$port/$databaseName"
            }
        }
    }
}

/**
 * Database exception
 */
class DatabaseException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    companion object {
        fun connectionFailed(reason: String): DatabaseException {
            return DatabaseException("Database connection failed: $reason")
        }

        fun queryFailed(sql: String, reason: String): DatabaseException {
            return DatabaseException("Query failed: $reason\nSQL: $sql")
        }

        fun invalidSQL(sql: String, reason: String): DatabaseException {
            return DatabaseException("Invalid SQL: $reason\nSQL: $sql")
        }
    }
}

/**
 * Database connection factory - creates platform-specific database connections
 */
expect fun createDatabaseConnection(config: DatabaseConfig): DatabaseConnection
