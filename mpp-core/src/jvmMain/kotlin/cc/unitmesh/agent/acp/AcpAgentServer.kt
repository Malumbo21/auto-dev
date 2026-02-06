package cc.unitmesh.agent.acp

import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.agent.AgentSession
import com.agentclientprotocol.agent.AgentSupport
import com.agentclientprotocol.client.ClientInfo
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

private val logger = KotlinLogging.logger("AcpAgentServer")

/**
 * Callback interface for handling ACP agent prompts.
 *
 * Implementations should process the user's prompt and emit session update events.
 * This bridges the ACP protocol to the internal CodingAgent execution system.
 */
interface AcpPromptHandler {
    /**
     * Handle an incoming prompt from the ACP client.
     *
     * @param sessionId The ACP session ID
     * @param content The content blocks from the prompt
     * @param updateEmitter Use this to emit session update events back to the client
     * @return The stop reason for this prompt turn
     */
    suspend fun handlePrompt(
        sessionId: String,
        content: List<ContentBlock>,
        updateEmitter: AcpUpdateEmitter,
    ): StopReason

    /**
     * Cancel any running task for the given session.
     */
    suspend fun cancel(sessionId: String)
}

/**
 * Emitter for sending ACP session updates back to the client.
 */
interface AcpUpdateEmitter {
    /**
     * Emit a text message chunk to the client.
     */
    suspend fun emitTextChunk(text: String)

    /**
     * Emit a thinking/thought chunk.
     */
    suspend fun emitThoughtChunk(text: String)

    /**
     * Emit a tool call update.
     */
    suspend fun emitToolCall(
        toolCallId: String,
        title: String,
        status: ToolCallStatus,
        kind: ToolKind? = null,
        input: String? = null,
        output: String? = null,
    )

    /**
     * Emit a plan update.
     */
    suspend fun emitPlanUpdate(entries: List<PlanEntry>)
}

/**
 * ACP Agent Server that exposes our CodingAgent via the Agent Client Protocol.
 *
 * Other editors (VSCode, Zed, etc.) can connect to this server and use our agent
 * through the standardized ACP protocol. Communication happens over STDIO (JSON-RPC).
 *
 * Note: ACP Kotlin SDK currently does not provide Kotlin/Native variants, so this server is JVM-only.
 */
class AcpAgentServer(
    private val coroutineScope: CoroutineScope,
    private val input: RawSource,
    private val output: RawSink,
    private val agentName: String = "autodev-xiuper",
    private val agentVersion: String = "dev",
) {
    private var protocol: Protocol? = null
    private var agent: Agent? = null

    /**
     * The handler that processes incoming prompts.
     * Must be set before calling [start].
     */
    var promptHandler: AcpPromptHandler? = null

    /**
     * Start the ACP agent server. This will begin listening for messages.
     */
    suspend fun start() {
        val handler = promptHandler ?: throw IllegalStateException("promptHandler must be set before starting")

        val transport = StdioTransport(
            parentScope = coroutineScope,
            ioDispatcher = Dispatchers.Default,
            input = input.buffered(),
            output = output.buffered(),
            name = agentName
        )
        val proto = Protocol(coroutineScope, transport)

        val agentSupport = AutoDevAgentSupport(
            agentName = agentName,
            agentVersion = agentVersion,
            promptHandler = handler
        )

        val acpAgent = Agent(
            protocol = proto,
            agentSupport = agentSupport
        )

        this.protocol = proto
        this.agent = acpAgent

        logger.info { "ACP agent server starting ($agentName v$agentVersion)..." }
        proto.start()
        logger.info { "ACP agent server started and listening for connections." }
    }

    /**
     * Stop the server and clean up resources.
     */
    suspend fun stop() {
        try {
            protocol?.close()
        } catch (_: Exception) {
        }
        protocol = null
        agent = null
        logger.info { "ACP agent server stopped." }
    }
}

