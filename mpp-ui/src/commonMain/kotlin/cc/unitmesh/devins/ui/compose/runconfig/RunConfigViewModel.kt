package cc.unitmesh.devins.ui.compose.runconfig

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    val state: StateFlow<RunConfigState> = service.state
    val configs: StateFlow<List<RunConfig>> = service.configs
    val defaultConfig: StateFlow<RunConfig?> = service.defaultConfig
    val isRunning: StateFlow<Boolean> = service.isRunning
    val runningConfigId: StateFlow<String?> = service.runningConfigId

    private val _output = MutableStateFlow<String>("")
    val output: StateFlow<String> = _output.asStateFlow()

    private val _analysisLog = MutableStateFlow<String>("")
    val analysisLog: StateFlow<String> = _analysisLog.asStateFlow()

    var progressMessage by mutableStateOf<String?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun analyzeProject() {
        scope.launch {
            errorMessage = null
            _analysisLog.value = "🔍 Starting project analysis...\n\n"
            progressMessage = "Analyzing with AI..."

            service.analyzeProject { progress ->
                progressMessage = progress.take(50)
                // Append progress directly (streaming style)
                _analysisLog.value = _analysisLog.value + progress
            }

            // Check for errors from service
            service.errorMessage.value?.let { error ->
                errorMessage = error
                _analysisLog.value = _analysisLog.value + "\n\n❌ Error: $error\n"
            }

            progressMessage = null
            val configCount = configs.value.size
            if (configCount > 0) {
                val configSummary = configs.value.take(5).joinToString("\n") { "  • ${it.name}: ${it.command}" }
                _analysisLog.value += "\n\n📋 Discovered configurations:\n$configSummary"
                if (configCount > 5) {
                    _analysisLog.value += "\n  ... and ${configCount - 5} more"
                }
            }
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
                _output.value += "\n\n[FAILED] ${result.error ?: "Command failed"}\n"
            } else {
                _output.value += "\n\n[DONE] Exit code: ${result.exitCode ?: 0}\n"
            }
        }
    }

    /**
     * Stop the currently running command
     */
    fun stopRunning() {
        scope.launch {
            service.stopRunning()
            _output.value += "\n[STOPPED] Command cancelled by user\n"
        }
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

