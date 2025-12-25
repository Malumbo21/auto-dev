package cc.unitmesh.devins.ui.compose.agent.artifact

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.unitmesh.agent.ArtifactAgent
import cc.unitmesh.agent.artifact.ArtifactBundle
import cc.unitmesh.agent.artifact.ArtifactType
import cc.unitmesh.devins.llm.ChatHistoryManager
import cc.unitmesh.devins.llm.Message
import cc.unitmesh.devins.llm.MessageRole
import cc.unitmesh.devins.ui.compose.agent.ComposeRenderer
import cc.unitmesh.devins.ui.i18n.LanguageManager
import cc.unitmesh.llm.KoogLLMService
import kotlinx.coroutines.*

/**
 * ViewModel for ArtifactAgent, following the same pattern as CodingAgentViewModel.
 * Manages the artifact generation lifecycle and state.
 *
 * Supports streaming preview - artifact content is rendered in real-time as it's generated.
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

    // Completed artifact (after generation finishes)
    var lastArtifact by mutableStateOf<ArtifactAgent.Artifact?>(null)
        private set

    // Streaming artifact state - updated in real-time during generation
    var streamingArtifact by mutableStateOf<StreamingArtifact?>(null)
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
     * Execute artifact generation task with streaming preview support
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
        streamingArtifact = null // Reset streaming state

        currentExecutionJob = scope.launch {
            val contentBuilder = StringBuilder()

            try {
                val result = agent.generate(task) { chunk ->
                    contentBuilder.append(chunk)
                    // Parse and update streaming artifact in real-time
                    updateStreamingArtifact(contentBuilder.toString())
                }

                // Generation complete - set final artifact
                if (result.success && result.artifacts.isNotEmpty()) {
                    lastArtifact = result.artifacts.first()
                    streamingArtifact = null // Clear streaming state
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
                streamingArtifact = null
                isExecuting = false
                currentExecutionJob = null
            } catch (e: Exception) {
                renderer.renderError(e.message ?: "Unknown error")
                streamingArtifact = null
                isExecuting = false
                currentExecutionJob = null
            } finally {
                saveConversationHistory()
            }
        }
    }

    /**
     * Parse streaming content and update artifact preview in real-time
     */
    private fun updateStreamingArtifact(content: String) {
        // Look for artifact tag opening
        val artifactStartPattern = Regex(
            """<autodev-artifact\s+([^>]*)>""",
            RegexOption.IGNORE_CASE
        )

        val startMatch = artifactStartPattern.find(content) ?: return

        // Extract attributes
        val attributesStr = startMatch.groupValues[1]
        val identifier = extractAttribute(attributesStr, "identifier") ?: "streaming-artifact"
        val typeStr = extractAttribute(attributesStr, "type") ?: "application/autodev.artifacts.html"
        val title = extractAttribute(attributesStr, "title") ?: "Generating..."

        // Extract content after the opening tag
        val contentStartIndex = startMatch.range.last + 1
        val closingTagIndex = content.indexOf("</autodev-artifact>", contentStartIndex)

        val artifactContent = if (closingTagIndex > 0) {
            // Complete artifact
            content.substring(contentStartIndex, closingTagIndex).trim()
        } else {
            // Still streaming - get partial content
            content.substring(contentStartIndex).trim()
        }

        val isComplete = closingTagIndex > 0

        streamingArtifact = StreamingArtifact(
            identifier = identifier,
            type = typeStr,
            title = title,
            content = artifactContent,
            isComplete = isComplete
        )
    }

    private fun extractAttribute(attributesStr: String, name: String): String? {
        val pattern = Regex("""$name\s*=\s*["']([^"']+)["']""")
        return pattern.find(attributesStr)?.groupValues?.get(1)
    }

    /**
     * Cancel current task
     */
    fun cancelTask() {
        if (isExecuting && currentExecutionJob != null) {
            currentExecutionJob?.cancel("Task cancelled by user")
            currentExecutionJob = null
            streamingArtifact = null
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
        streamingArtifact = null
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

    /**
     * Load state from an ArtifactBundle (for load-back support)
     * This restores the conversation history and artifact preview from a .unit file
     */
    fun loadFromBundle(bundle: ArtifactBundle) {
        // Clear current state
        renderer.clearMessages()
        streamingArtifact = null

        // Restore conversation history from context
        val conversationHistory = bundle.context.conversationHistory
        if (conversationHistory.isNotEmpty()) {
            // Convert stored conversation messages to renderer messages
            conversationHistory.forEach { msg ->
                when (msg.role.lowercase()) {
                    "user" -> renderer.addUserMessage(msg.content)
                    "assistant" -> {
                        renderer.renderLLMResponseStart()
                        renderer.renderLLMResponseChunk(msg.content)
                        renderer.renderLLMResponseEnd()
                    }
                }
            }
        } else {
            // If no conversation history, add a context message
            renderer.addUserMessage("[Loaded from bundle: ${bundle.name}]")
        }

        // Convert bundle to artifact for preview
        val artifactType = when (bundle.type) {
            ArtifactType.HTML -> ArtifactAgent.Artifact.ArtifactType.HTML
            ArtifactType.REACT -> ArtifactAgent.Artifact.ArtifactType.REACT
            ArtifactType.NODEJS -> ArtifactAgent.Artifact.ArtifactType.NODEJS
            ArtifactType.PYTHON -> ArtifactAgent.Artifact.ArtifactType.PYTHON
            ArtifactType.SVG -> ArtifactAgent.Artifact.ArtifactType.SVG
            ArtifactType.MERMAID -> ArtifactAgent.Artifact.ArtifactType.MERMAID
        }

        lastArtifact = ArtifactAgent.Artifact(
            identifier = bundle.id,
            type = artifactType,
            title = bundle.name,
            content = bundle.mainContent
        )
        cc.unitmesh.agent.logging.AutoDevLogger.info("ArtifactAgentViewModel") { "📦 loadFromBundle: lastArtifact set to ${lastArtifact?.title}, content length=${bundle.mainContent.length}" }
        cc.unitmesh.agent.logging.AutoDevLogger.info("ArtifactAgentViewModel") { "📦 loadFromBundle: renderer timeline size=${renderer.getTimelineSnapshot().size}" }
    }

    /**
     * Get current artifact bundle for export (includes conversation history)
     */
    fun createBundleForExport(artifact: ArtifactAgent.Artifact): ArtifactBundle {
        // Collect conversation history from renderer
        val timelineMessages = renderer.getTimelineSnapshot()
        val conversationHistory = timelineMessages.map { msg ->
            cc.unitmesh.agent.artifact.ConversationMessage(
                role = msg.role.name.lowercase(),
                content = msg.content
            )
        }

        return ArtifactBundle.fromArtifact(
            artifact = artifact,
            conversationHistory = conversationHistory,
            modelInfo = llmService?.let {
                cc.unitmesh.agent.artifact.ModelInfo(
                    name = "unknown", // TODO: Get from LLM service
                    provider = "unknown"
                )
            }
        )
    }
}

/**
 * Represents an artifact that is currently being streamed/generated.
 * Used for real-time preview during generation.
 */
data class StreamingArtifact(
    val identifier: String,
    val type: String,
    val title: String,
    val content: String,
    val isComplete: Boolean
)

