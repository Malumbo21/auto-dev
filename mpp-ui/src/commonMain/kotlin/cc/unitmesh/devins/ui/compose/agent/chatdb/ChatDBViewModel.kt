package cc.unitmesh.devins.ui.compose.agent.chatdb

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.unitmesh.agent.chatdb.ChatDBAgent
import cc.unitmesh.agent.chatdb.ChatDBTask
import cc.unitmesh.agent.config.McpToolConfigService
import cc.unitmesh.agent.config.ToolConfigFile
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

    // Database connection
    private var currentConnection: DatabaseConnection? = null
    private var currentSchema: DatabaseSchema? = null

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
                val defaultDataSource = dataSourceRepository.getDefault()
                state = state.copy(
                    dataSources = dataSources,
                    selectedDataSourceId = defaultDataSource?.id
                )
                println("[ChatDB] Loaded ${dataSources.size} data sources")
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
        state = state.copy(
            dataSources = state.dataSources.filter { it.id != id },
            selectedDataSourceId = if (state.selectedDataSourceId == id) null else state.selectedDataSourceId
        )
        if (state.selectedDataSourceId == id) {
            disconnect()
        }
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

    fun selectDataSource(id: String) {
        if (state.selectedDataSourceId == id) return

        disconnect()
        state = state.copy(selectedDataSourceId = id)
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
                selectedDataSourceId = savedConfig.id
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
                selectedDataSourceId = savedConfig.id
            )
        } else {
            state.copy(
                dataSources = state.dataSources.map { if (it.id == savedConfig.id) savedConfig else it },
                isConfigPaneOpen = false,
                configuringDataSource = null,
                selectedDataSourceId = savedConfig.id
            )
        }
        saveDataSource(savedConfig)

        // Trigger connection
        connect()
    }

    fun connect() {
        val dataSource = state.selectedDataSource ?: return

        scope.launch {
            state = state.copy(connectionStatus = ConnectionStatus.Connecting)

            try {
                val connection = createDatabaseConnection(dataSource.toDatabaseConfig())
                if (connection.isConnected()) {
                    currentConnection = connection
                    currentSchema = connection.getSchema()
                    state = state.copy(connectionStatus = ConnectionStatus.Connected)
                    _notificationEvent.emit("Connected" to "Successfully connected to ${dataSource.name}")
                } else {
                    state = state.copy(connectionStatus = ConnectionStatus.Error("Failed to connect"))
                }
            } catch (e: Exception) {
                state = state.copy(connectionStatus = ConnectionStatus.Error(e.message ?: "Unknown error"))
                _notificationEvent.emit("Connection Failed" to (e.message ?: "Unknown error"))
            }
        }
    }

    fun disconnect() {
        scope.launch {
            try {
                currentConnection?.close()
            } catch (e: Exception) {
                println("[ChatDB] Error closing connection: ${e.message}")
            }
            currentConnection = null
            currentSchema = null
            state = state.copy(connectionStatus = ConnectionStatus.Disconnected)
        }
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

                val dataSource = state.selectedDataSource
                if (dataSource == null) {
                    renderer.renderError("No database selected. Please select a data source first.")
                    isGenerating = false
                    return@launch
                }

                val databaseConfig = dataSource.toDatabaseConfig()
                val projectPath = workspace?.rootPath ?: "."
                val mcpConfigService = McpToolConfigService(ToolConfigFile())

                val agent = ChatDBAgent(
                    projectPath = projectPath,
                    llmService = service,
                    databaseConfig = databaseConfig,
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

                val result = agent.execute(task) { progress ->
                    // Progress callback - can be used for UI updates
                    println("[ChatDB] Progress: $progress")
                }

                // Render the result to the timeline
                if (result.success) {
                    // Add the successful result as an assistant message to properly render markdown tables
                    renderer.renderLLMResponseStart()
                    renderer.renderLLMResponseChunk(result.content)
                    renderer.renderLLMResponseEnd()
                } else {
                    renderer.renderError("Query failed: ${result.content}")
                }

                // Close the agent connection
                agent.close()

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

    fun getSchema(): DatabaseSchema? = currentSchema

    fun dispose() {
        stopGeneration()
        disconnect()
        scope.cancel()
    }
}

