package cc.unitmesh.devins.db

import cc.unitmesh.devins.ui.compose.agent.chatdb.model.DataSourceConfig
import cc.unitmesh.devins.ui.compose.agent.chatdb.model.DatabaseDialect
import cc.unitmesh.devins.ui.platform.BrowserStorage
import cc.unitmesh.devins.ui.platform.console
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * DataSource Repository for WASM platform
 * Uses browser localStorage to store data source configurations
 */
actual class DataSourceRepository {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Serializable
    private data class StoredDataSource(
        val id: String,
        val name: String,
        val dialect: String,
        val host: String,
        val port: Int,
        val database: String,
        val username: String,
        val password: String,
        val description: String,
        val isDefault: Boolean,
        val createdAt: Long,
        val updatedAt: Long
    )

    @Serializable
    private data class DataSourceStorage(
        val dataSources: List<StoredDataSource>
    )

    actual fun getAll(): List<DataSourceConfig> {
        return try {
            val storage = loadStorage()
            storage.dataSources.map { it.toDataSourceConfig() }
        } catch (e: Exception) {
            console.error("WASM: Error loading data sources: ${e.message}")
            emptyList()
        }
    }

    actual fun getById(id: String): DataSourceConfig? {
        return try {
            val storage = loadStorage()
            storage.dataSources.firstOrNull { it.id == id }?.toDataSourceConfig()
        } catch (e: Exception) {
            console.error("WASM: Error getting data source by id: ${e.message}")
            null
        }
    }

    actual fun getDefault(): DataSourceConfig? {
        return try {
            val storage = loadStorage()
            storage.dataSources.firstOrNull { it.isDefault }?.toDataSourceConfig()
        } catch (e: Exception) {
            console.error("WASM: Error getting default data source: ${e.message}")
            null
        }
    }

    actual fun save(config: DataSourceConfig) {
        try {
            val storage = loadStorage()
            val stored = config.toStoredDataSource()
            val existing = storage.dataSources.indexOfFirst { it.id == config.id }
            val updatedList = if (existing >= 0) {
                storage.dataSources.toMutableList().apply { set(existing, stored) }
            } else {
                storage.dataSources + stored
            }
            saveStorage(DataSourceStorage(updatedList))
            console.log("WASM: Data source saved: ${config.id}")
        } catch (e: Exception) {
            console.error("WASM: Error saving data source: ${e.message}")
        }
    }

    actual fun delete(id: String) {
        try {
            val storage = loadStorage()
            val updatedList = storage.dataSources.filter { it.id != id }
            saveStorage(DataSourceStorage(updatedList))
            console.log("WASM: Data source deleted: $id")
        } catch (e: Exception) {
            console.error("WASM: Error deleting data source: ${e.message}")
        }
    }

    actual fun deleteAll() {
        try {
            saveStorage(DataSourceStorage(emptyList()))
            console.log("WASM: All data sources deleted")
        } catch (e: Exception) {
            console.error("WASM: Error deleting all data sources: ${e.message}")
        }
    }

    actual fun setDefault(id: String) {
        try {
            val storage = loadStorage()
            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            val updatedList = storage.dataSources.map {
                it.copy(
                    isDefault = it.id == id,
                    updatedAt = if (it.id == id) now else it.updatedAt
                )
            }
            saveStorage(DataSourceStorage(updatedList))
            console.log("WASM: Default data source set to: $id")
        } catch (e: Exception) {
            console.error("WASM: Error setting default data source: ${e.message}")
        }
    }

    private fun loadStorage(): DataSourceStorage {
        val content = BrowserStorage.getItem(STORAGE_KEY)
        return if (content != null) {
            try {
                json.decodeFromString<DataSourceStorage>(content)
            } catch (e: Exception) {
                console.warn("WASM: Failed to parse data source storage: ${e.message}")
                DataSourceStorage(emptyList())
            }
        } else {
            DataSourceStorage(emptyList())
        }
    }

    private fun saveStorage(storage: DataSourceStorage) {
        val content = json.encodeToString(storage)
        BrowserStorage.setItem(STORAGE_KEY, content)
    }

    private fun StoredDataSource.toDataSourceConfig(): DataSourceConfig {
        return DataSourceConfig(
            id = this.id,
            name = this.name,
            dialect = DatabaseDialect.fromString(this.dialect),
            host = this.host,
            port = this.port,
            database = this.database,
            username = this.username,
            password = this.password,
            description = this.description,
            isDefault = this.isDefault,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    private fun DataSourceConfig.toStoredDataSource(): StoredDataSource {
        return StoredDataSource(
            id = this.id,
            name = this.name,
            dialect = this.dialect.name,
            host = this.host,
            port = this.port,
            database = this.database,
            username = this.username,
            password = this.password,
            description = this.description,
            isDefault = this.isDefault,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    actual companion object {
        private const val STORAGE_KEY = "autodev-datasources"
        private var instance: DataSourceRepository? = null

        actual fun getInstance(): DataSourceRepository {
            return instance ?: DataSourceRepository().also { instance = it }
        }
    }
}

