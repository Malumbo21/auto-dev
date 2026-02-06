package cc.unitmesh.agent.claude

import cc.unitmesh.agent.render.CodingAgentRenderer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

private val logger = KotlinLogging.logger("ClaudeCodeClient")

/**
 * Kotlin client for Claude Code binary.
 *
 * Launches `claude -p --output-format stream-json --input-format stream-json`
 * and communicates via JSON lines over stdio.
 *
 * Reference implementations:
 * - IDEA ml-llm: ClaudeCodeProcessHandler + ClaudeCodeLongRunningSession
 * - zed-industries/claude-code-acp: TypeScript ACP adapter around Claude Agent SDK
 *
 * @see <a href="https://github.com/phodal/auto-dev/issues/538">Issue #538</a>
 */
class ClaudeCodeClient(
    private val scope: CoroutineScope,
    private val binaryPath: String,
    private val workingDirectory: String,
    private val agentName: String = "Claude Code",
    private val enableLogging: Boolean = true,
    private val model: String? = null,
    private val permissionMode: String? = null,
    private val additionalArgs: List<String> = emptyList(),
    private val envVars: Map<String, String> = emptyMap(),
) {
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var readerJob: Job? = null
    private var logWriter: BufferedWriter? = null
    private var sessionId: String? = null

    private val toolUseNames = mutableMapOf<String, String>()
    // Track which tool IDs have been rendered to avoid duplicates
    private val renderedToolIds = mutableSetOf<String>()
    // Track tool_use input data by toolId for rendering
    private val toolUseInputs = mutableMapOf<String, Map<String, Any>>()

    val isConnected: Boolean get() = process?.isAlive == true

    /**
     * Start the Claude Code process with stream-json mode.
     */
    fun start() {
        val cmd = mutableListOf(binaryPath, "-p")
        cmd.addAll(listOf("--output-format", "stream-json"))
        cmd.addAll(listOf("--input-format", "stream-json"))
        cmd.add("--verbose")
        // Required for streaming: without this flag, Claude Code only emits the final `result`
        // message. With it, we get `stream_event` messages with text/thinking/tool_use deltas.
        cmd.add("--include-partial-messages")

        model?.let { cmd.addAll(listOf("--model", it)) }
        
        // Default to acceptEdits if not specified - this auto-approves Edit/Write operations
        // while still prompting for dangerous operations like Bash. For full automation, 
        // users can configure permission-mode=bypassPermissions in config.yaml.
        val effectivePermissionMode = permissionMode ?: "acceptEdits"
        cmd.addAll(listOf("--permission-mode", effectivePermissionMode))
        
        // Disable AskUserQuestion tool - not supported in stream-json mode without
        // interactive CLI capabilities. Claude Code's AskUserQuestion expects terminal
        // interaction which doesn't work well with the stream-json protocol.
        cmd.addAll(listOf("--disallowed-tools", "AskUserQuestion"))
        
        cmd.addAll(additionalArgs)

        logger.info { "[ClaudeCode] Starting: ${cmd.joinToString(" ")}" }

        val pb = ProcessBuilder(cmd)
        pb.directory(File(workingDirectory))
        pb.redirectErrorStream(false)

        envVars.forEach { (k, v) -> pb.environment()[k] = v }
        pb.environment()["PWD"] = workingDirectory
        pb.environment()["AUTODEV_WORKSPACE"] = workingDirectory

        val proc = pb.start()
        process = proc
        writer = proc.outputStream.bufferedWriter()
        reader = proc.inputStream.bufferedReader()

        if (enableLogging) {
            initLogging()
        }

        // Drain stderr asynchronously to prevent blocking
        scope.launch(Dispatchers.IO) {
            try {
                proc.errorStream.bufferedReader().use { err ->
                    err.lineSequence().forEach { line ->
                        logger.debug { "[ClaudeCode stderr] $line" }
                    }
                }
            } catch (_: Exception) { }
        }

        logger.info { "[ClaudeCode] Process started (pid=${proc.pid()})" }
    }

    /**
     * Send a prompt and render the response through the given [renderer].
     *
     * Claude Code in stream-json mode emits messages in this order:
     * 1. system init (on first prompt only)
     * 2. stream_event (content_block_start/delta/stop, message_start/delta/stop)
     * 3. assistant (assembled message with all content blocks)
     * 4. result (success/error)
     *
     * This blocks (suspends) until Claude finishes its response (receives a `result` message).
     */
    suspend fun promptAndRender(text: String, renderer: CodingAgentRenderer) {
        val proc = process ?: throw IllegalStateException("ClaudeCodeClient not started")
        val w = writer ?: throw IllegalStateException("Writer not available")
        val r = reader ?: throw IllegalStateException("Reader not available")

        // Send user message
        val userJson = buildClaudeUserInput(text, sessionId)
        logLine(">>> $userJson")
        withContext(Dispatchers.IO) {
            w.write(userJson)
            w.newLine()
            w.flush()
        }

        renderer.renderLLMResponseStart()

        var inThinking = false
        var inText = false
        var hasRenderedStreamContent = false
        val startTime = System.currentTimeMillis()
        var toolCount = 0
        // Track tool input JSON as it streams in via input_json_delta
        val pendingToolInputs = mutableMapOf<Int, StringBuilder>()

        // Read response lines until we get a result message
        withContext(Dispatchers.IO) {
            while (proc.isAlive) {
                val line = try {
                    r.readLine()
                } catch (_: IOException) {
                    null
                }

                if (line == null) break
                logLine(line)

                val msg = parseClaudeOutputLine(line) ?: continue

                when (msg.type) {
                    ClaudeMessageType.SYSTEM -> {
                        if (msg.subtype == "init") {
                            sessionId = msg.sessionId
                            logger.info { "[ClaudeCode] Initialized (session=${sessionId})" }
                        }
                    }

                    ClaudeMessageType.STREAM_EVENT -> {
                        val event = msg.streamEvent ?: continue
                        when (event.type) {
                            "content_block_start" -> {
                                val block = event.contentBlock ?: continue
                                when (block.type) {
                                    "thinking" -> {
                                        inThinking = true
                                        renderer.renderThinkingChunk("", isStart = true)
                                    }
                                    "text" -> {
                                        inText = true
                                    }
                                    "tool_use" -> {
                                        val toolId = block.id ?: ""
                                        val toolName = block.name ?: "unknown"
                                        val index = event.index ?: -1
                                        toolUseNames[toolId] = toolName
                                        
                                        // Initialize StringBuilder for this tool's input JSON
                                        if (index >= 0) {
                                            pendingToolInputs[index] = StringBuilder()
                                        }
                                        
                                        // DO NOT render yet - wait for assistant message with full params
                                        // This avoids showing "Bash" with no parameters
                                    }
                                }
                            }

                            "content_block_delta" -> {
                                val delta = event.delta ?: continue
                                val index = event.index ?: -1
                                
                                when (delta.type) {
                                    "thinking_delta" -> {
                                        delta.thinking?.let {
                                            hasRenderedStreamContent = true
                                            renderer.renderThinkingChunk(it)
                                        }
                                    }
                                    "text_delta" -> {
                                        delta.text?.let {
                                            hasRenderedStreamContent = true
                                            renderer.renderLLMResponseChunk(it)
                                        }
                                    }
                                    "input_json_delta" -> {
                                        // Accumulate tool input JSON as it streams in
                                        delta.partialJson?.let { jsonChunk ->
                                            if (index >= 0) {
                                                pendingToolInputs.getOrPut(index) { StringBuilder() }
                                                    .append(jsonChunk)
                                            }
                                        }
                                    }
                                }
                            }

                            "content_block_stop" -> {
                                if (inThinking) {
                                    inThinking = false
                                    renderer.renderThinkingChunk("", isEnd = true)
                                }
                                if (inText) {
                                    inText = false
                                }
                            }

                            "message_start", "message_delta", "message_stop" -> {
                                // Lifecycle events - skip
                            }
                        }
                    }

                    ClaudeMessageType.ASSISTANT -> {
                        // Assistant message contains assembled content blocks.
                        // For tool_use: render the tool call with full parameters.
                        // For text/thinking: already rendered by stream_event, skip.
                        for (c in msg.content) {
                            when (c.type) {
                                "tool_use" -> {
                                    val toolId = c.id ?: ""
                                    val toolName = c.name ?: "unknown"
                                    toolUseNames[toolId] = toolName
                                    
                                    // Parse tool input to a readable map
                                    val inputMap = try {
                                        c.input?.let { parseJsonToMap(it) } ?: emptyMap()
                                    } catch (e: Exception) {
                                        logger.warn { "[ClaudeCode] Failed to parse tool input for $toolName: ${e.message}" }
                                        emptyMap()
                                    }
                                    
                                    // Store for later lookup when tool_result arrives
                                    toolUseInputs[toolId] = inputMap

                                    // Only render if not already rendered
                                    if (!renderedToolIds.contains(toolId)) {
                                        toolCount++
                                        // Map Claude Code tool names and params to our internal format
                                        val mappedName = mapClaudeToolName(toolName)
                                        val mappedParams = mapClaudeParams(toolName, inputMap)
                                        renderer.renderToolCallWithParams(mappedName, mappedParams)
                                        renderedToolIds.add(toolId)
                                    }
                                }
                                // text/thinking already handled by stream_event
                            }
                        }
                    }

                    ClaudeMessageType.USER -> {
                        // User messages contain tool_result blocks (Claude executed the tool
                        // and wraps the result in a user message for the next turn).
                        // We must handle these to mark tool calls as COMPLETED.
                        for (c in msg.content) {
                            when (c.type) {
                                "tool_result" -> {
                                    val toolId = c.toolUseId ?: ""
                                    val toolName = toolUseNames[toolId] ?: "unknown"
                                    val mappedName = mapClaudeToolName(toolName)
                                    val isErr = c.isError == true
                                    val output = extractToolResultText(c)
                                    // Truncate output for display (keep full for expanded view)
                                    val summary = if (output.length > 200) {
                                        output.take(200) + "..."
                                    } else {
                                        output
                                    }
                                    renderer.renderToolResult(mappedName, !isErr, summary, output)
                                }
                                // "text" in user messages is just echo/context - skip
                            }
                        }
                    }

                    ClaudeMessageType.RESULT -> {
                        // The result message contains the final text in `result` field.
                        // If streaming was active (stream_event messages were received),
                        // the text was already rendered incrementally. If not, render it now.
                        val resultText = msg.result ?: ""
                        if (resultText.isNotEmpty() && !hasRenderedStreamContent) {
                            // No stream events were received -- render the full result text
                            renderer.renderLLMResponseChunk(resultText)
                        }

                        renderer.renderLLMResponseEnd()
                        val elapsed = System.currentTimeMillis() - startTime
                        val success = !msg.isError
                        renderer.renderFinalResult(
                            success = success,
                            message = "Claude Code finished: ${msg.subtype ?: "unknown"}" +
                                    if (msg.isError) " (error)" else "",
                            iterations = 0
                        )
                        renderer.renderTaskComplete(elapsed, toolCount)
                        return@withContext
                    }

                    ClaudeMessageType.UNKNOWN -> {
                        logger.debug { "[ClaudeCode] Unknown message type: ${msg.rawJson}" }
                    }
                }
            }
        }

        // If we get here without a result, the process may have exited
        if (process?.isAlive != true) {
            renderer.renderLLMResponseEnd()
            renderer.renderError("Claude Code process exited unexpectedly (exit code: ${process?.exitValue()})")
        }
    }

    /**
     * Kill the process.
     */
    fun stop() {
        try {
            writer?.close()
        } catch (_: Exception) {}
        try {
            reader?.close()
        } catch (_: Exception) {}
        try {
            process?.destroyForcibly()
        } catch (_: Exception) {}
        try {
            logWriter?.close()
        } catch (_: Exception) {}

        writer = null
        reader = null
        process = null
        logWriter = null
        sessionId = null
        toolUseNames.clear()
        toolUseInputs.clear()
        renderedToolIds.clear()
        logger.info { "[ClaudeCode] Stopped" }
    }

    // ─── Internals ─────────────────────────────────────────────────

    /**
     * Map Claude Code tool names to our internal ToolType names.
     * Claude Code uses different names (e.g., "Bash" vs our "shell", "Read" vs "read_file").
     * This mapping enables proper formatting and icon display in ComposeRenderer.
     */
    private fun mapClaudeToolName(claudeToolName: String): String {
        return when (claudeToolName) {
            "Bash" -> "shell"
            "Read" -> "read_file"
            "Write" -> "write_file"
            "Edit" -> "edit_file"
            "Glob" -> "glob"
            "Grep" -> "grep"
            "Task" -> "Task"
            "WebFetch" -> "WebFetch"
            "WebSearch" -> "WebSearch"
            "TodoWrite" -> "TodoWrite"
            "LS" -> "LS"
            else -> claudeToolName
        }
    }

    /**
     * Map Claude Code param keys to our internal param keys.
     * E.g., Claude uses "file_path" but our formatToolCallDisplay expects "path".
     */
    private fun mapClaudeParams(claudeToolName: String, params: Map<String, Any>): Map<String, Any> {
        return when (claudeToolName) {
            "Read", "Write", "Edit" -> {
                // Map "file_path" -> "path" for compatibility with RendererUtils.formatToolCallDisplay
                val mapped = params.toMutableMap()
                params["file_path"]?.let { mapped["path"] = it }
                mapped
            }
            "Bash" -> {
                // Map "command" + "description" 
                val mapped = params.toMutableMap()
                // "command" already matches our internal key
                mapped
            }
            else -> params
        }
    }

    /**
     * Parse JsonElement to Map<String, Any> for rendering.
     * Handles both JsonObject (already parsed) and String (needs parsing).
     */
    private fun parseJsonToMap(input: Any?): Map<String, Any> {
        return try {
            when (input) {
                is JsonElement -> {
                    // Already a JsonElement from kotlinx.serialization
                    if (input is JsonObject) {
                        input.entries.associate { (key, value) ->
                            key to when (value) {
                                is JsonPrimitive -> value.contentOrNull ?: value.toString()
                                is JsonArray -> value.toString()
                                is JsonObject -> value.toString()
                                else -> value.toString()
                            }
                        }
                    } else {
                        emptyMap()
                    }
                }
                is String -> {
                    // String that needs parsing
                    val json = Json { ignoreUnknownKeys = true }
                    val element = json.parseToJsonElement(input)
                    parseJsonToMap(element) // Recursive call with parsed element
                }
                else -> emptyMap()
            }
        } catch (e: Exception) {
            logger.warn { "[ClaudeCode] Failed to parse tool input: ${e.message}" }
            emptyMap()
        }
    }

    private fun extractToolResultText(content: ClaudeContent): String {
        val c = content.content
        return when (c) {
            is JsonPrimitive -> c.contentOrNull ?: ""
            else -> c?.toString() ?: ""
        }
    }

    private fun initLogging() {
        try {
            val logDir = File(System.getProperty("user.home"), ".autodev/acp-logs")
            logDir.mkdirs()
            val ts = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
            val logFile = File(logDir, "ClaudeCode_$ts.jsonl")
            logWriter = logFile.bufferedWriter()
            logger.info { "[ClaudeCode] Logging to ${logFile.absolutePath}" }
        } catch (e: Exception) {
            logger.warn { "[ClaudeCode] Failed to init logging: ${e.message}" }
        }
    }

    private fun logLine(line: String) {
        try {
            logWriter?.apply {
                write(line)
                newLine()
                flush()
            }
        } catch (_: Exception) {}
    }
}
