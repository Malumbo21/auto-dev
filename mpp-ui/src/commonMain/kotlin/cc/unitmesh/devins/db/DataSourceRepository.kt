package cc.unitmesh.devins.db

import cc.unitmesh.devins.ui.compose.agent.chatdb.model.DataSourceConfig
import cc.unitmesh.devins.ui.compose.agent.chatdb.model.DatabaseDialect

/**
 * DataSource Repository - Data access layer for database connection configurations
 * Uses expect/actual pattern for cross-platform support
 */
expect class DataSourceRepository {
    /**
     * Get all data source configurations
     */
    fun getAll(): List<DataSourceConfig>

    /**
     * Get a data source by ID
     */
    fun getById(id: String): DataSourceConfig?

    /**
     * Get the default data source
     */
    fun getDefault(): DataSourceConfig?

    /**
     * Save a data source configuration (insert or update)
     */
    fun save(config: DataSourceConfig)

    /**
     * Delete a data source by ID
     */
    fun delete(id: String)

    /**
     * Delete all data sources
     */
    fun deleteAll()

    /**
     * Set a data source as default
     */
    fun setDefault(id: String)

    companion object {
        /**
         * Get singleton instance
         */
        fun getInstance(): DataSourceRepository
    }
}

