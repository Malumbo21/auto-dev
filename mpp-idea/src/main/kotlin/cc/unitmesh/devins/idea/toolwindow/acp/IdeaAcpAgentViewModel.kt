package cc.unitmesh.devins.idea.toolwindow.acp

import cc.unitmesh.agent.plan.MarkdownPlanParser
import cc.unitmesh.devins.idea.renderer.JewelRenderer
import cc.unitmesh.devti.settings.AutoDevSettingsState
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

private val acpLogger = Logger.getInstance("AutoDevAcpAgent")

/**
 * ACP (Agent Client Protocol) ViewModel for IntelliJ IDEA plugin.
 *
 * MVP scope:
 * - Spawn ACP agent as a local process (JSON-RPC over stdio)
 * - initialize -> session/new -> session/prompt
 * - Render session/update streaming events into existing timeline UI
 * - Keep client capabilities minimal (fs/terminal disabled)
 */
class IdeaAcpAgentViewModel(
    val project: Project,
    private val coroutineScope: CoroutineScope,
) : Disposable {
    val renderer = JewelRenderer()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _stderrTail = MutableStateFlow<List<String>>(emptyList())
    val stderrTail: StateFlow<List<String>> = _stderrTail.asStateFlow()

    private var process: Process? = null
    private var protocol: Protocol? = null
    private var client: Client? = null
    private var session: ClientSession? = null

    private var stderrJob: Job? = null
    private var connectJob: Job? = null
    private var currentPromptJob: Job? = null

    private val receivedAnyAgentChunk = AtomicBoolean(false)
    private val inThoughtStream = AtomicBoolean(false)

    fun loadConfigFromSettings(): AcpAgentConfig {
        val settings = AutoDevSettingsState.getInstance()
        val cwd = project.basePath ?: System.getProperty("user.home")
        return AcpAgentConfig(
            command = settings.acpCommand.trim(),
            args = settings.acpArgs.trim(),
            envText = settings.acpEnv,
            cwd = cwd
        )
    }

    fun saveConfigToSettings(config: AcpAgentConfig) {
        val settings = AutoDevSettingsState.getInstance()
        settings.acpCommand = config.command
        settings.acpArgs = config.args
        settings.acpEnv = config.envText
    }

    fun connect(config: AcpAgentConfig) {
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

                val args = splitArgs(config.args)
                val env = parseEnvLines(config.envText)
                val cwd = config.cwd.ifBlank { project.basePath ?: System.getProperty("user.home") }

                val pb = ProcessBuilder(listOf(cmd) + args)
                pb.directory(File(cwd))
                // Important: do NOT redirect stdout/stderr, stdout is used by ACP stdio transport.
                pb.redirectInput(ProcessBuilder.Redirect.PIPE)
                pb.redirectOutput(ProcessBuilder.Redirect.PIPE)
                pb.redirectError(ProcessBuilder.Redirect.PIPE)
                pb.environment().putAll(env)

                acpLogger.info("Starting ACP agent process: ${pb.command().joinToString(" ")} (cwd=$cwd)")
                val started = pb.start()
                process = started

                // Tail stderr for debugging and user visibility.
                stderrJob = coroutineScope.launch(Dispatchers.IO) {
                    readStderrTail(started)
                }

                val input = started.inputStream.asSource().buffered()
                val output = started.outputStream.asSink().buffered()

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

                val clientInfo = ClientInfo(
                    protocolVersion = 1,
                    capabilities = ClientCapabilities(
                        fs = null,
                        terminal = false,
                        _meta = JsonNull
                    ),
                    implementation = Implementation(
                        name = "autodev-xiuper",
                        version = "dev",
                        title = "AutoDev Xiuper (ACP Client)",
                        _meta = JsonNull
                    ),
                    _meta = JsonNull
                )

                client.initialize(clientInfo, JsonNull)

                val operationsFactory = object : ClientOperationsFactory {
                    override suspend fun createClientOperations(
                        sessionId: SessionId,
                        sessionResponse: AcpCreatedSessionResponse,
                    ): ClientSessionOperations {
                        return IdeaAcpClientSessionOps(
                            onSessionUpdate = { update ->
                                handleSessionUpdate(update)
                            },
                            onPermissionRequest = { toolCallUpdate, options ->
                                handlePermissionRequest(toolCallUpdate, options)
                            }
                        )
                    }
                }

                val session = client.newSession(
                    SessionCreationParameters(
                        cwd = cwd,
                        mcpServers = emptyList(),
                        _meta = JsonNull
                    ),
                    operationsFactory
                )
                this@IdeaAcpAgentViewModel.session = session

                _isConnected.value = true
                _connectionError.value = null
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

    private fun handlePermissionRequest(
        toolCall: SessionUpdate.ToolCallUpdate,
        options: List<PermissionOption>,
    ): RequestPermissionResponse {
        // MVP: do not grant permissions. This forces tools to stay disabled unless user enables later.
        return RequestPermissionResponse(RequestPermissionOutcome.Cancelled, JsonNull)
    }

    private fun handleSessionUpdate(update: SessionUpdate, source: String = "prompt") {
        when (update) {
            is SessionUpdate.AgentMessageChunk -> {
                if (!receivedAnyAgentChunk.getAndSet(true)) {
                    renderer.renderLLMResponseStart()
                }
                
                // Handle resource content blocks (e.g., markdown files from Gemini)
                val block = update.content
                if (block is ContentBlock.Resource) {
                    handleResourceContent(block)
                } else {
                    val text = extractText(block)
                    renderer.renderLLMResponseChunk(text)
                }
            }

            is SessionUpdate.AgentThoughtChunk -> {
                val thought = extractText(update.content)
                val isStart = !inThoughtStream.getAndSet(true)
                renderer.renderThinkingChunk(thought, isStart = isStart, isEnd = false)
            }

            is SessionUpdate.PlanUpdate -> {
                // Convert ACP plan entries into our markdown plan model.
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
            }

            is SessionUpdate.ToolCall -> {
                // Render tool call as a tool bubble using a safe wrapper param.
                val toolTitle = update.title?.takeIf { it.isNotBlank() } ?: "tool"
                val inputText = update.rawInput?.toString() ?: ""
                renderer.renderToolCallWithParams(
                    toolName = toolTitle,
                    params = mapOf(
                        "kind" to (update.kind?.name ?: "UNKNOWN"),
                        "status" to (update.status?.name ?: "UNKNOWN"),
                        "input" to inputText
                    )
                )

                // If agent already produced output in the same update, render it as a result.
                val out = update.rawOutput?.toString()
                val status = update.status
                if (out != null && out.isNotBlank() && status != null && status != ToolCallStatus.PENDING && status != ToolCallStatus.IN_PROGRESS) {
                    renderer.renderToolResult(
                        toolName = toolTitle,
                        success = status == ToolCallStatus.COMPLETED,
                        output = out,
                        fullOutput = out,
                        metadata = emptyMap()
                    )
                }
            }

            is SessionUpdate.ToolCallUpdate -> {
                // Treat updates similarly to tool calls (may include progressive output).
                val toolTitle = update.title?.takeIf { it.isNotBlank() } ?: "tool"
                val inputText = update.rawInput?.toString() ?: ""
                val out = update.rawOutput?.toString()

                renderer.renderToolCallWithParams(
                    toolName = toolTitle,
                    params = mapOf(
                        "kind" to (update.kind?.name ?: "UNKNOWN"),
                        "status" to (update.status?.name ?: "UNKNOWN"),
                        "input" to inputText
                    )
                )

                val status = update.status
                if (out != null && out.isNotBlank() && status != null && status != ToolCallStatus.PENDING && status != ToolCallStatus.IN_PROGRESS) {
                    renderer.renderToolResult(
                        toolName = toolTitle,
                        success = status == ToolCallStatus.COMPLETED,
                        output = out,
                        fullOutput = out,
                        metadata = emptyMap()
                    )
                }
            }

            is SessionUpdate.CurrentModeUpdate -> {
                // Surface mode changes as a system-like message.
                renderer.renderLLMResponseStart()
                renderer.renderLLMResponseChunk("Mode switched to: ${update.currentModeId}")
                renderer.renderLLMResponseEnd()
            }

            else -> {
                // Ignore other updates for MVP.
                acpLogger.debug("Unhandled ACP session update ($source): $update")
            }
        }
    }

    /**
     * Extract text content from an ACP ContentBlock.
     * Handles various content types.
     */
    private fun extractText(block: ContentBlock): String {
        return when (block) {
            is ContentBlock.Text -> block.text
            is ContentBlock.Resource -> {
                // Resource content - toString for now
                // TODO: Parse resource structure when SDK documentation is available
                val resourceStr = block.resource.toString()
                if (resourceStr.length > 500) {
                    "[Resource: ${resourceStr.take(500)}...]"
                } else {
                    resourceStr
                }
            }
            is ContentBlock.ResourceLink -> {
                "[Resource Link: ${block.name} (${block.uri})]"
            }
            is ContentBlock.Image -> {
                "[Image: mimeType=${block.mimeType}, uri=${block.uri ?: "embedded"}]"
            }
            is ContentBlock.Audio -> {
                "[Audio: mimeType=${block.mimeType}]"
            }
            else -> block.toString()
        }
    }
    
    /**
     * Handle resource content blocks (e.g., markdown architecture diagrams from Gemini).
     * Currently simplified to just toString() the resource until we understand its structure better.
     */
    private fun handleResourceContent(block: ContentBlock.Resource) {
        // For now, just render the text representation
        val text = extractText(block)
        renderer.renderLLMResponseChunk(text)
        
        // Log that we received a resource for debugging
        acpLogger.info("Received ContentBlock.Resource: ${block.resource}")
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

        val p = process
        process = null
        if (p != null) {
            try {
                p.destroy()
            } catch (_: Exception) {
            }
            withContext(Dispatchers.IO) {
                try {
                    p.waitFor()
                } catch (_: Exception) {
                }
            }
        }
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
    }
}

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
 * Minimal client operations used by ACP runtime.
 *
 * - notify(): forward session updates into the UI, in case the agent emits updates outside prompt flow
 * - requestPermissions(): MVP denies all permissions
 * - fs/terminal operations: not supported (capabilities disabled)
 */
private class IdeaAcpClientSessionOps(
    private val onSessionUpdate: (SessionUpdate) -> Unit,
    private val onPermissionRequest: (SessionUpdate.ToolCallUpdate, List<PermissionOption>) -> RequestPermissionResponse,
) : ClientSessionOperations {
    override suspend fun notify(notification: SessionUpdate, _meta: kotlinx.serialization.json.JsonElement?) {
        onSessionUpdate(notification)
    }

    override suspend fun requestPermissions(
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
        _meta: kotlinx.serialization.json.JsonElement?,
    ): RequestPermissionResponse {
        return onPermissionRequest(toolCall, permissions)
    }

    override suspend fun fsReadTextFile(
        path: String,
        line: UInt?,
        limit: UInt?,
        _meta: kotlinx.serialization.json.JsonElement?,
    ): ReadTextFileResponse {
        throw UnsupportedOperationException("ACP fs.read_text_file is disabled in this client")
    }

    override suspend fun fsWriteTextFile(
        path: String,
        content: String,
        _meta: kotlinx.serialization.json.JsonElement?,
    ): WriteTextFileResponse {
        throw UnsupportedOperationException("ACP fs.write_text_file is disabled in this client")
    }

    override suspend fun terminalCreate(
        command: String,
        args: List<String>,
        cwd: String?,
        env: List<EnvVariable>,
        outputByteLimit: ULong?,
        _meta: kotlinx.serialization.json.JsonElement?,
    ): CreateTerminalResponse {
        throw UnsupportedOperationException("ACP terminal.create is disabled in this client")
    }

    override suspend fun terminalOutput(
        terminalId: String,
        _meta: kotlinx.serialization.json.JsonElement?,
    ): TerminalOutputResponse {
        throw UnsupportedOperationException("ACP terminal.output is disabled in this client")
    }

    override suspend fun terminalRelease(
        terminalId: String,
        _meta: kotlinx.serialization.json.JsonElement?,
    ): ReleaseTerminalResponse {
        throw UnsupportedOperationException("ACP terminal.release is disabled in this client")
    }

    override suspend fun terminalWaitForExit(
        terminalId: String,
        _meta: kotlinx.serialization.json.JsonElement?,
    ): WaitForTerminalExitResponse {
        throw UnsupportedOperationException("ACP terminal.wait_for_exit is disabled in this client")
    }

    override suspend fun terminalKill(
        terminalId: String,
        _meta: kotlinx.serialization.json.JsonElement?,
    ): KillTerminalCommandResponse {
        throw UnsupportedOperationException("ACP terminal.kill is disabled in this client")
    }
}

