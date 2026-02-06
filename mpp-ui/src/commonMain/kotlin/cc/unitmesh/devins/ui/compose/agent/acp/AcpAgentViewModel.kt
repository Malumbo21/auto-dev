package cc.unitmesh.devins.ui.compose.agent.acp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.unitmesh.config.AcpAgentConfig
import kotlinx.coroutines.*

/**
 * Represents a single message or event in the ACP chat timeline.
 */
sealed class AcpTimelineItem {
    abstract val timestamp: Long

    data class UserMessage(
        val content: String,
        override val timestamp: Long = currentTimeMillis()
    ) : AcpTimelineItem()

    data class AgentMessage(
        val content: String,
        val isStreaming: Boolean = false,
        override val timestamp: Long = currentTimeMillis()
    ) : AcpTimelineItem()

    data class ThinkingBlock(
        val content: String,
        val isActive: Boolean = false,
        override val timestamp: Long = currentTimeMillis()
    ) : AcpTimelineItem()

    data class ToolCall(
        val title: String,
        val status: String,
        val input: String? = null,
        val output: String? = null,
        override val timestamp: Long = currentTimeMillis()
    ) : AcpTimelineItem()

    data class PlanBlock(
        val entries: List<PlanEntry>,
        override val timestamp: Long = currentTimeMillis()
    ) : AcpTimelineItem()

    data class ErrorMessage(
        val message: String,
        override val timestamp: Long = currentTimeMillis()
    ) : AcpTimelineItem()

    data class SystemMessage(
        val content: String,
        override val timestamp: Long = currentTimeMillis()
    ) : AcpTimelineItem()
}

