package cc.unitmesh.agent.acp

import cc.unitmesh.agent.plan.MarkdownPlanParser
import cc.unitmesh.agent.render.CodingAgentRenderer
import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.client.ClientOperationsFactory
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.*
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

private val logger = KotlinLogging.logger("AcpClient")

/**
 * JVM ACP Client that connects to external ACP agents (e.g., Claude CLI, Gemini CLI).
 *
 * Note: ACP Kotlin SDK currently does not provide Kotlin/Native variants, so this client is JVM-only.
 */
class AcpClient(
    private val coroutineScope: CoroutineScope,
    private val input: RawSource,
    private val output: RawSink,
    private val clientName: String = "autodev-xiuper",
    private val clientVersion: String = "dev",
    private val cwd: String = "",
) {
    private var protocol: Protocol? = null
    private var client: Client? = null
    private var session: ClientSession? = null

    val isConnected: Boolean get() = session != null

    /**
     * Callback for session updates received outside the prompt flow (background notifications).
     */
    var onSessionUpdate: ((SessionUpdate) -> Unit)? = null

    /**
     * Callback for permission requests from the agent.
     * Return the response to grant/deny.
     */
    var onPermissionRequest: ((SessionUpdate.ToolCallUpdate, List<PermissionOption>) -> RequestPermissionResponse)? =
        null

    /**
     * Connect to the ACP agent: set up transport, initialize protocol, create session.
     */
    suspend fun connect() {
        val transport = StdioTransport(
            parentScope = coroutineScope,
            ioDispatcher = Dispatchers.Default,
            input = input.buffered(),
            output = output.buffered(),
            name = clientName
        )
        val proto = Protocol(coroutineScope, transport)
        proto.start()

        val acpClient = Client(proto)
        this.protocol = proto
        this.client = acpClient

        val clientInfo = ClientInfo(
            protocolVersion = 1,
            capabilities = ClientCapabilities(
                fs = null,
                terminal = false,
                _meta = JsonNull
            ),
            implementation = Implementation(
                name = clientName,
                version = clientVersion,
                title = "AutoDev Xiuper (ACP Client)",
                _meta = JsonNull
            ),
            _meta = JsonNull
        )

        acpClient.initialize(clientInfo, JsonNull)

        val operationsFactory = object : ClientOperationsFactory {
            override suspend fun createClientOperations(
                sessionId: SessionId,
                sessionResponse: AcpCreatedSessionResponse,
            ): ClientSessionOperations {
                return AcpClientSessionOps(
                    onSessionUpdate = { update -> onSessionUpdate?.invoke(update) },
                    onPermissionRequest = { toolCall, options ->
                        onPermissionRequest?.invoke(toolCall, options)
                            ?: RequestPermissionResponse(RequestPermissionOutcome.Cancelled, JsonNull)
                    }
                )
            }
        }

        val acpSession = acpClient.newSession(
            SessionCreationParameters(
                cwd = cwd,
                mcpServers = emptyList(),
                _meta = JsonNull
            ),
            operationsFactory
        )
        this.session = acpSession

        logger.info { "ACP client connected successfully (session=${acpSession.sessionId})" }
    }

    /**
     * Send a prompt to the agent and collect streaming events.
     * Returns a Flow of ACP events (session updates and prompt responses).
     */
    fun prompt(text: String): Flow<Event> = flow {
        val sess = session ?: throw IllegalStateException("ACP client not connected")

        val contentBlocks = listOf(ContentBlock.Text(text, Annotations(), JsonNull))
        val eventFlow = sess.prompt(contentBlocks, JsonNull)

        eventFlow.collect { event ->
            emit(event)
        }
    }

    /**
     * Send a prompt and render updates directly to a CodingAgentRenderer.
     * This is a convenience method that bridges ACP events to the renderer system.
     */
    suspend fun promptAndRender(text: String, renderer: CodingAgentRenderer) {
        var receivedAnyChunk = false
        var inThought = false

        prompt(text).collect { event ->
            when (event) {
                is Event.SessionUpdateEvent -> {
                    renderSessionUpdate(
                        event.update,
                        renderer,
                        { receivedAnyChunk },
                        { receivedAnyChunk = it },
                        { inThought },
                        { inThought = it }
                    )
                }

                is Event.PromptResponseEvent -> {
                    if (inThought) {
                        renderer.renderThinkingChunk("", isStart = false, isEnd = true)
                        inThought = false
                    }

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
    }

    /**
     * Cancel the current prompt turn.
     */
    suspend fun cancel() {
        try {
            session?.cancel()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to cancel ACP session" }
        }
    }

    /**
     * Disconnect from the agent and clean up resources.
     */
    suspend fun disconnect() {
        try {
            protocol?.close()
        } catch (_: Exception) {
        }
        protocol = null
        client = null
        session = null
        logger.info { "ACP client disconnected" }
    }

    companion object {
        /**
         * Render an ACP SessionUpdate to a CodingAgentRenderer.
         * This is a shared utility that can be used by JVM UI integrations.
         */
        fun renderSessionUpdate(
            update: SessionUpdate,
            renderer: CodingAgentRenderer,
            getReceivedChunk: () -> Boolean,
            setReceivedChunk: (Boolean) -> Unit,
            getInThought: () -> Boolean,
            setInThought: (Boolean) -> Unit,
        ) {
            when (update) {
                is SessionUpdate.AgentMessageChunk -> {
                    if (!getReceivedChunk()) {
                        renderer.renderLLMResponseStart()
                        setReceivedChunk(true)
                    }
                    val text = extractText(update.content)
                    renderer.renderLLMResponseChunk(text)
                }

                is SessionUpdate.AgentThoughtChunk -> {
                    val thought = extractText(update.content)
                    val isStart = !getInThought()
                    setInThought(true)
                    renderer.renderThinkingChunk(thought, isStart = isStart, isEnd = false)
                }

                is SessionUpdate.PlanUpdate -> {
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
                            MarkdownPlanParser.parseToPlan(markdown)
                            renderer.renderInfo("Plan updated: $markdown")
                        } catch (e: Exception) {
                            logger.warn(e) { "Failed to parse ACP plan update" }
                        }
                    }
                }

                is SessionUpdate.ToolCall -> {
                    val toolTitle = update.title.takeIf { it.isNotBlank() } ?: "tool"
                    val inputText = update.rawInput?.toString() ?: ""
                    renderer.renderToolCallWithParams(
                        toolName = toolTitle,
                        params = mapOf(
                            "kind" to (update.kind?.name ?: "UNKNOWN"),
                            "status" to (update.status?.name ?: "UNKNOWN"),
                            "input" to inputText
                        )
                    )

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
                    renderer.renderInfo("Mode switched to: ${update.currentModeId}")
                }

                else -> {
                    logger.debug { "Unhandled ACP session update: $update" }
                }
            }
        }

        /**
         * Extract text content from an ACP ContentBlock.
         */
        fun extractText(block: ContentBlock): String {
            return when (block) {
                is ContentBlock.Text -> block.text
                else -> block.toString()
            }
        }
    }
}

/**
 * Internal client session operations used by ACP runtime.
 */
internal class AcpClientSessionOps(
    private val onSessionUpdate: (SessionUpdate) -> Unit,
    private val onPermissionRequest: (SessionUpdate.ToolCallUpdate, List<PermissionOption>) -> RequestPermissionResponse,
) : ClientSessionOperations {
    override suspend fun notify(notification: SessionUpdate, _meta: JsonElement?) {
        onSessionUpdate(notification)
    }

    override suspend fun requestPermissions(
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
        _meta: JsonElement?,
    ): RequestPermissionResponse {
        return onPermissionRequest(toolCall, permissions)
    }

    override suspend fun fsReadTextFile(
        path: String,
        line: UInt?,
        limit: UInt?,
        _meta: JsonElement?,
    ): ReadTextFileResponse {
        throw UnsupportedOperationException("ACP fs.read_text_file is not supported in this client")
    }

    override suspend fun fsWriteTextFile(
        path: String,
        content: String,
        _meta: JsonElement?,
    ): WriteTextFileResponse {
        throw UnsupportedOperationException("ACP fs.write_text_file is not supported in this client")
    }

    override suspend fun terminalCreate(
        command: String,
        args: List<String>,
        cwd: String?,
        env: List<EnvVariable>,
        outputByteLimit: ULong?,
        _meta: JsonElement?,
    ): CreateTerminalResponse {
        throw UnsupportedOperationException("ACP terminal.create is not supported in this client")
    }

    override suspend fun terminalOutput(
        terminalId: String,
        _meta: JsonElement?,
    ): TerminalOutputResponse {
        throw UnsupportedOperationException("ACP terminal.output is not supported in this client")
    }

    override suspend fun terminalRelease(
        terminalId: String,
        _meta: JsonElement?,
    ): ReleaseTerminalResponse {
        throw UnsupportedOperationException("ACP terminal.release is not supported in this client")
    }

    override suspend fun terminalWaitForExit(
        terminalId: String,
        _meta: JsonElement?,
    ): WaitForTerminalExitResponse {
        throw UnsupportedOperationException("ACP terminal.wait_for_exit is not supported in this client")
    }

    override suspend fun terminalKill(
        terminalId: String,
        _meta: JsonElement?,
    ): KillTerminalCommandResponse {
        throw UnsupportedOperationException("ACP terminal.kill is not supported in this client")
    }
}

