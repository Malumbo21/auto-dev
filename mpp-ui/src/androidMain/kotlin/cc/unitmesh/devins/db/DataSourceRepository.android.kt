package cc.unitmesh.devins.db

import cc.unitmesh.devins.ui.compose.agent.chatdb.model.DataSourceConfig
import cc.unitmesh.devins.ui.compose.agent.chatdb.model.DatabaseDialect

/**
 * DataSource Repository - Android implementation
 */
actual class DataSourceRepository(private val database: DevInsDatabase) {
    private val queries = database.dataSourceQueries

    actual fun getAll(): List<DataSourceConfig> {
        return queries.selectAll().executeAsList().map { it.toDataSourceConfig() }
    }

    actual fun getById(id: String): DataSourceConfig? {
        return queries.selectById(id).executeAsOneOrNull()?.toDataSourceConfig()
    }

    actual fun getDefault(): DataSourceConfig? {
        return queries.selectDefault().executeAsOneOrNull()?.toDataSourceConfig()
    }

    actual fun save(config: DataSourceConfig) {
        queries.insertOrReplace(
            id = config.id,
            name = config.name,
            dialect = config.dialect.name,
            host = config.host,
            port = config.port.toLong(),
            databaseName = config.database,
            username = config.username,
            password = config.password,
            description = config.description,
            isDefault = if (config.isDefault) 1L else 0L,
            createdAt = config.createdAt,
            updatedAt = config.updatedAt
        )
    }

    actual fun delete(id: String) {
        queries.deleteById(id)
    }

    actual fun deleteAll() {
        queries.deleteAll()
    }

    actual fun setDefault(id: String) {
        queries.clearDefault()
        queries.setDefault(id)
    }

    private fun DataSource.toDataSourceConfig(): DataSourceConfig {
        return DataSourceConfig(
            id = this.id,
            name = this.name,
            dialect = DatabaseDialect.fromString(this.dialect),
            host = this.host,
            port = this.port.toInt(),
            database = this.databaseName,
            username = this.username,
            password = this.password,
            description = this.description,
            isDefault = this.isDefault == 1L,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    actual companion object {
        private var instance: DataSourceRepository? = null

        actual fun getInstance(): DataSourceRepository {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val driverFactory = DatabaseDriverFactory()
                    val database = createDatabase(driverFactory)
                    DataSourceRepository(database).also { instance = it }
                }
            }
        }
    }
}

