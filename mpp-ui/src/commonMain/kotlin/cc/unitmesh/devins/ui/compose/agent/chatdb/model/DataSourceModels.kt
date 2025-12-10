package cc.unitmesh.devins.ui.compose.agent.chatdb.model

import kotlinx.serialization.Serializable

/**
 * Supported database dialects
 */
enum class DatabaseDialect(val displayName: String, val defaultPort: Int) {
    MYSQL("MySQL", 3306),
    MARIADB("MariaDB", 3306),
    POSTGRESQL("PostgreSQL", 5432),
    SQLITE("SQLite", 0),
    ORACLE("Oracle", 1521),
    SQLSERVER("SQL Server", 1433);

    companion object {
        fun fromString(value: String): DatabaseDialect {
            return entries.find {
                it.name.equals(value, ignoreCase = true) ||
                it.displayName.equals(value, ignoreCase = true)
            } ?: MYSQL
        }
    }
}

/**
 * Data source configuration for database connections
 */
@Serializable
data class DataSourceConfig(
    val id: String,
    val name: String,
    val dialect: DatabaseDialect = DatabaseDialect.MYSQL,
    val host: String = "localhost",
    val port: Int = 3306,
    val database: String = "",
    val username: String = "",
    val password: String = "",
    val description: String = "",
    val isDefault: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    /**
     * Get connection URL for display (without password)
     */
    fun getDisplayUrl(): String {
        return when (dialect) {
            DatabaseDialect.SQLITE -> "sqlite://$database"
            else -> "${dialect.name.lowercase()}://$host:$port/$database"
        }
    }

    /**
     * Validate the configuration
     */
    fun validate(): ValidationResult {
        val errors = mutableListOf<String>()

        if (name.isBlank()) errors.add("Name is required")
        if (dialect != DatabaseDialect.SQLITE) {
            if (host.isBlank()) errors.add("Host is required")
            if (port <= 0 || port > 65535) errors.add("Port must be between 1 and 65535")
        }
        if (database.isBlank()) errors.add("Database name is required")

        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }

    /**
     * Convert to cc.unitmesh.agent.database.DatabaseConfig
     */
    fun toDatabaseConfig(): cc.unitmesh.agent.database.DatabaseConfig {
        return cc.unitmesh.agent.database.DatabaseConfig(
            host = host,
            port = port,
            databaseName = database,
            username = username,
            password = password,
            dialect = dialect.name
        )
    }
}

/**
 * Validation result
 */
sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val errors: List<String>) : ValidationResult()
}

/**
 * Connection status for a data source
 */
sealed class ConnectionStatus {
    data object Disconnected : ConnectionStatus()
    data object Connecting : ConnectionStatus()
    data object Connected : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

/**
 * Chat message in ChatDB context
 */
@Serializable
data class ChatDBMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val sql: String? = null,
    val queryResult: QueryResultDisplay? = null
)

/**
 * Message role
 */
@Serializable
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * Query result for display
 */
@Serializable
data class QueryResultDisplay(
    val columns: List<String>,
    val rows: List<List<String>>,
    val rowCount: Int,
    val executionTimeMs: Long = 0
)

/**
 * UI state for ChatDB page
 */
data class ChatDBState(
    val dataSources: List<DataSourceConfig> = emptyList(),
    val selectedDataSourceId: String? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val filterQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isConfigDialogOpen: Boolean = false,
    val editingDataSource: DataSourceConfig? = null,
    /** Whether the config pane is shown (inline panel mode) */
    val isConfigPaneOpen: Boolean = false,
    /** The data source being configured in the pane */
    val configuringDataSource: DataSourceConfig? = null
) {
    val selectedDataSource: DataSourceConfig?
        get() = dataSources.find { it.id == selectedDataSourceId }

    val filteredDataSources: List<DataSourceConfig>
        get() = if (filterQuery.isBlank()) {
            dataSources
        } else {
            dataSources.filter { ds ->
                ds.name.contains(filterQuery, ignoreCase = true) ||
                ds.database.contains(filterQuery, ignoreCase = true) ||
                ds.host.contains(filterQuery, ignoreCase = true)
            }
        }
}

