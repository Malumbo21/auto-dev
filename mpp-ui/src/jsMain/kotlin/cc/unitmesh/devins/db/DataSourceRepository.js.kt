package cc.unitmesh.devins.db

import cc.unitmesh.devins.ui.compose.agent.chatdb.model.DataSourceConfig

/**
 * DataSource Repository - JS implementation
 * Currently provides stub implementation, can be extended with localStorage or IndexedDB
 */
actual class DataSourceRepository {
    actual fun getAll(): List<DataSourceConfig> {
        console.warn("DataSourceRepository not implemented for JS platform")
        return emptyList()
    }

    actual fun getById(id: String): DataSourceConfig? {
        return null
    }

    actual fun getDefault(): DataSourceConfig? {
        return null
    }

    actual fun save(config: DataSourceConfig) {
        // No-op
    }

    actual fun delete(id: String) {
        // No-op
    }

    actual fun deleteAll() {
        // No-op
    }

    actual fun setDefault(id: String) {
        // No-op
    }

    actual companion object {
        private var instance: DataSourceRepository? = null

        actual fun getInstance(): DataSourceRepository {
            return instance ?: DataSourceRepository().also { instance = it }
        }
    }
}

