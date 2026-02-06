package cc.unitmesh.agent.acp

import cc.unitmesh.agent.plan.MarkdownPlanParser
import cc.unitmesh.agent.render.CodingAgentRenderer
import cc.unitmesh.config.ConfigManager
import com.agentclientprotocol.annotations.UnstableApi
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
import kotlinx.serialization.json.JsonNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
    private val agentName: String = "acp-agent",
    private val enableLogging: Boolean = true,
) {
    private var protocol: Protocol? = null
    private var client: Client? = null
    private var session: ClientSession? = null
    private var logFile: File? = null
    private var logWriter: java.io.BufferedWriter? = null

    val isConnected: Boolean get() = session != null

    /**
     * Per-instance tool call dedup tracking.
     * ACP streams many IN_PROGRESS updates per tool call (title grows char-by-char).
     * We only render the first and terminal events to the renderer.
     */
    private val renderedToolCallIds = mutableSetOf<String>()
    private val toolCallTitles = mutableMapOf<String, String>()
    private val startedToolCallIds = mutableSetOf<String>()

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
        // Initialize log file if logging is enabled
        if (enableLogging) {
            try {
                val logsDir = ConfigManager.getAcpLogsDir()
                val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
                val sanitizedAgentName = agentName.replace(Regex("[^a-zA-Z0-9-]"), "_")
                logFile = File(logsDir, "${sanitizedAgentName}_${timestamp}.jsonl")
                logWriter = logFile!!.bufferedWriter()
                logger.info { "📝 ACP logging enabled: ${logFile!!.absolutePath}" }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to initialize ACP log file" }
            }
        }
        
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

        // Enable fs capabilities so the agent can read/write files on our side
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
                name = clientName,
                version = clientVersion,
                title = "AutoDev Xiuper (ACP Client)",
                _meta = JsonNull
            ),
            _meta = JsonNull
        )

        acpClient.initialize(clientInfo, JsonNull)

        // Load MCP servers from config.yaml and pass to ACP session creation.
        // Some agents (e.g., Kimi / Gemini) rely on MCP servers to provide tools like glob/search.
        val mcpServers: List<McpServer> = try {
            val wrapper = ConfigManager.load()
            val enabled = wrapper.getEnabledMcpServers()
            enabled.mapNotNull { (serverName, cfg) ->
                // Map our McpServerConfig (cc.unitmesh.agent.mcp.McpServerConfig) into ACP model McpServer.
                when {
                    cfg.command != null -> {
                        McpServer.Stdio(
                            name = serverName,
                            command = cfg.command,
                            args = cfg.args,
                            env = (cfg.env ?: emptyMap()).entries.map { (k, v) ->
                                EnvVariable(name = k, value = v, _meta = JsonNull)
                            }
                        )
                    }

                    cfg.url != null -> {
                        val headers = (cfg.headers ?: emptyMap()).entries.map { (k, v) ->
                            HttpHeader(name = k, value = v, _meta = JsonNull)
                        }

                        // Heuristic: treat URLs containing "/sse" as SSE transport.
                        val url = cfg.url
                        if (url.contains("/sse", ignoreCase = true)) {
                            McpServer.Sse(name = serverName, url = url, headers = headers)
                        } else {
                            McpServer.Http(name = serverName, url = url, headers = headers)
                        }
                    }

                    else -> null
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load MCP servers from config; creating ACP session without MCP servers" }
            emptyList()
        }

        val operationsFactory = object : ClientOperationsFactory {
            override suspend fun createClientOperations(
                sessionId: SessionId,
                sessionResponse: AcpCreatedSessionResponse,
            ): ClientSessionOperations {
                return AcpClientSessionOps(
                    onSessionUpdate = { update -> onSessionUpdate?.invoke(update) },
                    onPermissionRequest = { toolCall, options ->
                        onPermissionRequest?.invoke(toolCall, options)
                            ?: defaultPermissionResponse(toolCall, options)
                    },
                    cwd = cwd,
                    enableFs = true,
                    enableTerminal = true,
                )
            }
        }

        val acpSession = acpClient.newSession(
            SessionCreationParameters(
                cwd = cwd,
                mcpServers = mcpServers,
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
        var sawAnyToolCall = false
        var sawAnyThought = false

        // Clear tool call dedup state for the new prompt
        renderedToolCallIds.clear()
        toolCallTitles.clear()
        startedToolCallIds.clear()
        
        // Log prompt metadata
        logEvent(mapOf(
            "type" to "prompt_start",
            "timestamp" to System.currentTimeMillis(),
            "prompt" to text
        ))

        prompt(text).collect { event ->
            // Log raw ACP event
            logEvent(serializeEvent(event))
            
            when (event) {
                is Event.SessionUpdateEvent -> {
                    when (event.update) {
                        is SessionUpdate.ToolCall,
                        is SessionUpdate.ToolCallUpdate -> sawAnyToolCall = true
                        is SessionUpdate.AgentThoughtChunk -> sawAnyThought = true
                        else -> {}
                    }
                    renderSessionUpdate(
                        event.update,
                        renderer,
                        { receivedAnyChunk },
                        { receivedAnyChunk = it },
                        { inThought },
                        { inThought = it },
                        renderedToolCallIds,
                        toolCallTitles,
                        startedToolCallIds
                    )
                }

                is Event.PromptResponseEvent -> {
                    // Close thinking section if still open
                    if (inThought) {
                        renderer.renderThinkingChunk("", isStart = false, isEnd = true)
                        inThought = false
                    }
                    
                    // Close LLM response section if any chunks were received
                    if (receivedAnyChunk) {
                        renderer.renderLLMResponseEnd()
                    }

                    val success = event.response.stopReason != StopReason.REFUSAL &&
                        event.response.stopReason != StopReason.CANCELLED

                    // If the agent ended the turn without producing any message chunks, surface it.
                    // This avoids the confusing "finished" state with no visible output.
                    if (!receivedAnyChunk) {
                        val logPath = logFile?.absolutePath
                        val hint = if (!logPath.isNullOrBlank()) {
                            " (see ACP log: $logPath)"
                        } else {
                            ""
                        }
                        val details = buildString {
                            append("ACP ended without any message output; stopReason=${event.response.stopReason}")
                            if (sawAnyToolCall) append(", toolCalls=true")
                            if (sawAnyThought) append(", thoughts=true")
                        }
                        renderer.renderError(details + hint)
                    }
                    renderer.renderFinalResult(
                        success = success,
                        message = "ACP finished: ${event.response.stopReason}",
                        iterations = 0
                    )
                }
            }
        }
        
        // Log prompt end
        logEvent(mapOf(
            "type" to "prompt_end",
            "timestamp" to System.currentTimeMillis()
        ))
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
            // Close log file
            logWriter?.flush()
            logWriter?.close()
            logWriter = null
            if (logFile != null) {
                logger.info { "📝 ACP log saved: ${logFile!!.absolutePath}" }
            }
            
            protocol?.close()
        } catch (_: Exception) {
        }
        protocol = null
        client = null
        session = null
        logger.info { "ACP client disconnected" }
    }
    
    /**
     * Log an event to the JSONL file.
     */
    private fun logEvent(eventData: Map<String, Any?>) {
        if (!enableLogging || logWriter == null) return
        
        try {
            val json = buildString {
                append("{")
                eventData.entries.forEachIndexed { index, (key, value) ->
                    if (index > 0) append(",")
                    append("\"$key\":")
                    when (value) {
                        null -> append("null")
                        is String -> append("\"${value.replace("\"", "\\\"")}\"")
                        is Number -> append(value)
                        is Boolean -> append(value)
                        else -> append("\"${value.toString().replace("\"", "\\\"")}\"")
                    }
                }
                append("}")
            }
            logWriter?.appendLine(json)
            logWriter?.flush()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to log ACP event" }
        }
    }
    
    /**
     * Serialize an ACP Event to a map for logging.
     */
    private fun serializeEvent(event: Event): Map<String, Any?> {
        return when (event) {
            is Event.SessionUpdateEvent -> {
                mapOf(
                    "event_type" to "SessionUpdate",
                    "timestamp" to System.currentTimeMillis(),
                    "update_type" to event.update::class.simpleName,
                    "update" to serializeSessionUpdate(event.update)
                )
            }
            is Event.PromptResponseEvent -> {
                mapOf(
                    "event_type" to "PromptResponse",
                    "timestamp" to System.currentTimeMillis(),
                    "stop_reason" to event.response.stopReason.name,
                    // Some agents may include additional fields on the response.
                    // Logging the full response helps diagnose cases where no SessionUpdate chunks arrive.
                    "response" to event.response.toString()
                )
            }
        }
    }
    
    /**
     * Serialize SessionUpdate to a map for logging.
     */
    @OptIn(UnstableApi::class)
    private fun serializeSessionUpdate(update: SessionUpdate): Map<String, Any?> {
        return when (update) {
            is SessionUpdate.AgentMessageChunk -> {
                mapOf(
                    "type" to "AgentMessageChunk",
                    "content" to serializeContentBlock(update.content)
                )
            }
            is SessionUpdate.AgentThoughtChunk -> {
                mapOf(
                    "type" to "AgentThoughtChunk",
                    "content" to serializeContentBlock(update.content)
                )
            }
            is SessionUpdate.ToolCall -> {
                mapOf(
                    "type" to "ToolCall",
                    "toolCallId" to update.toolCallId?.value,
                    "title" to update.title,
                    "kind" to update.kind?.name,
                    "status" to update.status?.name,
                    "rawInput" to update.rawInput?.toString(),
                    "rawOutput" to update.rawOutput?.toString()
                )
            }
            is SessionUpdate.ToolCallUpdate -> {
                mapOf(
                    "type" to "ToolCallUpdate",
                    "toolCallId" to update.toolCallId?.value,
                    "title" to update.title,
                    "kind" to update.kind?.name,
                    "status" to update.status?.name,
                    "rawInput" to update.rawInput?.toString(),
                    "rawOutput" to update.rawOutput?.toString()
                )
            }
            is SessionUpdate.PlanUpdate -> {
                mapOf(
                    "type" to "PlanUpdate",
                    "entries" to update.entries.map { 
                        mapOf(
                            "content" to it.content,
                            "status" to it.status.name
                        )
                    }
                )
            }
            is SessionUpdate.CurrentModeUpdate -> {
                mapOf(
                    "type" to "CurrentModeUpdate",
                    "modeId" to update.currentModeId.toString()
                )
            }
            is SessionUpdate.ConfigOptionUpdate -> {
                mapOf(
                    "type" to "ConfigOptionUpdate",
                    "optionCount" to update.configOptions.size
                )
            }
            is SessionUpdate.AvailableCommandsUpdate -> {
                mapOf(
                    "type" to "AvailableCommandsUpdate",
                    "commandCount" to update.availableCommands.size
                )
            }
            is SessionUpdate.UserMessageChunk -> {
                mapOf(
                    "type" to "UserMessageChunk",
                    "content" to serializeContentBlock(update.content)
                )
            }
            else -> {
                mapOf("type" to update::class.simpleName)
            }
        }
    }
    
    /**
     * Serialize ContentBlock to a map for logging.
     * Handles all ACP ContentBlock types: Text, Image, Audio, Resource, ResourceLink.
     */
    private fun serializeContentBlock(block: ContentBlock): Map<String, Any?> {
        return when (block) {
            is ContentBlock.Text -> {
                mapOf(
                    "blockType" to "text",
                    "text" to block.text,
                    "annotations" to block.annotations?.let { serializeAnnotations(it) }
                )
            }
            is ContentBlock.Image -> {
                mapOf(
                    "blockType" to "image",
                    "mimeType" to block.mimeType,
                    "dataLength" to (block.data?.length ?: 0),
                    "uri" to block.uri,
                    "annotations" to block.annotations?.let { serializeAnnotations(it) }
                )
            }
            is ContentBlock.Audio -> {
                mapOf(
                    "blockType" to "audio",
                    "mimeType" to block.mimeType,
                    "dataLength" to (block.data?.length ?: 0),
                    "annotations" to block.annotations?.let { serializeAnnotations(it) }
                )
            }
            is ContentBlock.Resource -> {
                val resource = block.resource
                try {
                    mapOf(
                        "blockType" to "resource",
                        "resource" to resource.toString()
                    )
                } catch (e: Exception) {
                    mapOf(
                        "blockType" to "resource",
                        "error" to e.message
                    )
                }
            }
            is ContentBlock.ResourceLink -> {
                mapOf(
                    "blockType" to "resource_link",
                    "uri" to block.uri,
                    "name" to block.name,
                    "mimeType" to block.mimeType,
                    "title" to block.title,
                    "description" to block.description,
                    "size" to block.size,
                    "annotations" to block.annotations?.let { serializeAnnotations(it) }
                )
            }
            else -> {
                mapOf(
                    "blockType" to "unknown",
                    "class" to block::class.simpleName,
                    "toString" to block.toString()
                )
            }
        }
    }
    
    /**
     * Serialize Annotations to a map.
     */
    private fun serializeAnnotations(annotations: Annotations): Map<String, Any?>? {
        // Annotations are opaque, so we just indicate if they exist
        return try {
            val priority = annotations.priority
            val audience = annotations.audience?.joinToString(",")
            if (priority != null || audience != null) {
                mapOf(
                    "priority" to priority,
                    "audience" to audience
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /**
         * Default permission strategy when the embedding UI does not provide a handler.
         *
         * We prefer ALLOW_ONCE (or ALLOW_ALWAYS) to avoid agents getting stuck in a
         * "tool call failed -> END_TURN with no output" state (common for shell-like actions).
         *
         * IDE integrations (Compose UI / IDEA) SHOULD override [onPermissionRequest] to prompt the user.
         */
        private fun defaultPermissionResponse(
            toolCall: SessionUpdate.ToolCallUpdate,
            options: List<PermissionOption>,
        ): RequestPermissionResponse {
            val allow = options.firstOrNull {
                it.kind == PermissionOptionKind.ALLOW_ONCE || it.kind == PermissionOptionKind.ALLOW_ALWAYS
            }
            return if (allow != null) {
                logger.info {
                    "ACP permission auto-approved (${allow.kind}) for tool=${toolCall.title ?: "tool"} option=${allow.name}"
                }
                RequestPermissionResponse(RequestPermissionOutcome.Selected(allow.optionId), JsonNull)
            } else {
                logger.info {
                    "ACP permission cancelled (no allow option) for tool=${toolCall.title ?: "tool"}"
                }
                RequestPermissionResponse(RequestPermissionOutcome.Cancelled, JsonNull)
            }
        }

        /**
         * Render an ACP SessionUpdate to a CodingAgentRenderer.
         * This is a shared utility that can be used by JVM UI integrations.
         *
         * [renderedToolCallIds] and [toolCallTitles] are per-prompt state for
         * deduplicating streaming tool call updates. ACP sends many IN_PROGRESS
         * updates per tool call (title streams char-by-char). We only render:
         *   1. The *first* event for a new toolCallId
         *   2. The *terminal* event (COMPLETED/FAILED) with the fully-built title
         */
        @OptIn(UnstableApi::class)
        fun renderSessionUpdate(
            update: SessionUpdate,
            renderer: CodingAgentRenderer,
            getReceivedChunk: () -> Boolean,
            setReceivedChunk: (Boolean) -> Unit,
            getInThought: () -> Boolean,
            setInThought: (Boolean) -> Unit,
            renderedToolCallIds: MutableSet<String> = mutableSetOf(),
            toolCallTitles: MutableMap<String, String> = mutableMapOf(),
            startedToolCallIds: MutableSet<String> = mutableSetOf(),
        ) {
            when (update) {
                is SessionUpdate.AgentMessageChunk -> {
                    // Close thinking section if transitioning from thought to message
                    if (getInThought()) {
                        renderer.renderThinkingChunk("", isStart = false, isEnd = true)
                        setInThought(false)
                    }
                    
                    if (!getReceivedChunk()) {
                        renderer.renderLLMResponseStart()
                        setReceivedChunk(true)
                    }
                    
                    // Handle resource content blocks (e.g., markdown files from Gemini)
                    val block = update.content
                    if (block is ContentBlock.Resource) {
                        handleResourceContent(block, renderer)
                    } else {
                        val text = extractText(block)
                        renderer.renderLLMResponseChunk(text)
                    }
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
                    handleToolCallEvent(
                        toolCallId = update.toolCallId?.value,
                        title = update.title,
                        kind = update.kind?.name,
                        status = update.status,
                        rawInput = update.rawInput?.toString(),
                        rawOutput = update.rawOutput?.toString(),
                        renderer = renderer,
                        renderedToolCallIds = renderedToolCallIds,
                        toolCallTitles = toolCallTitles,
                        startedToolCallIds = startedToolCallIds
                    )
                }

                is SessionUpdate.ToolCallUpdate -> {
                    handleToolCallEvent(
                        toolCallId = update.toolCallId?.value,
                        title = update.title,
                        kind = update.kind?.name,
                        status = update.status,
                        rawInput = update.rawInput?.toString(),
                        rawOutput = update.rawOutput?.toString(),
                        renderer = renderer,
                        renderedToolCallIds = renderedToolCallIds,
                        toolCallTitles = toolCallTitles,
                        startedToolCallIds = startedToolCallIds
                    )
                }

                is SessionUpdate.CurrentModeUpdate -> {
                    renderer.renderInfo("Mode switched to: ${update.currentModeId}")
                }

                is SessionUpdate.ConfigOptionUpdate -> {
                    val options = update.configOptions
                    if (options.isNotEmpty()) {
                        val summary = options.joinToString(", ") { opt ->
                            opt.name
                        }
                        renderer.renderInfo("Config updated: $summary")
                    }
                }

                is SessionUpdate.AvailableCommandsUpdate -> {
                    val commands = update.availableCommands
                    if (commands.isNotEmpty()) {
                        logger.debug { "Available commands updated: ${commands.joinToString { it.name }}" }
                    }
                }

                is SessionUpdate.UserMessageChunk -> {
                    // Agent echoing back user message - typically ignored by clients
                    logger.debug { "User message echo: ${extractText(update.content).take(100)}" }
                }

                else -> {
                    logger.debug { "Unhandled ACP session update: $update" }
                }
            }
        }

        /**
         * Unified handler for ToolCall and ToolCallUpdate events.
         *
         * Based on raw ACP event analysis (see docs/test-scripts/acp-raw-events/):
         *
         * Each tool operation has one unique `toolCallId` and follows this lifecycle:
         *   ToolCall(status=IN_PROGRESS, title="ReadFile")     // initial, title incomplete
         *   ToolCallUpdate(IN_PROGRESS, title="ReadFile: build.gradle")  // title growing
         *   ToolCallUpdate(IN_PROGRESS, title="ReadFile: build.gradle.kts")  // title stable
         *   ...many identical heartbeat ToolCallUpdates...      // 60/sec for WriteFile
         *   ToolCallUpdate(COMPLETED/FAILED, title=None)        // terminal, title absent
         *
         * Rendering strategy:
         * - IN_PROGRESS: render ONCE (first time) so UI shows a running tool bubble
         * - IN_PROGRESS updates: accumulate best title, do not render (avoid spam)
         * - COMPLETED/FAILED: render ONLY the tool result (it upgrades the last running bubble)
         *
         * This preserves responsiveness for long-running operations (WriteFile, Shell, etc.)
         * without flooding the timeline.
         */
        private fun handleToolCallEvent(
            toolCallId: String?,
            title: String?,
            kind: String?,
            status: ToolCallStatus?,
            rawInput: String?,
            rawOutput: String?,
            renderer: CodingAgentRenderer,
            renderedToolCallIds: MutableSet<String>,
            toolCallTitles: MutableMap<String, String>,
            startedToolCallIds: MutableSet<String>,
        ) {
            val inputText = rawInput ?: ""
            val isTerminal = status == ToolCallStatus.COMPLETED || status == ToolCallStatus.FAILED
            val isRunning = status == ToolCallStatus.IN_PROGRESS || status == ToolCallStatus.PENDING
            val id = toolCallId ?: ""

            // Always update the best-known title for this id (title streams char-by-char)
            val currentTitle = title?.takeIf { it.isNotBlank() }
            if (id.isNotBlank() && currentTitle != null) {
                toolCallTitles[id] = currentTitle
            }

            // IN_PROGRESS / PENDING: render once so users see "still running"
            if (isRunning) {
                if (id.isNotBlank() && !startedToolCallIds.contains(id)) {
                    val toolTitle = toolCallTitles[id] ?: currentTitle ?: "tool"
                    renderer.renderToolCallWithParams(
                        toolName = toolTitle,
                        params = mapOf(
                            "kind" to (kind ?: "UNKNOWN"),
                            "status" to (status?.name ?: "IN_PROGRESS"),
                            "input" to inputText
                        )
                    )
                    startedToolCallIds.add(id)
                }
                return
            }

            if (!isTerminal) return

            // Terminal state (COMPLETED / FAILED): render once with best title
            // Note: terminal events have title=None, so we must use stored title
            val toolTitle = (if (id.isNotBlank()) toolCallTitles[id] else null)
                ?: currentTitle
                ?: "tool"

            val out = rawOutput
            if (out != null && out.isNotBlank()) {
                renderer.renderToolResult(
                    toolName = toolTitle,
                    success = status == ToolCallStatus.COMPLETED,
                    output = out,
                    fullOutput = out,
                    metadata = emptyMap()
                )
            } else {
                // Even without output, mark as result so UI shows completion state
                renderer.renderToolResult(
                    toolName = toolTitle,
                    success = status == ToolCallStatus.COMPLETED,
                    output = if (status == ToolCallStatus.COMPLETED) "Done" else "Failed",
                    fullOutput = null,
                    metadata = emptyMap()
                )
            }

            // Clean up tracking
            if (id.isNotBlank()) {
                renderedToolCallIds.add(id)
                toolCallTitles.remove(id)
                startedToolCallIds.remove(id)
            }
        }

        /**
         * Handle resource content blocks (e.g., markdown architecture diagrams from Gemini).
         * Currently simplified to just toString() the resource until we understand its structure better.
         */
        private fun handleResourceContent(block: ContentBlock.Resource, renderer: CodingAgentRenderer) {
            // For now, just render the text representation
            val text = extractText(block)
            renderer.renderLLMResponseChunk(text)
            
            // Log that we received a resource for debugging
            logger.info { "Received ContentBlock.Resource: ${block.resource}" }
        }

        /**
         * Extract text content from an ACP ContentBlock.
         * Handles various content types.
         */
        fun extractText(block: ContentBlock): String {
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
    }
}

