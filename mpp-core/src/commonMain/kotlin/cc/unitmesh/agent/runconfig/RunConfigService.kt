package cc.unitmesh.agent.runconfig

import cc.unitmesh.agent.logging.getLogger
import cc.unitmesh.agent.tool.filesystem.DefaultToolFileSystem
import cc.unitmesh.agent.tool.filesystem.ToolFileSystem
import cc.unitmesh.agent.tool.shell.DefaultShellExecutor
import cc.unitmesh.agent.tool.shell.LiveShellExecutor
import cc.unitmesh.agent.tool.shell.LiveShellSession
import cc.unitmesh.agent.tool.shell.ShellExecutionConfig
import cc.unitmesh.agent.tool.shell.ShellExecutor
import cc.unitmesh.llm.LLMService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * RunConfigService - Manages run configurations for a project.
 * 
 * This service:
 * 1. Uses LLM to analyze project and generate run configurations (streaming)
 * 2. Executes run configs via shell
 * 3. Persists configs to .xiuper/run-configs.json
 * 
 * Note: Requires LLM service for project analysis. If llmService is null,
 * users must add run configs manually.
 */
class RunConfigService(
    private val projectPath: String,
    private val fileSystem: ToolFileSystem = DefaultToolFileSystem(projectPath = projectPath),
    private val shellExecutor: ShellExecutor = DefaultShellExecutor(),
    private val llmService: LLMService? = null
) {
    private val logger = getLogger("RunConfigService")
    private val json = Json { 
        prettyPrint = true 
        ignoreUnknownKeys = true 
    }
    
    // LLM-based analyzer
    private val llmAnalyzer: LLMRunConfigAnalyzer? = llmService?.let { 
        LLMRunConfigAnalyzer(projectPath, fileSystem, it) 
    }
    
    private val configStoragePath = "$projectPath/.xiuper/run-configs.json"
    
    // State
    private val _state = MutableStateFlow(RunConfigState.NOT_CONFIGURED)
    val state: StateFlow<RunConfigState> = _state.asStateFlow()
    
    private val _configs = MutableStateFlow<List<RunConfig>>(emptyList())
    val configs: StateFlow<List<RunConfig>> = _configs.asStateFlow()
    
    private val _defaultConfig = MutableStateFlow<RunConfig?>(null)
    val defaultConfig: StateFlow<RunConfig?> = _defaultConfig.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private val _runningConfigId = MutableStateFlow<String?>(null)
    val runningConfigId: StateFlow<String?> = _runningConfigId.asStateFlow()
    
    /**
     * Initialize - try to load saved configs or analyze project
     */
    suspend fun initialize() {
        // Try to load saved configs first
        val loaded = loadConfigs()
        if (loaded != null && loaded.configs.isNotEmpty()) {
            _configs.value = loaded.configs
            _defaultConfig.value = loaded.configs.find { it.id == loaded.defaultConfigId }
                ?: loaded.configs.find { it.isDefault }
            _state.value = RunConfigState.CONFIGURED
            logger.info { "Loaded ${loaded.configs.size} run configs from storage" }
            return
        }
        
        // Otherwise, analyze project
        analyzeProject()
    }
    
    /**
     * Analyze project to discover run configurations using LLM streaming.
     */
    suspend fun analyzeProject(onProgress: (String) -> Unit = {}) {
        if (_state.value == RunConfigState.ANALYZING) return
        
        _state.value = RunConfigState.ANALYZING
        _errorMessage.value = null
        
        try {
            // Check if LLM is available
            if (llmAnalyzer == null) {
                _state.value = RunConfigState.ERROR
                _errorMessage.value = "LLM service not configured. Please configure an LLM provider first."
                onProgress("Error: LLM service not available")
                return
            }
            
            var discoveredConfigs: List<RunConfig> = emptyList()
            
            onProgress("Using AI to analyze project...")
            
            llmAnalyzer.analyzeStreaming().collect { event ->
                when (event) {
                    is AnalysisEvent.Progress -> onProgress(event.message)
                    is AnalysisEvent.Complete -> discoveredConfigs = event.configs
                    is AnalysisEvent.Error -> {
                        logger.warn { "LLM analysis failed: ${event.message}" }
                        _errorMessage.value = event.message
                    }
                }
            }
            
            if (discoveredConfigs.isNotEmpty()) {
                _configs.value = discoveredConfigs
                _defaultConfig.value = discoveredConfigs.find { it.isDefault }
                    ?: discoveredConfigs.firstOrNull { it.type == RunConfigType.RUN }
                    ?: discoveredConfigs.firstOrNull()
                _state.value = RunConfigState.CONFIGURED
                
                // Save configs
                saveConfigs()
                
                onProgress("Found ${discoveredConfigs.size} run configurations")
                logger.info { "Discovered ${discoveredConfigs.size} run configs" }
            } else {
                _state.value = RunConfigState.NOT_CONFIGURED
                onProgress("No run configurations found. You can add custom configs manually.")
            }
        } catch (e: Exception) {
            _state.value = RunConfigState.ERROR
            _errorMessage.value = e.message ?: "Analysis failed"
            logger.error { "Failed to analyze project: ${e.message}" }
        }
    }
    
    /**
     * Get streaming analysis flow for UI integration.
     * This allows the UI to show real-time LLM reasoning.
     */
    fun analyzeStreamingFlow(): Flow<AnalysisEvent>? {
        return llmAnalyzer?.analyzeStreaming()
    }
    
    /**
     * Execute a run configuration
     */
    suspend fun execute(
        configId: String,
        onOutput: (String) -> Unit = {}
    ): RunConfigResult {
        val config = _configs.value.find { it.id == configId }
            ?: return RunConfigResult(
                success = false,
                error = "Run config not found: $configId"
            )
        
        return execute(config, onOutput)
    }
    
    /**
     * Execute a run configuration with streaming output support.
     * Uses live execution for long-running commands to stream output in real-time.
     */
    suspend fun execute(
        config: RunConfig,
        onOutput: (String) -> Unit = {}
    ): RunConfigResult {
        if (_isRunning.value) {
            return RunConfigResult(
                success = false,
                error = "Another command is already running"
            )
        }
        
        _isRunning.value = true
        _runningConfigId.value = config.id
        
        logger.info { "Executing run config: ${config.name} -> ${config.command}" }
        onOutput("$ ${config.command}\n")
        
        return kotlinx.coroutines.coroutineScope {
            executionJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
            
            try {
                val workDir = if (config.workingDir == ".") {
                    projectPath
                } else {
                    "$projectPath/${config.workingDir}"
                }
                
                val shellConfig = ShellExecutionConfig(
                    workingDirectory = workDir,
                    timeoutMs = 600000L, // 10 minutes for long-running tasks
                    environment = config.env
                )
                
                // Check if live execution is supported for streaming output
                val liveExecutor = shellExecutor as? LiveShellExecutor
                if (liveExecutor != null && liveExecutor.supportsLiveExecution()) {
                    // Use live streaming execution for real-time output
                    executeWithLiveStreaming(config.command, shellConfig, onOutput)
                } else {
                    // Fallback to blocking execution
                    executeBlocking(config.command, shellConfig, onOutput)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                logger.info { "Command execution cancelled" }
                onOutput("\n[STOPPED] Command cancelled by user\n")
                RunConfigResult(
                    success = false,
                    error = "Cancelled by user"
                )
            } catch (e: Exception) {
                logger.error { "Failed to execute command: ${e.message}" }
                onOutput("\n[ERROR] ${e.message}")
                
                RunConfigResult(
                    success = false,
                    error = e.message ?: "Execution failed"
                )
            } finally {
                _isRunning.value = false
                _runningConfigId.value = null
                currentSession = null
                currentProcessHandle = null
                executionJob = null
            }
        }
    }
    
    /**
     * Execute with live streaming output (for long-running commands like bootRun)
     */
    private suspend fun executeWithLiveStreaming(
        command: String,
        config: ShellExecutionConfig,
        onOutput: (String) -> Unit
    ): RunConfigResult {
        val liveExecutor = shellExecutor as LiveShellExecutor
        val session = liveExecutor.startLiveExecution(command, config)
        
        // Store session for potential stop functionality
        currentSession = session
        
        try {
            // Read output from PTY process incrementally
            val process = session.ptyHandle
            
            return if (isNativeProcess(process)) {
                currentProcessHandle = process
                readProcessOutputPlatform(process, onOutput, config.timeoutMs) { pid ->
                    // Process started callback
                }
            } else {
                // For PTY process, use the PTY-specific handling
                readPtyOutput(session, onOutput, config.timeoutMs)
            }
        } finally {
            currentSession = null
            currentProcessHandle = null
        }
    }
    
    private var currentSession: LiveShellSession? = null
    private var currentProcessHandle: Any? = null
    private var executionJob: kotlinx.coroutines.Job? = null
    
    /**
     * Stop the currently running command
     */
    suspend fun stopRunning() {
        if (!_isRunning.value) {
            logger.warn { "Attempted to stop when no command is running" }
            return
        }
        
        logger.info { "Stopping running command..." }
        
        // Cancel the execution job if it exists
        executionJob?.cancel()
        
        // Kill the process using platform-specific implementation
        currentProcessHandle?.let { handle ->
            try {
                killProcessPlatform(handle)
                logger.info { "Process killed forcefully" }
            } catch (e: Exception) {
                logger.error { "Failed to kill process: ${e.message}" }
            }
        }
        
        // Kill the session if it exists
        currentSession?.let { session ->
            try {
                session.kill()
                logger.info { "Session killed" }
            } catch (e: Exception) {
                logger.error { "Failed to kill session: ${e.message}" }
            }
        }
        
        // Reset state
        _isRunning.value = false
        _runningConfigId.value = null
        currentSession = null
        currentProcessHandle = null
        executionJob = null
    }
    
    /**
     * Read output from PTY session
     */
    private suspend fun readPtyOutput(
        session: LiveShellSession,
        onOutput: (String) -> Unit,
        timeoutMs: Long
    ): RunConfigResult {
        val liveExecutor = shellExecutor as LiveShellExecutor
        
        try {
            val exitCode = liveExecutor.waitForSession(session, timeoutMs)
            
            val stdout = session.getStdout()
            val stderr = session.getStderr()
            
            if (stdout.isNotEmpty()) onOutput(stdout)
            if (stderr.isNotEmpty()) onOutput("\n[STDERR]\n$stderr")
            
            return RunConfigResult(
                success = exitCode == 0,
                exitCode = exitCode,
                message = if (exitCode == 0) "Command completed" else "Command failed"
            )
        } catch (e: Exception) {
            // Timeout or other error - process may still be running
            onOutput("\n[INFO] ${e.message}\n")
            return RunConfigResult(
                success = true,
                message = "Process may still be running"
            )
        }
    }
    
    /**
     * Fallback blocking execution
     */
    private suspend fun executeBlocking(
        command: String,
        config: ShellExecutionConfig,
        onOutput: (String) -> Unit
    ): RunConfigResult {
        val result = shellExecutor.execute(command, config)
        
        onOutput(result.stdout)
        if (result.stderr.isNotBlank()) {
            onOutput("\n[STDERR]\n${result.stderr}")
        }
        
        return RunConfigResult(
            success = result.exitCode == 0,
            exitCode = result.exitCode,
            message = if (result.exitCode == 0) {
                "Command completed successfully"
            } else {
                "Command exited with code ${result.exitCode}"
            }
        )
    }
    
    /**
     * Execute the default run configuration
     */
    suspend fun executeDefault(onOutput: (String) -> Unit = {}): RunConfigResult {
        val default = _defaultConfig.value
            ?: return RunConfigResult(
                success = false,
                error = "No default run configuration"
            )
        
        return execute(default, onOutput)
    }
    
    /**
     * Set a config as the default
     */
    fun setDefaultConfig(configId: String) {
        val config = _configs.value.find { it.id == configId }
        if (config != null) {
            // Update default flags
            _configs.value = _configs.value.map { c ->
                c.copy(isDefault = c.id == configId)
            }
            _defaultConfig.value = config.copy(isDefault = true)
        }
    }
    
    /**
     * Add a custom run configuration
     */
    fun addConfig(config: RunConfig) {
        _configs.value = _configs.value + config.copy(source = RunConfigSource.USER_DEFINED)
        if (config.isDefault || _defaultConfig.value == null) {
            _defaultConfig.value = config
        }
        _state.value = RunConfigState.CONFIGURED
    }
    
    /**
     * Remove a run configuration
     */
    fun removeConfig(configId: String) {
        _configs.value = _configs.value.filter { it.id != configId }
        if (_defaultConfig.value?.id == configId) {
            _defaultConfig.value = _configs.value.firstOrNull { it.isDefault }
                ?: _configs.value.firstOrNull()
        }
        if (_configs.value.isEmpty()) {
            _state.value = RunConfigState.NOT_CONFIGURED
        }
    }
    
    /**
     * Get configs filtered by type
     */
    fun getConfigsByType(type: RunConfigType): List<RunConfig> {
        return _configs.value.filter { it.type == type }
    }
    
    /**
     * Save configs to storage
     */
    private suspend fun saveConfigs() {
        try {
            // Ensure .xiuper directory exists
            val xiuperDir = "$projectPath/.xiuper"
            if (!fileSystem.exists(xiuperDir)) {
                fileSystem.createDirectory(xiuperDir)
            }
            
            val storage = RunConfigStorage(
                projectPath = projectPath,
                configs = _configs.value,
                defaultConfigId = _defaultConfig.value?.id,
                lastAnalyzedAt = cc.unitmesh.agent.Platform.getCurrentTimestamp()
            )
            
            val content = json.encodeToString(RunConfigStorage.serializer(), storage)
            fileSystem.writeFile(configStoragePath, content)
            
            logger.debug { "Saved ${_configs.value.size} run configs to $configStoragePath" }
        } catch (e: Exception) {
            logger.warn { "Failed to save run configs: ${e.message}" }
        }
    }
    
    /**
     * Load configs from storage
     */
    private suspend fun loadConfigs(): RunConfigStorage? {
        return try {
            if (!fileSystem.exists(configStoragePath)) {
                return null
            }
            
            val content = fileSystem.readFile(configStoragePath) ?: return null
            json.decodeFromString<RunConfigStorage>(content)
        } catch (e: Exception) {
            logger.warn { "Failed to load run configs: ${e.message}" }
            null
        }
    }
    
    /**
     * Clear all configs and reset state
     */
    fun reset() {
        _configs.value = emptyList()
        _defaultConfig.value = null
        _state.value = RunConfigState.NOT_CONFIGURED
        _errorMessage.value = null
    }
}

