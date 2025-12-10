package cc.unitmesh.devins.ui.compose.agent.chatdb

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.unitmesh.agent.chatdb.ChatDBAgent
import cc.unitmesh.agent.chatdb.ChatDBTask
import cc.unitmesh.agent.chatdb.MultiDatabaseChatDBAgent
import cc.unitmesh.agent.config.McpToolConfigService
import cc.unitmesh.agent.config.ToolConfigFile
import cc.unitmesh.agent.database.DatabaseConfig
import cc.unitmesh.agent.database.DatabaseConnection
import cc.unitmesh.agent.database.DatabaseSchema
import cc.unitmesh.agent.database.createDatabaseConnection
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devins.db.DataSourceRepository
import cc.unitmesh.devins.ui.compose.agent.ComposeRenderer
import cc.unitmesh.devins.ui.compose.agent.chatdb.model.*
import cc.unitmesh.devins.workspace.Workspace
import cc.unitmesh.llm.KoogLLMService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ViewModel for ChatDB Page
 *
 * Manages data sources, database connections, and chat interactions for text-to-SQL.
 */
class ChatDBViewModel(
    private val workspace: Workspace? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val renderer = ComposeRenderer()

    // LLM Service
    private var llmService: KoogLLMService? = null
    private var currentExecutionJob: Job? = null

    // Database connections (multi-datasource support)
    private val connections: MutableMap<String, DatabaseConnection> = mutableMapOf()
    private val schemas: MutableMap<String, DatabaseSchema> = mutableMapOf()

    // Data source repository for persistence
    private val dataSourceRepository: DataSourceRepository by lazy {
        DataSourceRepository.getInstance()
    }

    // UI State
    var state by mutableStateOf(ChatDBState())
        private set

    var isGenerating by mutableStateOf(false)
        private set

    // Notifications
    private val _notificationEvent = MutableSharedFlow<Pair<String, String>>()
    val notificationEvent = _notificationEvent.asSharedFlow()

    init {
        initializeLLMService()
        loadDataSources()
    }

    private fun initializeLLMService() {
        scope.launch {
            try {
                val configWrapper = ConfigManager.load()
                val modelConfig = configWrapper.getActiveModelConfig()
                if (modelConfig != null && modelConfig.isValid()) {
                    llmService = KoogLLMService.create(modelConfig)
                }
            } catch (e: Exception) {
                println("[ChatDB] Failed to initialize LLM service: ${e.message}")
            }
        }
    }

    private fun loadDataSources() {
        scope.launch {
            try {
                val dataSources = dataSourceRepository.getAll()
                // Multi-datasource: select all data sources by default
                val allIds = dataSources.map { it.id }.toSet()
                state = state.copy(
                    dataSources = dataSources,
                    selectedDataSourceIds = allIds
                )
                println("[ChatDB] Loaded ${dataSources.size} data sources, all selected by default")
            } catch (e: Exception) {
                println("[ChatDB] Failed to load data sources: ${e.message}")
                state = state.copy(dataSources = emptyList())
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun addDataSource(config: DataSourceConfig) {
        val newConfig = config.copy(
            id = if (config.id.isBlank()) Uuid.random().toString() else config.id,
            createdAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            updatedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
        state = state.copy(
            dataSources = state.dataSources + newConfig,
            isConfigDialogOpen = false,
            editingDataSource = null
        )
        saveDataSource(newConfig)
    }

    fun updateDataSource(config: DataSourceConfig) {
        val updated = config.copy(
            updatedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
        state = state.copy(
            dataSources = state.dataSources.map { if (it.id == config.id) updated else it },
            isConfigDialogOpen = false,
            editingDataSource = null
        )
        saveDataSource(updated)
    }

    fun deleteDataSource(id: String) {
        // Disconnect this specific data source if connected
        disconnectDataSource(id)

        state = state.copy(
            dataSources = state.dataSources.filter { it.id != id },
            selectedDataSourceIds = state.selectedDataSourceIds - id,
            connectionStatuses = state.connectionStatuses - id
        )
        deleteDataSourceFromRepository(id)
    }

    private fun saveDataSources() {
        scope.launch {
            try {
                // Save all data sources to repository
                state.dataSources.forEach { config ->
                    dataSourceRepository.save(config)
                }
                println("[ChatDB] Saved ${state.dataSources.size} data sources")
            } catch (e: Exception) {
                println("[ChatDB] Failed to save data sources: ${e.message}")
            }
        }
    }

    private fun saveDataSource(config: DataSourceConfig) {
        scope.launch {
            try {
                dataSourceRepository.save(config)
                println("[ChatDB] Saved data source: ${config.id}")
            } catch (e: Exception) {
                println("[ChatDB] Failed to save data source: ${e.message}")
            }
        }
    }

    private fun deleteDataSourceFromRepository(id: String) {
        scope.launch {
            try {
                dataSourceRepository.delete(id)
                println("[ChatDB] Deleted data source: $id")
            } catch (e: Exception) {
                println("[ChatDB] Failed to delete data source: ${e.message}")
            }
        }
    }

    /**
     * Toggle selection of a data source (multi-selection mode)
     */
    fun toggleDataSource(id: String) {
        val newSelectedIds = if (id in state.selectedDataSourceIds) {
            // Deselect: disconnect if connected
            disconnectDataSource(id)
            state.selectedDataSourceIds - id
        } else {
            // Select: add to selection
            state.selectedDataSourceIds + id
        }
        state = state.copy(selectedDataSourceIds = newSelectedIds)
    }

    /**
     * Select a data source (for backward compatibility, also used for single-click selection)
     */
    fun selectDataSource(id: String) {
        if (id !in state.selectedDataSourceIds) {
            state = state.copy(selectedDataSourceIds = state.selectedDataSourceIds + id)
        }
    }

    /**
     * Select all data sources
     */
    fun selectAllDataSources() {
        val allIds = state.dataSources.map { it.id }.toSet()
        state = state.copy(selectedDataSourceIds = allIds)
    }

    /**
     * Deselect all data sources
     */
    fun deselectAllDataSources() {
        disconnectAll()
        state = state.copy(selectedDataSourceIds = emptySet())
    }

    fun setFilterQuery(query: String) {
        state = state.copy(filterQuery = query)
    }

    fun openAddDialog() {
        state = state.copy(isConfigDialogOpen = true, editingDataSource = null)
    }

    fun openEditDialog(config: DataSourceConfig) {
        state = state.copy(isConfigDialogOpen = true, editingDataSource = config)
    }

    fun closeConfigDialog() {
        state = state.copy(isConfigDialogOpen = false, editingDataSource = null)
    }

    // --- Config Pane methods (inline panel mode) ---

    fun openConfigPane(config: DataSourceConfig? = null) {
        state = state.copy(isConfigPaneOpen = true, configuringDataSource = config)
    }

    fun closeConfigPane() {
        state = state.copy(isConfigPaneOpen = false, configuringDataSource = null)
    }

    @OptIn(ExperimentalUuidApi::class)
    fun saveFromPane(config: DataSourceConfig) {
        val isNew = config.id.isBlank()
        val savedConfig = config.copy(
            id = if (isNew) Uuid.random().toString() else config.id,
            createdAt = if (isNew) kotlinx.datetime.Clock.System.now().toEpochMilliseconds() else state.configuringDataSource?.createdAt ?: 0L,
            updatedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )

        state = if (isNew) {
            state.copy(
                dataSources = state.dataSources + savedConfig,
                isConfigPaneOpen = false,
                configuringDataSource = null,
                selectedDataSourceIds = state.selectedDataSourceIds + savedConfig.id
            )
        } else {
            state.copy(
                dataSources = state.dataSources.map { if (it.id == savedConfig.id) savedConfig else it },
                isConfigPaneOpen = false,
                configuringDataSource = null
            )
        }
        saveDataSource(savedConfig)
    }

    @OptIn(ExperimentalUuidApi::class)
    fun saveAndConnectFromPane(config: DataSourceConfig) {
        val isNew = config.id.isBlank()
        val savedConfig = config.copy(
            id = if (isNew) Uuid.random().toString() else config.id,
            createdAt = if (isNew) kotlinx.datetime.Clock.System.now().toEpochMilliseconds() else state.configuringDataSource?.createdAt ?: 0L,
            updatedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )

        state = if (isNew) {
            state.copy(
                dataSources = state.dataSources + savedConfig,
                isConfigPaneOpen = false,
                configuringDataSource = null,
                selectedDataSourceIds = state.selectedDataSourceIds + savedConfig.id
            )
        } else {
            state.copy(
                dataSources = state.dataSources.map { if (it.id == savedConfig.id) savedConfig else it },
                isConfigPaneOpen = false,
                configuringDataSource = null,
                selectedDataSourceIds = state.selectedDataSourceIds + savedConfig.id
            )
        }
        saveDataSource(savedConfig)

        // Trigger connection for this specific data source
        connectDataSource(savedConfig.id)
    }

    /**
     * Connect all selected data sources
     */
    fun connectAll() {
        state.selectedDataSources.forEach { dataSource ->
            connectDataSource(dataSource.id)
        }
    }

    /**
     * Connect a specific data source
     */
    fun connectDataSource(id: String) {
        val dataSource = state.dataSources.find { it.id == id } ?: return

        scope.launch {
            // Update status to connecting
            state = state.copy(
                connectionStatuses = state.connectionStatuses + (id to ConnectionStatus.Connecting),
                connectionStatus = ConnectionStatus.Connecting
            )

            try {
                val connection = createDatabaseConnection(dataSource.toDatabaseConfig())
                if (connection.isConnected()) {
                    connections[id] = connection
                    schemas[id] = connection.getSchema()
                    state = state.copy(
                        connectionStatuses = state.connectionStatuses + (id to ConnectionStatus.Connected),
                        connectionStatus = if (state.hasAnyConnection || true) ConnectionStatus.Connected else state.connectionStatus
                    )
                    _notificationEvent.emit("Connected" to "Successfully connected to ${dataSource.name}")
                } else {
                    state = state.copy(
                        connectionStatuses = state.connectionStatuses + (id to ConnectionStatus.Error("Failed to connect"))
                    )
                }
            } catch (e: Exception) {
                state = state.copy(
                    connectionStatuses = state.connectionStatuses + (id to ConnectionStatus.Error(e.message ?: "Unknown error"))
                )
                _notificationEvent.emit("Connection Failed" to "${dataSource.name}: ${e.message ?: "Unknown error"}")
            }
        }
    }

    /**
     * Disconnect a specific data source
     */
    fun disconnectDataSource(id: String) {
        scope.launch {
            try {
                connections[id]?.close()
            } catch (e: Exception) {
                println("[ChatDB] Error closing connection for $id: ${e.message}")
            }
            connections.remove(id)
            schemas.remove(id)
            state = state.copy(
                connectionStatuses = state.connectionStatuses + (id to ConnectionStatus.Disconnected),
                connectionStatus = if (connections.isEmpty()) ConnectionStatus.Disconnected else ConnectionStatus.Connected
            )
        }
    }

    /**
     * Disconnect all data sources
     */
    fun disconnectAll() {
        scope.launch {
            connections.forEach { (id, connection) ->
                try {
                    connection.close()
                } catch (e: Exception) {
                    println("[ChatDB] Error closing connection for $id: ${e.message}")
                }
            }
            connections.clear()
            schemas.clear()
            state = state.copy(
                connectionStatuses = emptyMap(),
                connectionStatus = ConnectionStatus.Disconnected
            )
        }
    }

    /**
     * Legacy connect method - connects all selected data sources
     */
    fun connect() {
        connectAll()
    }

    /**
     * Legacy disconnect method - disconnects all data sources
     */
    fun disconnect() {
        disconnectAll()
    }

    fun sendMessage(text: String) {
        if (isGenerating || text.isBlank()) return

        currentExecutionJob = scope.launch {
            isGenerating = true
            renderer.addUserMessage(text)

            try {
                val service = llmService
                if (service == null) {
                    renderer.renderError("LLM service not initialized. Please configure your model settings.")
                    isGenerating = false
                    return@launch
                }

                // Get connected data sources
                val connectedDataSources = state.selectedDataSources.filter { ds ->
                    state.getConnectionStatus(ds.id) is ConnectionStatus.Connected
                }

                if (connectedDataSources.isEmpty()) {
                    renderer.renderError("No database connected. Please connect to at least one data source first.")
                    isGenerating = false
                    return@launch
                }

                val projectPath = workspace?.rootPath ?: "."
                val mcpConfigService = McpToolConfigService(ToolConfigFile())

                // Build database configs map for multi-database agent
                val databaseConfigs: Map<String, DatabaseConfig> = connectedDataSources.associate { ds ->
                    ds.id to ds.toDatabaseConfig()
                }

                // Use MultiDatabaseChatDBAgent for unified schema linking and query execution
                val agent = MultiDatabaseChatDBAgent(
                    projectPath = projectPath,
                    llmService = service,
                    databaseConfigs = databaseConfigs,
                    maxIterations = 10,
                    renderer = renderer,
                    mcpToolConfigService = mcpConfigService,
                    enableLLMStreaming = true
                )

                val task = ChatDBTask(
                    query = text,
                    maxRows = 100,
                    generateVisualization = false
                )

                try {
                    // Execute the multi-database agent
                    // It will merge schemas, let LLM decide which database(s) to query,
                    // and execute SQL on the appropriate database(s)
                    agent.execute(task) { progress ->
                        println("[ChatDB] Progress: $progress")
                    }
                } finally {
                    agent.close()
                }

            } catch (e: CancellationException) {
                renderer.forceStop()
                renderer.renderError("Generation cancelled")
            } catch (e: Exception) {
                renderer.renderError("Error: ${e.message}")
            } finally {
                isGenerating = false
                currentExecutionJob = null
            }
        }
    }

    fun stopGeneration() {
        currentExecutionJob?.cancel()
        isGenerating = false
    }

    /**
     * Create a new session - clears the current chat timeline
     */
    fun newSession() {
        if (isGenerating) {
            stopGeneration()
        }
        renderer.clearMessages()
    }

    /**
     * Get combined schema from all connected data sources
     */
    fun getSchema(): DatabaseSchema? {
        if (schemas.isEmpty()) return null

        // Return the first schema for now, or combine them
        // For multi-datasource, we might want to show all schemas
        return schemas.values.firstOrNull()
    }

    /**
     * Get all schemas from connected data sources
     */
    fun getAllSchemas(): Map<String, DatabaseSchema> = schemas.toMap()

    /**
     * Get schema for a specific data source
     */
    fun getSchemaForDataSource(id: String): DatabaseSchema? = schemas[id]

    fun dispose() {
        stopGeneration()
        disconnectAll()
        scope.cancel()
    }
}

