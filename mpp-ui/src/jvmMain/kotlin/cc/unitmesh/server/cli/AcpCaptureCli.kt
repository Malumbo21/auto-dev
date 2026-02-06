package cc.unitmesh.server.cli

import cc.unitmesh.agent.render.CodingAgentRenderer
import cc.unitmesh.config.AcpAgentConfig
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devins.ui.compose.agent.acp.createAcpConnection
import cc.unitmesh.llm.compression.TokenInfo
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Captures ACP agent responses for analysis.
 * 
 * Key: overrides renderToolCallWithParams to capture the structured params map
 * (kind, status, input) instead of the flattened string.
 * 
 * Usage:
 * ```bash
 * ./gradlew :mpp-ui:runAcpCapture -PacpPrompt="画一下项目架构图"
 * ```
 */
object AcpCaptureCli {

    @JvmStatic
    fun main(args: Array<String>) {
        println("=".repeat(80))
        println("ACP Response Capture CLI")
        println("=".repeat(80))
        println()

        val prompt = System.getProperty("acpPrompt")
            ?: args.firstOrNull { it.startsWith("--prompt=") }?.substringAfter("=")
            ?: args.getOrNull(0)
            ?: run {
            System.err.println("Usage: -PacpPrompt=\"your prompt here\"")
            return
        }

        val agentKeyOverride = System.getProperty("acpAgentKey")
            ?: args.firstOrNull { it.startsWith("--agent=") }?.substringAfter("=")
        val cwdOverride = System.getProperty("acpCwd")
            ?: args.firstOrNull { it.startsWith("--cwd=") }?.substringAfter("=")

        println("Prompt: $prompt")
        if (!agentKeyOverride.isNullOrBlank()) {
            println("Agent override: $agentKeyOverride")
        }
        if (!cwdOverride.isNullOrBlank()) {
            println("CWD override: $cwdOverride")
        }
        println()

        runBlocking { captureAcpResponse(prompt, agentKeyOverride, cwdOverride) }
    }

    private suspend fun captureAcpResponse(prompt: String, agentKeyOverride: String?, cwdOverride: String?) {
        val configWrapper = ConfigManager.load()
        val acpAgents = configWrapper.getAcpAgents()
        val activeAgentKey = agentKeyOverride?.takeIf { it.isNotBlank() } ?: configWrapper.getActiveAcpAgentKey()

        if (activeAgentKey == null) {
            System.err.println("No active ACP agent configured")
            return
        }

        val acpConfig: AcpAgentConfig = acpAgents[activeAgentKey] ?: run {
            System.err.println("ACP agent '$activeAgentKey' not found")
            return
        }

        println("Agent: ${acpConfig.name} (${acpConfig.command})")
        println()

        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val jsonlFile = File("docs/test-scripts/acp-captures/raw_$ts.jsonl")
        jsonlFile.parentFile.mkdirs()

        val renderer = StructuredCaptureRenderer(jsonlFile)

        val connection = createAcpConnection() ?: run {
            System.err.println("ACP not supported on this platform")
            return
        }

        try {
            println("Connecting...")
            val effectiveCwd = cwdOverride?.takeIf { it.isNotBlank() } ?: System.getProperty("user.dir")
            connection.connect(acpConfig, effectiveCwd)

            if (connection.isConnected) {
                println("Connected. Sending prompt...\n")
                connection.prompt(prompt, renderer)

                println("\n\n" + "=".repeat(80))
                println("Capture complete -> ${jsonlFile.absolutePath}")
                println("=".repeat(80))
                renderer.printSummary()
            } else {
                System.err.println("Failed to connect")
            }
        } catch (e: Exception) {
            System.err.println("Error: ${e.message}")
            e.printStackTrace()
        } finally {
            connection.disconnect()
            renderer.close()
        }
    }
}

/**
 * Renderer that captures every event as a JSONL line, preserving the structured
 * params from renderToolCallWithParams (kind/status/input).
 */
class StructuredCaptureRenderer(jsonlFile: File) : CodingAgentRenderer {
    private val writer = FileWriter(jsonlFile)
    private var n = 0

    // Stats
    var llmChunks = 0; private set
    var toolCalls = 0; private set
    var toolCallUpdates = 0; private set
    var toolResults = 0; private set
    private val toolCallsByTitle = mutableMapOf<String, Int>()
    private val toolCallsByStatus = mutableMapOf<String, Int>()
    private val toolCallIdsSeen = mutableSetOf<String>()

    private fun emit(type: String, fields: Map<String, String>) {
        n++
        val sb = StringBuilder()
        sb.append("{\"n\":$n,\"type\":\"$type\"")
        fields.forEach { (k, v) -> sb.append(",\"$k\":\"${esc(v)}\"") }
        sb.append("}")
        writer.write(sb.toString())
        writer.write("\n")
        writer.flush()
    }

    // ─── LLM ─────────────────────────────────────────────────────

    override fun renderLLMResponseStart() {
        emit("LLM_START", emptyMap())
        print("[LLM] ")
    }

