package cc.unitmesh.devins.ui.compose.agent.artifact

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.unitmesh.agent.ArtifactAgent
import cc.unitmesh.devins.llm.ChatHistoryManager
import cc.unitmesh.devins.llm.MessageRole
import cc.unitmesh.devins.ui.compose.agent.ComposeRenderer
import cc.unitmesh.devins.ui.i18n.LanguageManager
import cc.unitmesh.llm.KoogLLMService
import kotlinx.coroutines.*

/**
 * ViewModel for ArtifactAgent, following the same pattern as CodingAgentViewModel.
 * Manages the artifact generation lifecycle and state.
 */
class ArtifactAgentViewModel(
    private val llmService: KoogLLMService?,
    private val chatHistoryManager: ChatHistoryManager? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Use ComposeRenderer for consistent UI rendering (same as CodingAgentPage)
    val renderer = ComposeRenderer()

    var isExecuting by mutableStateOf(false)
        private set

    var lastArtifact by mutableStateOf<ArtifactAgent.Artifact?>(null)
        private set

    private var currentExecutionJob: Job? = null
    private var _artifactAgent: ArtifactAgent? = null

    init {
        // Load historical messages from chatHistoryManager
        chatHistoryManager?.let { manager ->
            val messages = manager.getMessages()
            renderer.loadFromMessages(messages)
        }
    }

    /**
     * Initialize or get the ArtifactAgent
     */
    private fun getArtifactAgent(): ArtifactAgent? {
        if (llmService == null) return null

        if (_artifactAgent == null) {
            val language = LanguageManager.getLanguage().code.uppercase()
            _artifactAgent = ArtifactAgent(
                llmService = llmService,
                renderer = renderer,
                language = language
            )
        }
        return _artifactAgent
    }

    /**
     * Execute artifact generation task
     */
    fun executeTask(task: String) {
        if (isExecuting) return

        if (llmService == null) {
            renderer.addUserMessage(task)
            renderer.renderError("WARNING: LLM model is not configured. Please configure your model to continue.")
            return
        }

        val agent = getArtifactAgent() ?: return

        isExecuting = true
        renderer.clearError()
        renderer.addUserMessage(task)

        currentExecutionJob = scope.launch {
            try {
                val result = agent.generate(task) { chunk ->
                    // Progress is handled by renderer via renderLLMResponseChunk
                }

                if (result.success && result.artifacts.isNotEmpty()) {
                    lastArtifact = result.artifacts.first()
                } else {
                    result.error?.let { errorMsg ->
                        renderer.renderError(errorMsg)
                    }
                }

                isExecuting = false
                currentExecutionJob = null
            } catch (e: CancellationException) {
                renderer.forceStop()
                renderer.renderError("Task cancelled by user")
                isExecuting = false
                currentExecutionJob = null
            } catch (e: Exception) {
                renderer.renderError(e.message ?: "Unknown error")
                isExecuting = false
                currentExecutionJob = null
            } finally {
                saveConversationHistory()
            }
        }
    }

    /**
     * Cancel current task
     */
    fun cancelTask() {
        if (isExecuting && currentExecutionJob != null) {
            currentExecutionJob?.cancel("Task cancelled by user")
            currentExecutionJob = null
            isExecuting = false
        }
    }

    /**
     * Clear all messages and reset state
     */
    fun clearMessages() {
        renderer.clearMessages()
        chatHistoryManager?.clearCurrentSession()
        lastArtifact = null
    }

    /**
     * Save conversation history
     */
    private suspend fun saveConversationHistory() {
        chatHistoryManager?.let { manager ->
            try {
                val timelineMessages = renderer.getTimelineSnapshot()
                val existingMessagesCount = manager.getMessages().size
                val newMessages = timelineMessages.drop(existingMessagesCount)

                newMessages.forEach { message ->
                    when (message.role) {
                        MessageRole.USER, MessageRole.ASSISTANT -> {
                            manager.getCurrentSession().messages.add(message)
                        }
                        else -> {}
                    }
                }

                if (newMessages.isNotEmpty()) {
                    manager.getCurrentSession().updatedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                }
            } catch (e: Exception) {
                println("[ERROR] Failed to save conversation history: ${e.message}")
            }
        }
    }

    fun isConfigured(): Boolean = llmService != null
}

