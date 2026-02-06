package cc.unitmesh.devins.ui.compose.agent.acp

import cc.unitmesh.agent.render.CodingAgentRenderer
import cc.unitmesh.config.AcpAgentConfig

/**
 * Cross-platform ACP connection provider.
 *
 * On JVM, this spawns the agent process and communicates via ACP (JSON-RPC over stdio).
 * On other platforms, this returns null (ACP agents are not supported).
 */
expect fun createAcpConnection(): AcpConnection?

/**
 * Create the appropriate connection for a given agent config.
 * For Claude Code agents, uses the Claude stream-json protocol.
 * For all other agents, uses standard ACP JSON-RPC.
 */
expect fun createConnectionForAgent(config: AcpAgentConfig): AcpConnection?

/**
 * Whether the current platform supports ACP agent connections.
 */
expect fun isAcpSupported(): Boolean

/**
 * Abstract ACP connection that manages the lifecycle of an external ACP agent process.
 *
 * Events are streamed directly to a [CodingAgentRenderer], allowing seamless integration
 * with the existing timeline UI (ComposeRenderer).
 */
interface AcpConnection {
    val isConnected: Boolean

    /**
     * Connect to the ACP agent: spawn the process, initialize protocol, create session.
     */
    suspend fun connect(config: AcpAgentConfig, cwd: String)

    /**
     * Send a prompt to the agent. Events are streamed to the provided [renderer].
     */
    suspend fun prompt(text: String, renderer: CodingAgentRenderer): String

    /**
     * Cancel the current prompt.
     */
    suspend fun cancel()

    /**
     * Disconnect from the agent and kill the process.
     */
    suspend fun disconnect()
}
