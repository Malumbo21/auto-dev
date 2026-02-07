package cc.unitmesh.devins.ui.compose.agent.acp

import cc.unitmesh.agent.acp.AcpClient
import cc.unitmesh.agent.claude.ClaudeCodeClient
import cc.unitmesh.agent.render.CodingAgentRenderer
import cc.unitmesh.config.AcpAgentConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.io.asSink
import kotlinx.io.asSource
import java.io.File

/**
 * Create the appropriate connection based on agent configuration.
 * For Claude Code agents, uses [JvmClaudeCodeConnection] with direct stream-json protocol.
 * For all other ACP agents (Auggie, Gemini, Kimi, Copilot, etc.), uses [JvmAcpConnection].
 */
actual fun createAcpConnection(): AcpConnection? = JvmAcpConnection()

/**
 * Create the appropriate connection for the given agent config.
 * - Claude Code: uses [JvmClaudeCodeConnection] with direct stream-json protocol.
 * - Auggie: uses [JvmAcpConnection] with standard ACP JSON-RPC.
 * - All others (Kimi, Gemini, etc.): uses [JvmAcpConnection] with standard ACP JSON-RPC.
 *
 * Supported agents:
 * - **Auggie**: Augment Code's AI agent (https://docs.augmentcode.com/cli/acp/agent)
 * - **Claude Code**: Anthropic's Claude Code agent
 * - **Kimi**: Chinese AI agent with strong coding capabilities
 * - **Gemini**: Google's Gemini agent
 *
 * @see <a href="https://github.com/phodal/auto-dev/issues/538">Issue #538</a>
 */
actual fun createConnectionForAgent(config: AcpAgentConfig): AcpConnection? {
    return if (looksLikeClaude(config.command)) {
        JvmClaudeCodeConnection()
    } else {
        JvmAcpConnection()
    }
}

actual fun isAcpSupported(): Boolean = true

/**
 * JVM implementation of AcpConnection.
 * Spawns the agent as a child process and communicates via ACP (JSON-RPC over stdio).
 *
 * Uses [AcpClient] from mpp-core which handles the ACP protocol details.
 * Events are streamed directly to the provided [CodingAgentRenderer] via
 * [AcpClient.promptAndRender], allowing seamless integration with ComposeRenderer.
 *
 * Supports all standard ACP agents including:
 * - Auggie (https://docs.augmentcode.com/cli/acp/agent)
 * - Kimi CLI (with automatic --work-dir injection)
 * - Gemini CLI
 * - Any other ACP-compliant agent
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

/**
 * JVM implementation for Claude Code using direct stream-json protocol.
 *
 * Unlike ACP agents, Claude Code uses its own JSON streaming protocol:
 * - Launches `claude -p --output-format stream-json --input-format stream-json`
 * - Reads JSON lines from stdout (system, stream_event, assistant, result messages)
 * - Writes JSON lines to stdin (user messages)
 *
 * This is a Kotlin adaptation of the approach used in:
 * - IDEA ml-llm: [ClaudeCodeProcessHandler] + [ClaudeCodeLongRunningSession]
 * - zed-industries/claude-code-acp: TypeScript ACP adapter
 *
 * @see <a href="https://github.com/phodal/auto-dev/issues/538">Issue #538</a>
 */
class JvmClaudeCodeConnection : AcpConnection {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var client: ClaudeCodeClient? = null

    override val isConnected: Boolean get() = client?.isConnected == true

    override suspend fun connect(config: AcpAgentConfig, cwd: String) {
        withContext(Dispatchers.IO) {
            val effectiveCwd = cwd.ifBlank { System.getProperty("user.dir") ?: cwd }

            // Parse extra args from config (skip -p, --output-format, --input-format which we set)
            val extraArgs = config.getArgsList().toMutableList()

            // Extract model and permission-mode from args if present
            var model: String? = null
            var permissionMode: String? = null
            val filteredArgs = mutableListOf<String>()
            val iter = extraArgs.iterator()
            while (iter.hasNext()) {
                val arg = iter.next()
                when (arg) {
                    "--model" -> { if (iter.hasNext()) model = iter.next() }
                    "--permission-mode" -> { if (iter.hasNext()) permissionMode = iter.next() }
                    // Skip flags we add ourselves
                    "-p", "--print", "--output-format", "--input-format", "--verbose",
                    "--include-partial-messages" -> {}
                    "stream-json" -> {} // value of --output-format or --input-format
                    else -> filteredArgs.add(arg)
                }
            }

            val claudeClient = ClaudeCodeClient(
                scope = scope,
                binaryPath = config.command,
                workingDirectory = effectiveCwd,
                agentName = config.name.ifBlank { "Claude Code" },
                enableLogging = true,
                model = model,
                permissionMode = permissionMode,
                additionalArgs = filteredArgs,
                envVars = config.getEnvMap(),
            )

            claudeClient.start()
            client = claudeClient

            println("[ClaudeCode] Connected to Claude Code at ${config.command}")
        }
    }

    override suspend fun prompt(text: String, renderer: CodingAgentRenderer): String {
        val c = client ?: throw IllegalStateException("ClaudeCodeClient not connected")
        c.promptAndRender(text, renderer)
        return "completed"
    }

    override suspend fun cancel() {
        // Claude Code in -p mode doesn't have a cancel mechanism via stdin.
        // The only way is to kill the process (which disconnect() does).
        println("[ClaudeCode] Cancel requested - stopping process")
        disconnect()
    }

    override suspend fun disconnect() {
        try {
            client?.stop()
        } catch (_: Exception) {}
        client = null
        println("[ClaudeCode] Disconnected")
    }
}

/**
 * Check if a command path looks like the Claude Code CLI.
 */
private fun looksLikeClaude(command: String): Boolean {
    val base = command.substringAfterLast('/').substringAfterLast('\\').lowercase()
    return base == "claude" || base == "claude.exe" || base == "claude-code" || base == "claude-code.exe"
}