    override fun renderLLMResponseChunk(chunk: String) {
        llmChunks++
        emit("LLM_CHUNK", mapOf("text" to chunk))
        print(chunk)
    }

    override fun renderLLMResponseEnd() {
        emit("LLM_END", emptyMap())
        println()
    }

    override fun renderThinkingChunk(chunk: String, isStart: Boolean, isEnd: Boolean) {
        emit("THINKING", mapOf("text" to chunk, "isStart" to isStart.toString(), "isEnd" to isEnd.toString()))
    }

    // ─── Tool calls (key override!) ──────────────────────────────

    /**
     * This is what AcpClient actually calls. We override it to capture the
     * structured map (kind, status, input) before it gets flattened.
     */
    override fun renderToolCallWithParams(toolName: String, params: Map<String, Any>) {
        val kind = params["kind"]?.toString() ?: ""
        val status = params["status"]?.toString() ?: ""
        val input = params["input"]?.toString() ?: ""

        // Track stats
        val statusKey = "$toolName:$status"
        toolCallsByTitle[toolName] = (toolCallsByTitle[toolName] ?: 0) + 1
        toolCallsByStatus[statusKey] = (toolCallsByStatus[statusKey] ?: 0) + 1

        // Determine if this is an "update" (same tool repeated) or "new"
        if (status == "IN_PROGRESS" || status == "PENDING") {
            toolCallUpdates++
        } else {
            toolCalls++
        }

        emit("TOOL_CALL", mapOf(
            "title" to toolName,
            "kind" to kind,
            "status" to status,
            "inputLength" to input.length.toString(),
            "inputPreview" to input.take(300)
        ))

        // Console output: only print meaningful state changes
        when (status) {
            "IN_PROGRESS" -> {
                // Don't spam console for every IN_PROGRESS chunk
                if (toolCallUpdates % 50 == 1) {
                    println("  [$n] $toolName (streaming... ${toolCallUpdates} updates)")
                }
            }
            "COMPLETED" -> {
                println("  [$n] $toolName COMPLETED (input: ${input.take(60)})")
            }
            else -> {
                println("  [$n] $toolName [$status] kind=$kind")
            }
        }
    }

    override fun renderToolCall(toolName: String, paramsStr: String) {
        // Should not be called if renderToolCallWithParams is overridden,
        // but capture it anyway
        toolCalls++
        emit("TOOL_CALL_STR", mapOf("toolName" to toolName, "params" to paramsStr))
        println("  [$n] $toolName (string params): ${paramsStr.take(80)}")
    }

    override fun renderToolResult(
        toolName: String,
        success: Boolean,
        output: String?,
        fullOutput: String?,
        metadata: Map<String, String>
    ) {
        toolResults++
        emit("TOOL_RESULT", mapOf(
            "toolName" to toolName,
            "success" to success.toString(),
            "outputLength" to (output?.length ?: 0).toString(),
            "outputPreview" to (output?.take(200) ?: "")
        ))
        println("  [$n] RESULT: $toolName success=$success output=${output?.take(60) ?: "null"}")
    }

    // ─── Other events ────────────────────────────────────────────

    override fun renderIterationHeader(current: Int, max: Int) {
        emit("ITERATION", mapOf("current" to current.toString(), "max" to max.toString()))
    }

    override fun renderTaskComplete(executionTimeMs: Long, toolsUsedCount: Int) {
        emit("TASK_COMPLETE", mapOf("time" to executionTimeMs.toString(), "tools" to toolsUsedCount.toString()))
    }

    override fun renderFinalResult(success: Boolean, message: String, iterations: Int) {
        emit("FINAL", mapOf("success" to success.toString(), "message" to message))
        println("\n[FINAL] success=$success message=$message")
    }

    override fun renderError(message: String) {
        emit("ERROR", mapOf("message" to message))
        System.err.println("[ERROR] $message")
    }

    override fun renderRepeatWarning(toolName: String, count: Int) {}
    override fun renderRecoveryAdvice(recoveryAdvice: String) {}
    override fun renderUserConfirmationRequest(toolName: String, params: Map<String, Any>) {}
    override fun updateTokenInfo(tokenInfo: TokenInfo) {
        emit("TOKENS", mapOf("in" to tokenInfo.inputTokens.toString(), "out" to tokenInfo.outputTokens.toString()))
    }

    fun printSummary() {
        println()
        println("Summary:")
        println("  Total events: $n")
        println("  LLM chunks: $llmChunks")
        println("  Tool calls (new/completed): $toolCalls")
        println("  Tool call updates (IN_PROGRESS/PENDING): $toolCallUpdates")
        println("  Tool results: $toolResults")
        println()
        println("  By title:")
        toolCallsByTitle.entries.sortedByDescending { it.value }.forEach { (k, v) ->
            println("    $k: $v")
        }
        println()
        println("  By title:status:")
        toolCallsByStatus.entries.sortedByDescending { it.value }.take(20).forEach { (k, v) ->
            println("    $k: $v")
        }
    }

    fun close() {
        writer.close()
    }

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
