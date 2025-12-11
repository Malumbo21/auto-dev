package cc.unitmesh.devti.llms

import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devti.gui.chat.message.ChatRole
import cc.unitmesh.devti.llm2.ChatSession
import cc.unitmesh.devti.llm2.GithubCopilotDetector
import cc.unitmesh.devti.llm2.LLMProvider2
import cc.unitmesh.devti.llm2.model.LlmConfig
import cc.unitmesh.devti.llm2.model.ModelType
import cc.unitmesh.devti.llm2.model.toKoogMessages
import cc.unitmesh.devti.llms.custom.Message
import cc.unitmesh.devti.observer.agent.AgentStateService
import cc.unitmesh.devti.prompting.optimizer.PromptOptimizer
import cc.unitmesh.devti.settings.coder.coderSetting
import cc.unitmesh.llm.KoogLLMService
import cc.unitmesh.llm.ModelConfig
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.atomic.AtomicReference

/**
 * Adapter that bridges the old LLMProvider interface with the new LLMProvider2 implementation.
 * This allows gradual migration from LLMProvider to LLMProvider2 while maintaining backward compatibility.
 */
class LLMProviderAdapter(
    private val project: Project,
    private val modelType: ModelType = ModelType.Default
) : LLMProvider {
    private val logger = logger<LLMProviderAdapter>()
    private val agentService = project.getService(AgentStateService::class.java)

    private val messages: MutableList<Message> = mutableListOf()
    private var currentSession: ChatSession<Message> = ChatSession("adapter-session")
    private var currentProvider: LLMProvider2? = null

    override val defaultTimeout: Long get() = 600

    companion object {
        // Cached config loaded asynchronously to avoid blocking EDT
        private val cachedConfig = AtomicReference<ModelConfig?>(null)
        @Volatile
        private var configLoaded = false
        private val configLoadLock = Any()

        /**
         * Preload config asynchronously. Should be called during plugin initialization.
         */
        fun preloadConfig() {
            if (configLoaded) return
            ApplicationManager.getApplication().executeOnPooledThread {
                loadConfigInBackground()
            }
        }

        /**
         * Load config in background thread (non-blocking)
         */
        private fun loadConfigInBackground() {
            synchronized(configLoadLock) {
                if (configLoaded) return
                try {
                    // Use suspendCancellableCoroutine alternative - direct blocking on pooled thread is OK
                    val wrapper = kotlinx.coroutines.runBlocking {
                        ConfigManager.load()
                    }
                    if (wrapper.isValid()) {
                        val activeConfig = wrapper.getActiveModelConfig()
                        if (activeConfig != null && activeConfig.apiKey.isNotEmpty()) {
                            cachedConfig.set(activeConfig)
                        }
                    }
                } catch (e: Exception) {
                    // Config load failed, will use fallback
                } finally {
                    configLoaded = true
                }
            }
        }

        /**
         * Get cached config or null if not loaded yet
         */
        fun getCachedConfig(): ModelConfig? {
            if (!configLoaded) {
                // Trigger background load if not started
                preloadConfig()
            }
            return cachedConfig.get()
        }

        /**
         * Invalidate cached config (call when config changes)
         */
        fun invalidateCache() {
            synchronized(configLoadLock) {
                configLoaded = false
                cachedConfig.set(null)
            }
        }
    }

    override fun stream(
        promptText: String,
        systemPrompt: String,
        keepHistory: Boolean,
        usePlanForFirst: Boolean
    ): Flow<String> {
        logger.info("LLMProviderAdapter.stream called with model type: $modelType")

        if (!keepHistory || project.coderSetting.state.noChatHistory) {
            clearMessage()
            currentSession = ChatSession("adapter-session")
        }

        if (systemPrompt.isNotEmpty()) {
            if (messages.isNotEmpty() && messages[0].role != "system") {
                messages.add(0, Message("system", systemPrompt))
            } else if (messages.isEmpty()) {
                messages.add(Message("system", systemPrompt))
            } else {
                messages[0] = Message("system", systemPrompt)
            }
        }

        // Process prompt optimization
        val prompt = if (project.coderSetting.state.trimCodeBeforeSend) {
            PromptOptimizer.trimCodeSpace(promptText)
        } else {
            promptText
        }

        messages.add(Message("user", prompt))
        val finalMsgs = agentService.processMessages(messages)
        currentSession = ChatSession("adapter-session", finalMsgs)

        // Priority 1: Try to get ModelConfig from ConfigManager (~/.autodev/config.yaml)
        val configManagerConfig = tryLoadFromConfigManager()
        if (configManagerConfig != null) {
            logger.info("Using config from ConfigManager: provider=${configManagerConfig.provider}, model=${configManagerConfig.modelName}")
            return streamWithKoogLLMServiceDirect(configManagerConfig, prompt, keepHistory)
        }

        // Priority 2: Use LlmConfig from IDEA settings (legacy)
        val actualLlmConfig = if (usePlanForFirst && shouldUsePlanModel()) {
            LlmConfig.forCategory(ModelType.Plan)
        } else {
            when (modelType) {
                ModelType.Default -> LlmConfig.default()
                else -> LlmConfig.forCategory(modelType)
            }
        }

        // Priority 3: Check if it's a GitHub Copilot config
        if (actualLlmConfig.isGithubCopilot()) {
            return streamWithLLMProvider2(actualLlmConfig, prompt)
        }

        // Priority 4: If no API key but model is a known GitHub model, use GitHub Copilot
        if (actualLlmConfig.auth.token.isEmpty()) {
            val modelName = actualLlmConfig.getModelName()
            if (isKnownGithubModel(modelName) && GithubCopilotDetector.isGithubCopilotConfigured()) {
                logger.info("No API key configured, falling back to GitHub Copilot for model: $modelName")
                val githubConfig = LlmConfig.createGithubConfig(modelName)
                return streamWithLLMProvider2(githubConfig, prompt)
            }

            // No valid config found, return helpful error
            logger.warn("No valid LLM configuration found. Please configure in Settings > AutoDev or ~/.autodev/config.yaml")
            return flowOf("Error: No LLM configuration found. Please configure your API key in:\n" +
                "1. Settings > AutoDev > LLM Settings, or\n" +
                "2. ~/.autodev/config.yaml\n\n" +
                "If you have GitHub Copilot installed, you can also use GitHub Copilot models.")
        }

        return streamWithKoogLLMService(actualLlmConfig, prompt, keepHistory)
    }

    /**
     * Try to load ModelConfig from ConfigManager (~/.autodev/config.yaml)
     * Uses cached config to avoid blocking EDT.
     */
    private fun tryLoadFromConfigManager(): ModelConfig? {
        val config = getCachedConfig()
        if (config != null) {
            logger.info("Using cached config from ConfigManager")
        }
        return config
    }

    /**
     * Check if a model name is a known GitHub Copilot model
     */
    private fun isKnownGithubModel(modelName: String): Boolean {
        return modelName.startsWith("gpt-") ||
               modelName.startsWith("claude-") ||
               modelName.startsWith("o1-") ||
               modelName.startsWith("o3-") ||
               modelName.contains("github")
    }

    /**
     * Stream using KoogLLMService with ModelConfig directly (preferred)
     */
    private fun streamWithKoogLLMServiceDirect(
        modelConfig: ModelConfig,
        prompt: String,
        keepHistory: Boolean
    ): Flow<String> {
        return try {
            logger.info("Using KoogLLMService directly: provider=${modelConfig.provider}, model=${modelConfig.modelName}")
            val koogService = KoogLLMService.create(modelConfig)

            // Convert IDEA messages to Koog messages (excluding the last user message as it's passed separately)
            val historyMessages = messages.dropLast(1).toKoogMessages()

            flow {
                var fullResponse = ""

                koogService.streamPrompt(
                    userPrompt = prompt,
                    historyMessages = historyMessages,
                    compileDevIns = false // DevIns compilation is handled elsewhere in IDEA
                ).collect { chunk ->
                    fullResponse += chunk
                    emit(chunk)
                }

                // Update message history with the complete response
                if (fullResponse.isNotEmpty()) {
                    messages.add(Message("assistant", fullResponse))
                }
            }
        } catch (e: Exception) {
            logger.error("Error in KoogLLMService stream", e)
            flowOf("Error: ${e.message}")
        }.also {
            if (!keepHistory || project.coderSetting.state.noChatHistory) {
                clearMessage()
            }
        }
    }

    /**
     * Stream using KoogLLMService for non-GitHub Copilot models (legacy LlmConfig)
     */
    private fun streamWithKoogLLMService(
        llmConfig: LlmConfig,
        prompt: String,
        keepHistory: Boolean
    ): Flow<String> {
        return try {
            logger.info("Using KoogLLMService for model: ${llmConfig.name}, url: ${llmConfig.url}")
            val modelConfig = llmConfig.toModelConfig()
            logger.info("ModelConfig created: provider=${modelConfig.provider}, model=${modelConfig.modelName}, hasApiKey=${modelConfig.apiKey.isNotEmpty()}")
            
            // Validate config before creating service
            if (!modelConfig.isValid()) {
                val errorMsg = "Invalid model configuration: provider=${modelConfig.provider}, " +
                    "hasApiKey=${modelConfig.apiKey.isNotEmpty()}, hasModelName=${modelConfig.modelName.isNotEmpty()}"
                logger.warn(errorMsg)
                return flowOf("Error: $errorMsg\nPlease configure your LLM settings in Settings > AutoDev or ~/.autodev/config.yaml")
            }
            
            val koogService = KoogLLMService.create(modelConfig)

            // Convert IDEA messages to Koog messages (excluding the last user message as it's passed separately)
            val historyMessages = messages.dropLast(1).toKoogMessages()

            flow {
                var fullResponse = ""

                koogService.streamPrompt(
                    userPrompt = prompt,
                    historyMessages = historyMessages,
                    compileDevIns = false // DevIns compilation is handled elsewhere in IDEA
                ).collect { chunk ->
                    fullResponse += chunk
                    emit(chunk)
                }

                // Update message history with the complete response
                if (fullResponse.isNotEmpty()) {
                    messages.add(Message("assistant", fullResponse))
                }
            }
        } catch (e: Exception) {
            logger.error("Error in KoogLLMService stream", e)
            flowOf("Error: ${e.message}")
        }.also {
            if (!keepHistory || project.coderSetting.state.noChatHistory) {
                clearMessage()
            }
        }
    }

    /**
     * Stream using LLMProvider2 for GitHub Copilot models
     */
    private fun streamWithLLMProvider2(
        llmConfig: LlmConfig,
        prompt: String
    ): Flow<String> {
        val actualProvider = LLMProvider2.fromConfig(llmConfig, project)
        currentProvider = actualProvider

        return try {
            flow {
                var fullResponse = ""
                var lastEmittedLength = 0

                actualProvider.request(
                    text = Message("user", prompt),
                    stream = true,
                    session = currentSession
                ).collect { sessionItem ->
                    val content = sessionItem.chatMessage.content
                    fullResponse = content

                    // Emit only the new part of the content (incremental)
                    if (fullResponse.length > lastEmittedLength) {
                        val newContent = fullResponse.substring(lastEmittedLength)
                        if (newContent.isNotEmpty()) {
                            emit(newContent)
                            lastEmittedLength = fullResponse.length
                        }
                    }
                }

                // Update message history with the complete response
                if (fullResponse.isNotEmpty()) {
                    messages.add(Message("assistant", fullResponse))
                }
            }
        } catch (e: Exception) {
            logger.error("Error in LLMProvider2 stream", e)
            flowOf("Error: ${e.message}")
        }
    }

    private fun shouldUsePlanModel(): Boolean {
        val canBePlanLength = 3 // System + User + Assistant
        return messages.size == canBePlanLength && LlmConfig.hasPlanModel()
    }

    override fun clearMessage() {
        messages.clear()
        currentSession = ChatSession("adapter-session")
    }

    /**
     * Cancels the current LLM request synchronously without waiting for completion.
     * This is useful for immediately stopping streaming responses from the LLM.
     */
    fun cancelCurrentRequestSync() {
        logger.info("Cancelling current LLM request synchronously")
        currentProvider?.cancelCurrentRequestSync() ?: run {
            logger.warn("No active LLM provider to cancel")
        }
    }

    override fun getAllMessages(): List<Message> {
        return messages.toList()
    }

    override fun appendLocalMessage(msg: String, role: ChatRole) {
        if (msg.isEmpty()) return
        messages.add(Message(role.roleName(), msg))

        // Update current session
        currentSession = ChatSession("adapter-session", messages)
    }
}