/**
 * Internal AgentSupport implementation for AutoDev.
 */
internal class AutoDevAgentSupport(
    private val agentName: String,
    private val agentVersion: String,
    private val promptHandler: AcpPromptHandler,
) : AgentSupport {

    override suspend fun initialize(clientInfo: ClientInfo): AgentInfo {
        logger.info { "ACP client connected: ${clientInfo.implementation?.name} v${clientInfo.implementation?.version}" }
        return AgentInfo(
            protocolVersion = LATEST_PROTOCOL_VERSION,
            capabilities = AgentCapabilities(loadSession = false, _meta = null),
            implementation = Implementation(
                name = agentName,
                version = agentVersion,
                title = "AutoDev Xiuper (ACP Agent)",
                _meta = null
            ),
        )
    }

    override suspend fun createSession(sessionParameters: SessionCreationParameters): AgentSession {
        val sessionId = SessionId("autodev-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}")
        logger.info { "Creating ACP session: $sessionId (cwd=${sessionParameters.cwd})" }
        return AutoDevAgentSession(sessionId, promptHandler)
    }

    override suspend fun loadSession(
        sessionId: SessionId,
        sessionParameters: SessionCreationParameters,
    ): AgentSession {
        logger.info { "Loading ACP session (not fully supported): $sessionId" }
        return AutoDevAgentSession(sessionId, promptHandler)
    }
}

/**
 * Internal AgentSession implementation for AutoDev.
 * Each session maps to one conversation with the client.
 */
internal class AutoDevAgentSession(
    override val sessionId: SessionId,
    private val promptHandler: AcpPromptHandler,
) : AgentSession {

    override suspend fun prompt(
        content: List<ContentBlock>,
        _meta: JsonElement?,
    ): Flow<Event> = flow {
        val emitter = FlowAcpUpdateEmitter { event -> emit(event) }

        val stopReason = try {
            promptHandler.handlePrompt(sessionId.value, content, emitter)
        } catch (e: Exception) {
            logger.warn(e) { "Error handling ACP prompt" }
            emit(
                Event.SessionUpdateEvent(
                    SessionUpdate.AgentMessageChunk(
                        ContentBlock.Text("Error: ${e.message}", Annotations(), null)
                    )
                )
            )
            StopReason.END_TURN
        }

        emit(Event.PromptResponseEvent(PromptResponse(stopReason)))
    }

    override suspend fun cancel() {
        promptHandler.cancel(sessionId.value)
    }
}

/**
 * AcpUpdateEmitter that emits events into a Flow.
 */
internal class FlowAcpUpdateEmitter(
    private val emit: suspend (Event) -> Unit,
) : AcpUpdateEmitter {

    override suspend fun emitTextChunk(text: String) {
        emit(
            Event.SessionUpdateEvent(
                SessionUpdate.AgentMessageChunk(
                    ContentBlock.Text(text, Annotations(), null)
                )
            )
        )
    }

    override suspend fun emitThoughtChunk(text: String) {
        emit(
            Event.SessionUpdateEvent(
                SessionUpdate.AgentThoughtChunk(
                    ContentBlock.Text(text, Annotations(), null)
                )
            )
        )
    }

    override suspend fun emitToolCall(
        toolCallId: String,
        title: String,
        status: ToolCallStatus,
        kind: ToolKind?,
        input: String?,
        output: String?,
    ) {
        emit(
            Event.SessionUpdateEvent(
                SessionUpdate.ToolCallUpdate(
                    toolCallId = ToolCallId(toolCallId),
                    title = title,
                    status = status,
                    kind = kind,
                    rawInput = null,
                    rawOutput = null,
                    _meta = null
                )
            )
        )
    }

    override suspend fun emitPlanUpdate(entries: List<PlanEntry>) {
        emit(
            Event.SessionUpdateEvent(
                SessionUpdate.PlanUpdate(
                    entries = entries,
                    _meta = null
                )
            )
        )
    }
}

