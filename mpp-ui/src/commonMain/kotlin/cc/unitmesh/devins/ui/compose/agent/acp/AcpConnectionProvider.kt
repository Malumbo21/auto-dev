package cc.unitmesh.devins.ui.compose.agent.acp

import cc.unitmesh.config.AcpAgentConfig

/**
 * Callbacks for receiving ACP session updates from an external agent.
 */
data class AcpSessionCallbacks(
    val onTextChunk: (String) -> Unit = {},
    val onThoughtChunk: (String) -> Unit = {},
    val onToolCall: (title: String, status: String, input: String?, output: String?) -> Unit =
        { _, _, _, _ -> },
    val onPlanUpdate: (entries: List<PlanEntry>) -> Unit = {},
    val onError: (String) -> Unit = {},
    val onComplete: (stopReason: String) -> Unit = {}
)

data class PlanEntry(
    val content: String,
    val status: String
)

/**
 * Cross-platform ACP connection provider.
 *
 * On JVM, this spawns the agent process and communicates via ACP (JSON-RPC over stdio).
 * On other platforms, this returns null (ACP agents are not supported).
 */
expect fun createAcpConnection(): AcpConnection?

/**
 * Whether the current platform supports ACP agent connections.
 */
expect fun isAcpSupported(): Boolean

/**
 * Abstract ACP connection that manages the lifecycle of an external ACP agent process.
 */
interface AcpConnection {
    val isConnected: Boolean

    /**
     * Connect to the ACP agent: spawn the process, initialize protocol, create session.
     */
    suspend fun connect(config: AcpAgentConfig, cwd: String, callbacks: AcpSessionCallbacks)

    /**
     * Send a prompt to the agent and wait for completion.
     */
    suspend fun prompt(text: String): String

    /**
     * Cancel the current prompt.
     */
    suspend fun cancel()

    /**
     * Disconnect from the agent and kill the process.
     */
    suspend fun disconnect()
}
