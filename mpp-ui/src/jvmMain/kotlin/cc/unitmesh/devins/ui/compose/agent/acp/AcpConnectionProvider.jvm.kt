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
 * Uses [AcpClient] from mpp-core which handles the ACP protocol details.
 * Events are streamed directly to the provided [CodingAgentRenderer] via
 * [AcpClient.promptAndRender], allowing seamless integration with ComposeRenderer.
 */
class JvmAcpConnection : AcpConnection {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var process: Process? = null
    private var acpClient: AcpClient? = null

    override val isConnected: Boolean get() = acpClient?.isConnected == true

    override suspend fun connect(
        config: AcpAgentConfig,
        cwd: String
    ) {
        withContext(Dispatchers.IO) {
            val effectiveCwd = cwd.ifBlank { System.getProperty("user.dir") ?: cwd }

            // Build command (and inject workdir for kimi if not provided)
            val args = config.getArgsList().toMutableList()
            if (looksLikeKimi(config.command) && !hasWorkDirArg(args)) {
                // Kimi CLI supports `--work-dir <path>` to pin its workspace.
                // This is critical because Kimi's shell actions may run in separate subprocesses,
                // so `cd`-based prompts are unreliable.
                args.addAll(0, listOf("--work-dir", effectiveCwd))
            }

            val commandList = mutableListOf(config.command).apply { addAll(args) }

            println("[ACP] Spawning agent: ${commandList.joinToString(" ")}")

            // Spawn process
            val pb = ProcessBuilder(commandList)
            pb.directory(File(effectiveCwd))
            pb.redirectErrorStream(false)

            // Add environment variables
            config.getEnvMap().forEach { (key, value) ->
                pb.environment()[key] = value
            }
            // Provide workspace hints for agents that rely on env vars.
            pb.environment()["PWD"] = effectiveCwd
            pb.environment()["AUTODEV_WORKSPACE"] = effectiveCwd

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
                cwd = effectiveCwd,
                agentName = config.name.ifBlank { "acp-agent" },
                enableLogging = true
            )

            client.connect()
            acpClient = client

            println("[ACP] Connected to agent successfully")
        }
    }

    override suspend fun prompt(text: String, renderer: CodingAgentRenderer): String {
        val client = acpClient ?: throw IllegalStateException("ACP client not connected")

        withContext(Dispatchers.IO) {
            client.promptAndRender(text, renderer)
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

    private fun looksLikeKimi(command: String): Boolean {
        val base = command.substringAfterLast('/').substringAfterLast('\\')
        return base.equals("kimi", ignoreCase = true) || base.equals("kimi.exe", ignoreCase = true)
    }

    private fun hasWorkDirArg(args: List<String>): Boolean {
        // Accept both "--work-dir" and "--workdir" just in case.
        return args.any { it == "--work-dir" || it == "--workdir" }
    }
}
