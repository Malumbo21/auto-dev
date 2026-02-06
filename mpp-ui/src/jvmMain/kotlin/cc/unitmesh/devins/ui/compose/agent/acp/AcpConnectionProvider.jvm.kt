package cc.unitmesh.devins.ui.compose.agent.acp

import cc.unitmesh.agent.acp.AcpClient
import cc.unitmesh.agent.render.CodingAgentRenderer
import cc.unitmesh.config.AcpAgentConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.io.asSink
import kotlinx.io.asSource
import java.io.File

actual fun createAcpConnection(): AcpConnection? = JvmAcpConnection()

actual fun isAcpSupported(): Boolean = true

/**
 * JVM implementation of AcpConnection.
 * Spawns the agent as a child process and communicates via ACP (JSON-RPC over stdio).
 *
 * Uses [AcpClient] from mpp-core which already handles the ACP protocol details.
 * Session updates are bridged via a custom [CodingAgentRenderer] that forwards events
 * to the [AcpSessionCallbacks].
 */
class JvmAcpConnection : AcpConnection {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var process: Process? = null
    private var acpClient: AcpClient? = null
    private var callbacks: AcpSessionCallbacks? = null

    override val isConnected: Boolean get() = acpClient?.isConnected == true

    override suspend fun connect(
        config: AcpAgentConfig,
        cwd: String,
        callbacks: AcpSessionCallbacks
    ) {
        this.callbacks = callbacks

        withContext(Dispatchers.IO) {
            // Build command
            val commandList = mutableListOf(config.command)
            commandList.addAll(config.getArgsList())

            println("[ACP] Spawning agent: ${commandList.joinToString(" ")}")

            // Spawn process
            val pb = ProcessBuilder(commandList)
            pb.directory(File(cwd))
            pb.redirectErrorStream(false)

            // Add environment variables
            config.getEnvMap().forEach { (key, value) ->
                pb.environment()[key] = value
            }

            val proc = pb.start()
            process = proc

            // Create ACP client using the process's stdio
            val input = proc.inputStream.asSource()
            val output = proc.outputStream.asSink()

            val client = AcpClient(
                coroutineScope = scope,
                input = input,
                output = output,
                clientName = "autodev-xiuper-compose",
                clientVersion = "3.0.0",
                cwd = cwd
            )

            client.connect()
            acpClient = client

            println("[ACP] Connected to agent successfully")
        }
    }

    override suspend fun prompt(text: String): String {
        val client = acpClient ?: throw IllegalStateException("ACP client not connected")
        val cbs = callbacks ?: throw IllegalStateException("No callbacks set")

        // Bridge ACP events to our callbacks via CodingAgentRenderer
        val bridgeRenderer = AcpCallbackBridgeRenderer(cbs)

        withContext(Dispatchers.IO) {
            client.promptAndRender(text, bridgeRenderer)
        }

        return "completed"
    }

    override suspend fun cancel() {
        try {
            acpClient?.cancel()
        } catch (e: Exception) {
            println("[ACP] Cancel failed: ${e.message}")
        }
    }

    override suspend fun disconnect() {
        try {
            acpClient?.disconnect()
        } catch (_: Exception) {}
        acpClient = null

        try {
            process?.destroyForcibly()
        } catch (_: Exception) {}
        process = null

        println("[ACP] Disconnected")
    }
}

/**
 * Bridge [CodingAgentRenderer] that forwards ACP events to [AcpSessionCallbacks].
 *
 * This allows us to reuse [AcpClient.promptAndRender] without exposing ACP SDK types,
 * since [CodingAgentRenderer] is a common API from mpp-core.
 */
private class AcpCallbackBridgeRenderer(
    private val callbacks: AcpSessionCallbacks
) : CodingAgentRenderer {

    override fun renderLLMResponseStart() {}

    override fun renderLLMResponseChunk(chunk: String) {
        callbacks.onTextChunk(chunk)
    }

    override fun renderLLMResponseEnd() {}

    override fun renderThinkingChunk(chunk: String, isStart: Boolean, isEnd: Boolean) {
        callbacks.onThoughtChunk(chunk)
    }

    override fun renderToolCall(toolName: String, paramsStr: String) {
        callbacks.onToolCall(toolName, "running", paramsStr, null)
    }

    override fun renderToolCallWithParams(toolName: String, params: Map<String, Any>) {
        val paramsStr = params.entries.joinToString(" ") { (key, value) -> "$key=\"$value\"" }
        callbacks.onToolCall(toolName, "running", paramsStr, null)
    }

    override fun renderToolResult(
        toolName: String,
        success: Boolean,
        output: String?,
        fullOutput: String?,
        metadata: Map<String, String>
    ) {
        callbacks.onToolCall(
            toolName,
            if (success) "completed" else "error",
            null,
            output ?: fullOutput
        )
    }

    override fun renderFinalResult(success: Boolean, message: String, iterations: Int) {
        callbacks.onComplete(if (success) "completed" else "failed")
    }

    override fun renderError(message: String) {
        callbacks.onError(message)
    }

    override fun renderIterationHeader(current: Int, max: Int) {}
    override fun renderTaskComplete(executionTimeMs: Long, toolsUsedCount: Int) {}
    override fun renderRepeatWarning(toolName: String, count: Int) {}
    override fun renderRecoveryAdvice(recoveryAdvice: String) {}
    override fun renderUserConfirmationRequest(toolName: String, params: Map<String, Any>) {}
}