private fun currentTimeMillis(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

/**
 * ViewModel for managing an ACP agent session.
 *
 * Handles the lifecycle of connecting to an external ACP agent,
 * sending prompts, and receiving streaming updates.
 *
 * When a custom ACP agent is active, all interaction bypasses the local LLM service
 * and goes through the external agent process.
 */
class AcpAgentViewModel(
    private val projectPath: String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Timeline of all events
    private val _timeline = mutableStateListOf<AcpTimelineItem>()
    val timeline: List<AcpTimelineItem> = _timeline

    // Connection state
    var isConnected by mutableStateOf(false)
        private set
    var isConnecting by mutableStateOf(false)
        private set
    var isExecuting by mutableStateOf(false)
        private set
    var connectionError by mutableStateOf<String?>(null)
        private set

    // Current agent config
    var currentConfig by mutableStateOf<AcpAgentConfig?>(null)
        private set

    // Streaming state
    private var currentStreamingContent = StringBuilder()
    private var currentThinkingContent = StringBuilder()

    private var connection: AcpConnection? = null
    private var currentJob: Job? = null

    /**
     * Connect to an ACP agent with the given configuration.
     */
    fun connect(config: AcpAgentConfig) {
        if (isConnecting || isConnected) return
        currentConfig = config

        isConnecting = true
        connectionError = null

        _timeline.add(
            AcpTimelineItem.SystemMessage(
                "Connecting to ${config.name.ifBlank { config.command }}..."
            )
        )

        scope.launch {
            try {
                val conn = createAcpConnection()
                if (conn == null) {
                    connectionError = "ACP agents are not supported on this platform"
                    isConnecting = false
                    _timeline.add(AcpTimelineItem.ErrorMessage("ACP agents are not supported on this platform"))
                    return@launch
                }

                val callbacks = AcpSessionCallbacks(
                    onTextChunk = { text ->
                        handleTextChunk(text)
                    },
                    onThoughtChunk = { text ->
                        handleThoughtChunk(text)
                    },
                    onToolCall = { title, status, input, output ->
                        handleToolCall(title, status, input, output)
                    },
                    onPlanUpdate = { entries ->
                        handlePlanUpdate(entries)
                    },
                    onError = { message ->
                        handleError(message)
                    },
                    onComplete = { stopReason ->
                        handleComplete(stopReason)
                    }
                )

                conn.connect(config, projectPath, callbacks)
                connection = conn
                isConnected = true
                isConnecting = false

                _timeline.add(
                    AcpTimelineItem.SystemMessage(
                        "Connected to ${config.name.ifBlank { config.command }}. " +
                            "All interaction now goes through the external agent."
                    )
                )
            } catch (e: Exception) {
                connectionError = e.message ?: "Connection failed"
                isConnecting = false
                _timeline.add(AcpTimelineItem.ErrorMessage("Connection failed: ${e.message}"))
            }
        }
    }

    /**
     * Send a prompt to the connected ACP agent.
     */
    fun sendPrompt(text: String) {
        if (!isConnected || isExecuting) return

        // Add user message
        _timeline.add(AcpTimelineItem.UserMessage(text))

        isExecuting = true
        currentStreamingContent.clear()
        currentThinkingContent.clear()

        currentJob = scope.launch {
            try {
                connection?.prompt(text)
            } catch (e: CancellationException) {
                // Cancelled by user
                finalizeStreaming()
                _timeline.add(AcpTimelineItem.SystemMessage("Prompt cancelled"))
            } catch (e: Exception) {
                finalizeStreaming()
                _timeline.add(AcpTimelineItem.ErrorMessage("Prompt failed: ${e.message}"))
            } finally {
                isExecuting = false
            }
        }
    }

    /**
     * Cancel the current prompt.
     */
    fun cancelPrompt() {
        currentJob?.cancel()
        scope.launch {
            try {
                connection?.cancel()
            } catch (_: Exception) {}
        }
    }

    /**
     * Disconnect from the agent.
     */
    fun disconnect() {
        scope.launch {
            try {
                connection?.disconnect()
            } catch (_: Exception) {}
            connection = null
            isConnected = false
            isConnecting = false
            _timeline.add(AcpTimelineItem.SystemMessage("Disconnected from agent"))
        }
    }

    /**
     * Clear the timeline and start fresh.
     */
    fun clearTimeline() {
        _timeline.clear()
    }

    /**
     * Clean up resources.
     */
    fun dispose() {
        currentJob?.cancel()
        scope.launch {
            try { connection?.disconnect() } catch (_: Exception) {}
        }
        scope.cancel()
    }

    // --- Private handlers for ACP callbacks ---

    private fun handleTextChunk(text: String) {
        currentStreamingContent.append(text)

        // Update or add the streaming message
        val lastItem = _timeline.lastOrNull()
        if (lastItem is AcpTimelineItem.AgentMessage && lastItem.isStreaming) {
            _timeline[_timeline.size - 1] = lastItem.copy(
                content = currentStreamingContent.toString()
            )
        } else {
            // Finalize any active thinking block first
            finalizeThinking()
            _timeline.add(
                AcpTimelineItem.AgentMessage(
                    content = currentStreamingContent.toString(),
                    isStreaming = true
                )
            )
        }
    }

    private fun handleThoughtChunk(text: String) {
        currentThinkingContent.append(text)

        val lastItem = _timeline.lastOrNull()
        if (lastItem is AcpTimelineItem.ThinkingBlock && lastItem.isActive) {
            _timeline[_timeline.size - 1] = lastItem.copy(
                content = currentThinkingContent.toString()
            )
        } else {
            _timeline.add(
                AcpTimelineItem.ThinkingBlock(
                    content = currentThinkingContent.toString(),
                    isActive = true
                )
            )
        }
    }

    private fun handleToolCall(title: String, status: String, input: String?, output: String?) {
        // Finalize any active streaming
        finalizeStreaming()
        finalizeThinking()

        _timeline.add(
            AcpTimelineItem.ToolCall(
                title = title,
                status = status,
                input = input,
                output = output
            )
        )
    }

    private fun handlePlanUpdate(entries: List<PlanEntry>) {
        // Update or add plan block
        val lastPlanIndex = _timeline.indexOfLast { it is AcpTimelineItem.PlanBlock }
        if (lastPlanIndex >= 0) {
            _timeline[lastPlanIndex] = AcpTimelineItem.PlanBlock(entries)
        } else {
            _timeline.add(AcpTimelineItem.PlanBlock(entries))
        }
    }

    private fun handleError(message: String) {
        finalizeStreaming()
        finalizeThinking()
        _timeline.add(AcpTimelineItem.ErrorMessage(message))
    }

    private fun handleComplete(stopReason: String) {
        finalizeStreaming()
        finalizeThinking()
    }

    private fun finalizeStreaming() {
        if (currentStreamingContent.isNotEmpty()) {
            val lastItem = _timeline.lastOrNull()
            if (lastItem is AcpTimelineItem.AgentMessage && lastItem.isStreaming) {
                _timeline[_timeline.size - 1] = lastItem.copy(isStreaming = false)
            }
            currentStreamingContent.clear()
        }
    }

    private fun finalizeThinking() {
        if (currentThinkingContent.isNotEmpty()) {
            val lastItem = _timeline.lastOrNull()
            if (lastItem is AcpTimelineItem.ThinkingBlock && lastItem.isActive) {
                _timeline[_timeline.size - 1] = lastItem.copy(isActive = false)
            }
            currentThinkingContent.clear()
        }
    }
}
