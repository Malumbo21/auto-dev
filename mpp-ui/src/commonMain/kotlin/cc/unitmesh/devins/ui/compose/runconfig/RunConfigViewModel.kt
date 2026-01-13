package cc.unitmesh.devins.ui.compose.runconfig

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.unitmesh.agent.Platform
import cc.unitmesh.agent.runconfig.*
import cc.unitmesh.agent.tool.filesystem.DefaultToolFileSystem
import cc.unitmesh.agent.tool.shell.DefaultShellExecutor
import cc.unitmesh.llm.LLMService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing run configurations in the UI.
 *
 * This ViewModel:
 * 1. Initializes RunConfigService with the project path
 * 2. Uses LLM streaming for intelligent project analysis
 * 3. Exposes state flows for the UI
 * 4. Handles user interactions (analyze, run, stop)
 */
class RunConfigViewModel(
    projectPath: String,
    llmService: LLMService? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val service = RunConfigService(
        projectPath = projectPath,
        fileSystem = DefaultToolFileSystem(projectPath = projectPath),
        shellExecutor = DefaultShellExecutor(),
        llmService = llmService
    )

    // State exposed to UI
    val state: StateFlow<RunConfigState> = service.state
    val configs: StateFlow<List<RunConfig>> = service.configs
    val defaultConfig: StateFlow<RunConfig?> = service.defaultConfig
    val isRunning: StateFlow<Boolean> = service.isRunning
    val runningConfigId: StateFlow<String?> = service.runningConfigId

    // Output from running commands
    private val _output = MutableStateFlow<String>("")
    val output: StateFlow<String> = _output.asStateFlow()

    // Streaming analysis log (shows LLM reasoning)
    private val _analysisLog = MutableStateFlow<String>("")
    val analysisLog: StateFlow<String> = _analysisLog.asStateFlow()

    // Progress message during analysis
    var progressMessage by mutableStateOf<String?>(null)
        private set

    // Error message
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        // Initialize on creation
//        scope.launch {
//            service.initialize()
//        }
    }

    /**
     * Analyze project to discover run configurations using LLM streaming.
     */
    fun analyzeProject() {
        scope.launch {
            errorMessage = null
            _analysisLog.value = "🔍 Starting project analysis...\n\n"
            progressMessage = "Analyzing with AI..."
            println("[RunConfigViewModel] Starting project analysis...")

            service.analyzeProject { progress ->
                progressMessage = progress.take(50)
                // Append progress directly (streaming style)
                _analysisLog.value = _analysisLog.value + progress
                println("[RunConfigViewModel] Progress: $progress")
            }

            // Check for errors from service
            service.errorMessage.value?.let { error ->
                errorMessage = error
                _analysisLog.value = _analysisLog.value + "\n\n❌ Error: $error\n"
            }

            progressMessage = null
            val configCount = configs.value.size
            if (configCount > 0) {
                // Show discovered configs summary
                val configSummary = configs.value.take(5).joinToString("\n") { "  • ${it.name}: ${it.command}" }
                _analysisLog.value = _analysisLog.value + "\n\n📋 Discovered configurations:\n$configSummary"
                if (configCount > 5) {
                    _analysisLog.value = _analysisLog.value + "\n  ... and ${configCount - 5} more"
                }
            }
            println("[RunConfigViewModel] Analysis complete. Found $configCount configs, default=${defaultConfig.value?.name}")
        }
    }

    /**
     * Clear the analysis log
     */
    fun clearAnalysisLog() {
        _analysisLog.value = ""
    }

    /**
     * Run a configuration
     */
    fun runConfig(config: RunConfig) {
        scope.launch {
            _output.value = "Starting: ${config.command}\n"
            errorMessage = null
            val result = service.execute(config) { outputLine ->
                _output.value = _output.value + outputLine
            }

            if (!result.success) {
                errorMessage = result.error ?: "Command failed"
                _output.value = _output.value + "\n\n[FAILED] ${result.error ?: "Command failed"}\n"
            } else {
                _output.value = _output.value + "\n\n[DONE] Exit code: ${result.exitCode ?: 0}\n"
            }
        }
    }

    /**
     * Run the default configuration
     */
    fun runDefault() {
        defaultConfig.value?.let { runConfig(it) }
    }

    /**
     * Stop the currently running command
     */
    fun stopRunning() {
        scope.launch {
            service.stopRunning()
            _output.value = _output.value + "\n[STOPPED] Command cancelled by user\n"
        }
    }

    /**
     * Set a config as the default
     */
    fun setDefaultConfig(configId: String) {
        service.setDefaultConfig(configId)
    }

    /**
     * Add a custom configuration
     */
    fun addCustomConfig(
        name: String,
        command: String,
        type: RunConfigType = RunConfigType.CUSTOM,
        description: String = ""
    ) {
        val config = RunConfig(
            id = "custom-${Platform.getCurrentTimestamp()}",
            name = name,
            type = type,
            command = command,
            description = description,
            source = RunConfigSource.USER_DEFINED
        )
        service.addConfig(config)
    }

    /**
     * Remove a configuration
     */
    fun removeConfig(configId: String) {
        service.removeConfig(configId)
    }

    /**
     * Clear output
     */
    fun clearOutput() {
        _output.value = ""
    }

    /**
     * Clear error
     */
    fun clearError() {
        errorMessage = null
    }

    /**
     * Reset and re-analyze
     */
    fun reset() {
        service.reset()
        _output.value = ""
        errorMessage = null
        progressMessage = null
    }
}

