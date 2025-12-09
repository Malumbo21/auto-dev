package cc.unitmesh.devins.ui.compose.agent.chatdb

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.unitmesh.agent.database.DatabaseConnection
import cc.unitmesh.agent.database.DatabaseSchema
import cc.unitmesh.agent.database.createDatabaseConnection
import cc.unitmesh.config.ConfigManager
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
        // TODO: Load from persistent storage
        // For now, use empty list
        state = state.copy(dataSources = emptyList())
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
        saveDataSources()
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
        saveDataSources()
    }

    fun deleteDataSource(id: String) {
        state = state.copy(
            dataSources = state.dataSources.filter { it.id != id },
            selectedDataSourceId = if (state.selectedDataSourceId == id) null else state.selectedDataSourceId
        )
        if (state.selectedDataSourceId == id) {
            disconnect()
        }
        saveDataSources()
    }

    private fun saveDataSources() {
        // TODO: Persist to storage
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

                val schemaContext = currentSchema?.getDescription() ?: "No database connected"
                val systemPrompt = buildSystemPrompt(schemaContext)

                val response = StringBuilder()
                renderer.renderLLMResponseStart()

                service.streamPrompt(
                    userPrompt = "$systemPrompt\n\nUser: $text",
                    compileDevIns = false
                ).collect { chunk ->
                    response.append(chunk)
                    renderer.renderLLMResponseChunk(chunk)
                }

                renderer.renderLLMResponseEnd()

                // Try to extract and execute SQL if present
                extractAndExecuteSQL(response.toString())

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

    private fun buildSystemPrompt(schemaContext: String): String {
        return """You are a helpful SQL assistant. You help users write SQL queries based on their natural language questions.

## Database Schema
$schemaContext

## Instructions
1. Analyze the user's question and understand what data they need
2. Generate the appropriate SQL query
3. Wrap SQL queries in ```sql code blocks
4. Explain your query briefly

## Rules
- Only generate SELECT queries for safety
- Always use proper table and column names from the schema
- If you're unsure about the schema, ask for clarification
"""
    }

    private suspend fun extractAndExecuteSQL(response: String) {
        val sqlPattern = Regex("```sql\\n([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val match = sqlPattern.find(response)

        if (match != null && currentConnection != null) {
            val sql = match.groupValues[1].trim()
            try {
                val result = currentConnection!!.executeQuery(sql)
                // Display query result as a new message
                renderer.renderLLMResponseStart()
                renderer.renderLLMResponseChunk("\n\n**Query Result:**\n```\n${result.toTableString()}\n```")
                renderer.renderLLMResponseEnd()
            } catch (e: Exception) {
                renderer.renderError("Query Error: ${e.message}")
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

