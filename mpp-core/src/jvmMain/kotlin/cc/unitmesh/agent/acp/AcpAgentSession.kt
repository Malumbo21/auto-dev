package cc.unitmesh.agent.acp

import cc.unitmesh.agent.render.CodingAgentRenderer
import cc.unitmesh.config.AcpAgentConfig
import cc.unitmesh.config.ConfigManager
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.io.asSink
import kotlinx.io.asSource

private val logger = KotlinLogging.logger("AcpAgentSession")

/**
 * High-level session manager for ACP agent interactions.
 *
 * Combines [AcpAgentProcessManager] and [AcpClient] to provide:
 * - Automatic process lifecycle management (reuse existing processes)
 * - Session creation and reconnection
 * - Multi-turn prompt support within a single session
 * - Clean resource disposal
 *
 * Usage:
 * ```kotlin
 * val session = AcpAgentSession.create("codex", projectPath)
 * session.promptAndRender("Fix the bug in main.kt", renderer)
 * // ... later, another prompt in the same session:
 * session.promptAndRender("Now add tests for the fix", renderer)
 * // When done:
 * session.close()
 * ```
 */
class AcpAgentSession private constructor(
    private val agentKey: String,
    private val cwd: String,
    private val scope: CoroutineScope,
) {
    private var client: AcpClient? = null
    private var managedProcess: ManagedProcess? = null

    val isConnected: Boolean get() = client?.isConnected == true

    /**
     * Connect (or reconnect) to the ACP agent.
     *
     * If the agent process has died, a new one is spawned.
     * If the ACP session has been lost, a new session is created.
     */
    suspend fun ensureConnected(config: AcpAgentConfig) {
        if (client?.isConnected == true && managedProcess?.isAlive() == true) {
            logger.debug { "ACP session '$agentKey' already connected" }
            return
        }

        // Clean up stale client
        if (client != null) {
            try {
                client?.disconnect()
            } catch (_: Exception) {
            }
            client = null
        }

        val processManager = AcpAgentProcessManager.getInstance()
        val managed = processManager.getOrCreateProcess(agentKey, config, cwd)
        this.managedProcess = managed

        val acpClient = AcpClient(
            coroutineScope = scope,
            input = managed.inputStream.asSource(),
            output = managed.outputStream.asSink(),
            cwd = cwd,
            agentName = agentKey,
            enableLogging = true,
        )

        acpClient.connect()
        this.client = acpClient

        logger.info { "ACP session '$agentKey' connected successfully" }
    }

    /**
     * Send a prompt and stream raw ACP events.
     * Automatically reconnects if the session has been lost.
     */
    suspend fun prompt(text: String, config: AcpAgentConfig): Flow<com.agentclientprotocol.common.Event> {
        ensureConnected(config)
        return client?.prompt(text) ?: throw IllegalStateException("ACP client not connected after ensureConnected")
    }

    /**
     * Send a prompt and render updates to a [CodingAgentRenderer].
     * Automatically reconnects if the session has been lost.
     */
    suspend fun promptAndRender(text: String, config: AcpAgentConfig, renderer: CodingAgentRenderer) {
        ensureConnected(config)
        val acpClient = client ?: throw IllegalStateException("ACP client not connected after ensureConnected")
        acpClient.promptAndRender(text, renderer)
    }

    /**
     * Cancel the current prompt.
     */
    suspend fun cancel() {
        try {
            client?.cancel()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to cancel ACP session '$agentKey'" }
        }
    }

    /**
     * Close the session and disconnect from the agent.
     * The underlying process is kept alive for potential reuse by other sessions.
     */
    suspend fun close() {
        try {
            client?.disconnect()
        } catch (e: Exception) {
            logger.warn(e) { "Error disconnecting ACP client for '$agentKey'" }
        }
        client = null
        managedProcess = null
        logger.info { "ACP session '$agentKey' closed" }
    }

    /**
     * Close the session AND terminate the underlying agent process.
     */
    suspend fun closeAndTerminate() {
        close()
        AcpAgentProcessManager.getInstance().terminateProcess(agentKey)
        logger.info { "ACP agent process '$agentKey' terminated" }
    }

    companion object {
        /**
         * Create a new AcpAgentSession for the given agent key.
         *
         * The session is not connected yet; call [ensureConnected] or use
         * [promptAndRender] which auto-connects.
         */
        fun create(
            agentKey: String,
            cwd: String,
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ): AcpAgentSession {
            return AcpAgentSession(
                agentKey = agentKey,
                cwd = cwd,
                scope = scope,
            )
        }

        /**
         * Create a session from the active ACP agent in config.yaml.
         *
         * @throws IllegalStateException if no active ACP agent is configured
         */
        suspend fun createFromConfig(
            cwd: String,
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ): Pair<AcpAgentSession, AcpAgentConfig> {
            val wrapper = ConfigManager.load()
            val agentKey = wrapper.getActiveAcpAgentKey()
                ?: throw IllegalStateException("No active ACP agent configured. Set activeAcpAgent in config.yaml.")
            val config = wrapper.getActiveAcpAgent()
                ?: throw IllegalStateException("Agent '$agentKey' not found in config.yaml.")

            val session = create(agentKey, cwd, scope)
            return session to config
        }
    }
}
