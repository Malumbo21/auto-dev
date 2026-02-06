package cc.unitmesh.devins.idea.toolwindow.acp

import cc.unitmesh.agent.acp.AcpAgentProcessManager
import cc.unitmesh.agent.acp.AcpClient
import cc.unitmesh.agent.plan.MarkdownPlanParser
import cc.unitmesh.config.AutoDevConfigWrapper
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devins.idea.renderer.JewelRenderer
import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.client.ClientOperationsFactory
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.*
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

private val acpLogger = Logger.getInstance("AutoDevAcpAgent")

/**
 * ACP (Agent Client Protocol) ViewModel for IntelliJ IDEA plugin.
 *
 * Features:
 * - Spawn ACP agent as a local process (JSON-RPC over stdio)
 * - initialize -> session/new -> session/prompt
 * - Render session/update streaming events into existing timeline UI
 * - Agent preset selection (Codex, Kimi, Gemini, Claude, Copilot)
 * - config.yaml integration for persistent agent configuration
 * - Process reuse via AcpAgentProcessManager
 * - Auto-approve permissions so agents can actually work
 * - Multi-turn prompt support within a single session
 */
class IdeaAcpAgentViewModel(
    val project: Project,
    private val coroutineScope: CoroutineScope,
    /**
     * Optional external renderer. When provided, ACP output renders to the shared timeline
     * (e.g., CODING tab's renderer). When null, creates its own standalone renderer.
     */
    externalRenderer: JewelRenderer? = null,
) : Disposable {
    val renderer: JewelRenderer = externalRenderer ?: JewelRenderer()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _stderrTail = MutableStateFlow<List<String>>(emptyList())
    val stderrTail: StateFlow<List<String>> = _stderrTail.asStateFlow()

    /**
     * Available ACP agents from config.yaml.
     * Key is the agent key (e.g., "codex"), value is the config.
     */
    private val _availableAgents = MutableStateFlow<Map<String, cc.unitmesh.config.AcpAgentConfig>>(emptyMap())
    val availableAgents: StateFlow<Map<String, cc.unitmesh.config.AcpAgentConfig>> = _availableAgents.asStateFlow()

    /**
     * Currently selected agent key (e.g., "codex", "kimi").
     */
    private val _selectedAgentKey = MutableStateFlow<String?>(null)
    val selectedAgentKey: StateFlow<String?> = _selectedAgentKey.asStateFlow()

    /**
     * Detected presets on the system.
     */
    private val _installedPresets = MutableStateFlow<List<IdeaAcpAgentPreset>>(emptyList())
    val installedPresets: StateFlow<List<IdeaAcpAgentPreset>> = _installedPresets.asStateFlow()

    private var process: Process? = null
    private var protocol: Protocol? = null
    private var client: Client? = null
    private var session: ClientSession? = null

    private var stderrJob: Job? = null
    private var connectJob: Job? = null
    private var currentPromptJob: Job? = null

    private val receivedAnyAgentChunk = AtomicBoolean(false)
    private val inThoughtStream = AtomicBoolean(false)

    /**
     * Per-prompt tool call dedup state (shared with AcpClient.renderSessionUpdate).
     * Prevents flooding the renderer with thousands of IN_PROGRESS tool call updates.
     */
    private val renderedToolCallIds = mutableSetOf<String>()
    private val toolCallTitles = mutableMapOf<String, String>()
    private val startedToolCallIds = mutableSetOf<String>()

    init {
        // Load agents from config.yaml and detect presets
        coroutineScope.launch(Dispatchers.IO) {
            loadAgentsFromConfig()
            detectPresets()
        }
    }

    /**
     * Load ACP agents from ~/.autodev/config.yaml.
     */
    private suspend fun loadAgentsFromConfig() {
        try {
            val wrapper = ConfigManager.load()
            _availableAgents.value = wrapper.getAcpAgents()
            _selectedAgentKey.value = wrapper.getActiveAcpAgentKey()
        } catch (e: Exception) {
            acpLogger.warn("Failed to load ACP agents from config", e)
        }
    }

    /**
     * Detect installed ACP agent presets on the system.
     */
    private fun detectPresets() {
        try {
            _installedPresets.value = IdeaAcpAgentPreset.detectInstalled()
        } catch (e: Exception) {
            acpLogger.warn("Failed to detect ACP presets", e)
        }
    }

    /**
     * Select an agent from the available list and optionally connect.
     */
    fun selectAgent(key: String) {
        _selectedAgentKey.value = key
        // Save to config.yaml
        coroutineScope.launch(Dispatchers.IO) {
            try {
                AutoDevConfigWrapper.saveActiveAcpAgent(key)
            } catch (e: Exception) {
                acpLogger.warn("Failed to save active ACP agent", e)
            }
        }
    }

    /**
     * Add a preset as an agent configuration and select it.
     */
    fun addPresetAgent(preset: IdeaAcpAgentPreset) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val agents = _availableAgents.value.toMutableMap()
                agents[preset.id] = preset.toConfig()
                AutoDevConfigWrapper.saveAcpAgents(agents, activeKey = preset.id)
                _availableAgents.value = agents
                _selectedAgentKey.value = preset.id
            } catch (e: Exception) {
                acpLogger.warn("Failed to add preset agent", e)
            }
        }
    }

    /**
     * Save a custom agent config and select it.
     */
    fun saveCustomAgent(key: String, config: cc.unitmesh.config.AcpAgentConfig) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val agents = _availableAgents.value.toMutableMap()
                agents[key] = config
                AutoDevConfigWrapper.saveAcpAgents(agents, activeKey = key)
                _availableAgents.value = agents
                _selectedAgentKey.value = key
            } catch (e: Exception) {
                acpLogger.warn("Failed to save custom agent", e)
            }
        }
    }

    /**
     * Remove an agent from config.
     */
    fun removeAgent(key: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val agents = _availableAgents.value.toMutableMap()
                agents.remove(key)
                val newActive = if (_selectedAgentKey.value == key) agents.keys.firstOrNull() else _selectedAgentKey.value
                AutoDevConfigWrapper.saveAcpAgents(agents, activeKey = newActive)
                _availableAgents.value = agents
                _selectedAgentKey.value = newActive
            } catch (e: Exception) {
                acpLogger.warn("Failed to remove agent", e)
            }
        }
    }

    /**
     * Reload agents from config (e.g., after external changes).
     */
    fun reloadAgents() {
        coroutineScope.launch(Dispatchers.IO) {
            loadAgentsFromConfig()
        }
    }

    /**
     * Connect using the currently selected agent.
     */
    fun connectSelectedAgent() {
        val key = _selectedAgentKey.value
        val config = if (key != null) _availableAgents.value[key] else null

        if (config == null) {
            _connectionError.value = "No agent selected. Please select an ACP agent first."
            return
        }

        connectWithConfig(key!!, config)
    }

    /**
     * Connect using a legacy manual config (backward compatibility).
     */
    fun connect(config: AcpAgentConfig) {
        val acpConfig = cc.unitmesh.config.AcpAgentConfig(
            name = config.command,
            command = config.command,
            args = config.args,
            env = config.envText
        )
        connectWithConfig("manual", acpConfig, config.cwd)
    }

    private fun connectWithConfig(
        agentKey: String,
        config: cc.unitmesh.config.AcpAgentConfig,
        overrideCwd: String? = null,
    ) {
        connectJob?.cancel()
        connectJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                disconnectInternal()
                receivedAnyAgentChunk.set(false)
                inThoughtStream.set(false)
                _connectionError.value = null
                _stderrTail.value = emptyList()

                val cmd = config.command.trim()
                if (cmd.isBlank()) {
                    _connectionError.value = "ACP agent command is empty."
                    _isConnected.value = false
                    return@launch
                }

                val cwd = overrideCwd?.ifBlank { null }
                    ?: project.basePath
                    ?: System.getProperty("user.home")

                // Use AcpAgentProcessManager for process lifecycle
                val processManager = AcpAgentProcessManager.getInstance()
                val managed = processManager.getOrCreateProcess(agentKey, config, cwd)
                process = managed.process

                // Tail stderr for debugging and user visibility.
                stderrJob = coroutineScope.launch(Dispatchers.IO) {
                    readStderrTail(managed.process)
                }

                val input = managed.inputStream.asSource().buffered()
                val output = managed.outputStream.asSink().buffered()

                val transport = StdioTransport(
                    coroutineScope,
                    Dispatchers.IO,
                    input,
                    output,
                    "autodev-acp"
                )
                val protocol = Protocol(coroutineScope, transport)
                protocol.start()

                val client = Client(protocol)
                this@IdeaAcpAgentViewModel.protocol = protocol
                this@IdeaAcpAgentViewModel.client = client

                val fsCapabilities = FileSystemCapability(
                    readTextFile = true,
                    writeTextFile = true,
                    _meta = JsonNull
                )

                val clientInfo = ClientInfo(
                    protocolVersion = 1,
                    capabilities = ClientCapabilities(
                        fs = fsCapabilities,
                        terminal = true,
                        _meta = JsonNull
                    ),
                    implementation = Implementation(
                        name = "autodev-xiuper",
                        version = "dev",
                        title = "AutoDev Xiuper IDEA (ACP Client)",
                        _meta = JsonNull
                    ),
                    _meta = JsonNull
                )

                client.initialize(clientInfo, JsonNull)

                // Load MCP servers from config.yaml
                val mcpServers: List<McpServer> = try {
                    val wrapper = ConfigManager.load()
                    val enabled = wrapper.getEnabledMcpServers()
                    enabled.mapNotNull { (serverName, cfg) ->
                        val cfgCommand = cfg.command
                        val cfgUrl = cfg.url
                        when {
                            cfgCommand != null -> {
                                McpServer.Stdio(
                                    name = serverName,
                                    command = cfgCommand,
                                    args = cfg.args,
                                    env = (cfg.env ?: emptyMap()).entries.map { (k, v) ->
                                        EnvVariable(name = k, value = v, _meta = JsonNull)
                                    }
                                )
                            }
                            cfgUrl != null -> {
                                val headers = (cfg.headers ?: emptyMap()).entries.map { (k, v) ->
                                    HttpHeader(name = k, value = v, _meta = JsonNull)
                                }
                                if (cfgUrl.contains("/sse", ignoreCase = true)) {
                                    McpServer.Sse(name = serverName, url = cfgUrl, headers = headers)
                                } else {
                                    McpServer.Http(name = serverName, url = cfgUrl, headers = headers)
                                }
                            }
                            else -> null
                        }
                    }
                } catch (e: Exception) {
                    acpLogger.warn("Failed to load MCP servers from config", e)
                    emptyList()
                }

                val operationsFactory = object : ClientOperationsFactory {
                    override suspend fun createClientOperations(
                        sessionId: SessionId,
                        sessionResponse: AcpCreatedSessionResponse,
                    ): ClientSessionOperations {
                        return cc.unitmesh.agent.acp.AcpClientSessionOps(
                            onSessionUpdate = { update ->
                                handleSessionUpdate(update)
                            },
                            onPermissionRequest = { toolCallUpdate, options ->
                                handlePermissionRequest(toolCallUpdate, options)
                            },
                            cwd = cwd,
                            enableFs = true,
                            enableTerminal = true,
                        )
                    }
                }

                val session = client.newSession(
                    SessionCreationParameters(
                        cwd = cwd,
                        mcpServers = mcpServers,
                        _meta = JsonNull
                    ),
                    operationsFactory
                )
                this@IdeaAcpAgentViewModel.session = session

                _isConnected.value = true
                _connectionError.value = null
                acpLogger.info("ACP agent '$agentKey' connected successfully (session=${session.sessionId})")
            } catch (e: CancellationException) {
                // Ignore
            } catch (e: Exception) {
                _isConnected.value = false
                _connectionError.value = "Failed to start ACP agent: ${e.message}"
                acpLogger.warn("ACP connect failed", e)
                disconnectInternal()
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        coroutineScope.launch(Dispatchers.IO) {
            disconnectInternal()
        }
    }

    fun sendMessage(text: String) {
        if (_isExecuting.value) return
        if (!_isConnected.value || session == null) {
            renderer.renderError("ACP agent is not connected. Please start it first.")
            return
        }

        renderer.clearError()
        renderer.addUserMessage(text)

        _isExecuting.value = true
        currentPromptJob?.cancel()
        currentPromptJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                receivedAnyAgentChunk.set(false)
                inThoughtStream.set(false)
                renderedToolCallIds.clear()
                toolCallTitles.clear()
                startedToolCallIds.clear()

                val flow = session!!.prompt(
                    listOf(ContentBlock.Text(text, Annotations(), JsonNull)),
                    JsonNull
                )

                flow.collect { event ->
                    when (event) {
                        is Event.SessionUpdateEvent -> handleSessionUpdate(event.update)
                        is Event.PromptResponseEvent -> {
                            finishStreamingIfNeeded()
                            val success = event.response.stopReason != StopReason.REFUSAL &&
                                event.response.stopReason != StopReason.CANCELLED
                            renderer.renderFinalResult(
                                success = success,
                                message = "ACP finished: ${event.response.stopReason}",
                                iterations = 0
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                // If user cancels, try to cancel the session turn too.
                try {
                    session?.cancel()
                } catch (_: Exception) {
                }
                renderer.forceStop()
                renderer.renderError("ACP turn cancelled by user.")
            } catch (e: Exception) {
                finishStreamingIfNeeded()
                renderer.renderError(e.message ?: "ACP execution error")
            } finally {
                _isExecuting.value = false
                currentPromptJob = null
            }
        }
    }

    fun cancelTask() {
        currentPromptJob?.cancel(CancellationException("Cancelled by user"))
        currentPromptJob = null
        _isExecuting.value = false
        coroutineScope.launch(Dispatchers.IO) {
            try {
                session?.cancel()
            } catch (_: Exception) {
            }
        }
    }

    private fun finishStreamingIfNeeded() {
        if (renderer.isProcessing.value) {
            renderer.renderLLMResponseEnd()
        }
        if (inThoughtStream.getAndSet(false)) {
            renderer.renderThinkingChunk("", isStart = false, isEnd = true)
        }
    }

    /**
     * Handle permission requests from the agent.
     *
     * Auto-approves ALLOW_ONCE or ALLOW_ALWAYS options so agents can actually perform
     * tool operations (shell, file read/write). Without this, agents get stuck in a
     * "tool call failed -> END_TURN with no output" state.
     *
     * IDE integrations (Compose UI) SHOULD eventually override this to prompt the user
     * for confirmation on dangerous operations.
     */
    private fun handlePermissionRequest(
        toolCall: SessionUpdate.ToolCallUpdate,
        options: List<PermissionOption>,
    ): RequestPermissionResponse {
        val allow = options.firstOrNull {
            it.kind == PermissionOptionKind.ALLOW_ONCE || it.kind == PermissionOptionKind.ALLOW_ALWAYS
        }
        return if (allow != null) {
            acpLogger.info(
                "ACP permission auto-approved (${allow.kind}) for tool=${toolCall.title ?: "tool"} option=${allow.name}"
            )
            RequestPermissionResponse(RequestPermissionOutcome.Selected(allow.optionId), JsonNull)
        } else {
            acpLogger.info(
                "ACP permission cancelled (no allow option) for tool=${toolCall.title ?: "tool"}"
            )
            RequestPermissionResponse(RequestPermissionOutcome.Cancelled, JsonNull)
        }
    }

    /**
     * Handle ACP session update using the shared utility from [AcpClient.renderSessionUpdate].
     *
     * This provides proper tool call deduplication: ACP streams many IN_PROGRESS updates
     * per tool call (title grows char-by-char), but we only render terminal events
     * (COMPLETED/FAILED). This reduces thousands of events to a handful of timeline items.
     *
     * For PlanUpdate, we additionally parse to the IDEA-specific plan model via [renderer.setPlan].
     */
    private fun handleSessionUpdate(update: SessionUpdate, source: String = "prompt") {
        // Special handling for PlanUpdate to use JewelRenderer's setPlan
        if (update is SessionUpdate.PlanUpdate) {
            val markdown = buildString {
                update.entries.forEachIndexed { index, entry ->
                    val marker = when (entry.status) {
                        PlanEntryStatus.COMPLETED -> "[x] "
                        PlanEntryStatus.IN_PROGRESS -> "[*] "
                        PlanEntryStatus.PENDING -> ""
                        else -> ""
                    }
                    appendLine("${index + 1}. $marker${entry.content}")
                }
            }.trim()

            if (markdown.isNotBlank()) {
                try {
                    val plan = MarkdownPlanParser.parseToPlan(markdown)
                    renderer.setPlan(plan)
                } catch (e: Exception) {
                    acpLogger.warn("Failed to parse ACP plan update", e)
                }
            }
            return
        }

        // Delegate to the shared utility for all other update types
        AcpClient.renderSessionUpdate(
            update = update,
            renderer = renderer,
            getReceivedChunk = { receivedAnyAgentChunk.get() },
            setReceivedChunk = { receivedAnyAgentChunk.set(it) },
            getInThought = { inThoughtStream.get() },
            setInThought = { inThoughtStream.set(it) },
            renderedToolCallIds = renderedToolCallIds,
            toolCallTitles = toolCallTitles,
            startedToolCallIds = startedToolCallIds,
        )
    }

    private suspend fun disconnectInternal() {
        _isConnected.value = false
        _isExecuting.value = false
        currentPromptJob?.cancel()
        currentPromptJob = null

        try {
            protocol?.close()
        } catch (_: Exception) {
        }
        protocol = null
        client = null
        session = null

        stderrJob?.cancel()
        stderrJob = null

        // Note: we do NOT destroy the process here because AcpAgentProcessManager
        // manages the process lifecycle and allows reuse. The process will be
        // cleaned up when the ViewModel is disposed or when the process is explicitly terminated.
        process = null
    }

    private fun readStderrTail(p: Process, maxLines: Int = 200) {
        try {
            BufferedReader(InputStreamReader(p.errorStream)).useLines { lines ->
                lines.forEach { line ->
                    _stderrTail.value = (_stderrTail.value + line).takeLast(maxLines)
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun dispose() {
        disconnect()
        // Terminate all managed processes on dispose
        AcpAgentProcessManager.getInstance().shutdownAll()
    }
}

/**
 * Legacy config class for backward compatibility with the manual config panel.
 */
data class AcpAgentConfig(
    val command: String,
    val args: String,
    val envText: String,
    val cwd: String,
)

private fun parseEnvLines(text: String): Map<String, String> {
    val result = linkedMapOf<String, String>()
    text.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
        val idx = trimmed.indexOf('=')
        if (idx <= 0) return@forEach
        val key = trimmed.substring(0, idx).trim()
        val value = trimmed.substring(idx + 1).trim()
        if (key.isNotBlank()) {
            result[key] = value
        }
    }
    return result
}

/**
 * Minimal argv parser that supports quoting with double quotes.
 *
 * Example: `--foo "bar baz"` => ["--foo", "bar baz"]
 */
private fun splitArgs(text: String): List<String> {
    val s = text.trim()
    if (s.isEmpty()) return emptyList()

    val out = mutableListOf<String>()
    val buf = StringBuilder()
    var inQuotes = false
    var escape = false

    fun flush() {
        if (buf.isNotEmpty()) {
            out.add(buf.toString())
            buf.setLength(0)
        }
    }

    for (ch in s) {
        when {
            escape -> {
                buf.append(ch)
                escape = false
            }
            ch == '\\' -> {
                escape = true
            }
            ch == '"' -> {
                inQuotes = !inQuotes
            }
            ch.isWhitespace() && !inQuotes -> {
                flush()
            }
            else -> buf.append(ch)
        }
    }
    flush()
    return out
}

/**
 * ACP agent preset for auto-detection in the IDEA plugin.
 *
 * This is a self-contained version of AcpAgentPreset from mpp-ui,
 * since mpp-idea does not depend on mpp-ui.
 */
data class IdeaAcpAgentPreset(
    val id: String,
    val name: String,
    val command: String,
    val args: String,
    val env: String = "",
    val description: String,
) {
    fun toConfig(): cc.unitmesh.config.AcpAgentConfig {
        return cc.unitmesh.config.AcpAgentConfig(
            name = name,
            command = command,
            args = args,
            env = env
        )
    }

    companion object {
        /**
         * Known presets for common ACP agent CLIs.
         */
        private val ALL_PRESETS = listOf(
            IdeaAcpAgentPreset(
                id = "codex",
                name = "Codex CLI",
                command = "codex",
                args = "--acp",
                description = "OpenAI Codex agent via ACP"
            ),
            IdeaAcpAgentPreset(
                id = "kimi",
                name = "Kimi CLI",
                command = "kimi",
                args = "acp",
                description = "Moonshot Kimi agent via ACP"
            ),
            IdeaAcpAgentPreset(
                id = "gemini",
                name = "Gemini CLI",
                command = "gemini",
                args = "--acp",
                description = "Google Gemini agent via ACP"
            ),
            IdeaAcpAgentPreset(
                id = "claude",
                name = "Claude Code",
                command = "claude",
                args = "--acp",
                description = "Anthropic Claude Code agent via ACP"
            ),
            IdeaAcpAgentPreset(
                id = "copilot",
                name = "GitHub Copilot",
                command = "github-copilot",
                args = "--acp",
                description = "GitHub Copilot agent via ACP"
            ),
        )

        /**
         * Detect installed presets by checking if the command is in PATH.
         */
        fun detectInstalled(): List<IdeaAcpAgentPreset> {
            return ALL_PRESETS.filter { preset ->
                isCommandAvailable(preset.command)
            }
        }

        private fun isCommandAvailable(command: String): Boolean {
            return try {
                val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
                val checkCmd = if (isWindows) listOf("where", command) else listOf("which", command)
                val process = ProcessBuilder(checkCmd)
                    .redirectErrorStream(true)
                    .start()
                val result = process.waitFor()
                result == 0
            } catch (e: Exception) {
                false
            }
        }
    }
}
