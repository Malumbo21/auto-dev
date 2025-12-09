package cc.unitmesh.llm

import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.*
import cc.unitmesh.llm.clients.CustomOpenAILLMClient
import cc.unitmesh.llm.provider.LLMClientRegistry

/**
 * Executor 工厂 - 负责根据配置创建合适的 LLM Executor
 * 职责：
 * 1. 根据 Provider 类型创建对应的 Executor
 * 2. 处理不同 Provider 的初始化逻辑
 * 3. 统一 Executor 创建接口
 * 4. 支持通过 LLMClientRegistry 注册的扩展 Provider
 * 
 * For extensible providers (like GitHub Copilot on JVM), register them via:
 * ```kotlin
 * LLMClientRegistry.register(GithubCopilotClientProvider())
 * ```
 */
object ExecutorFactory {
    
    /**
     * 根据模型配置创建 Executor
     * 
     * Note: For providers that require async initialization (like GitHub Copilot),
     * use [createAsync] instead.
     */
    fun create(config: ModelConfig): SingleLLMPromptExecutor {
        return when (config.provider) {
            LLMProviderType.OPENAI -> createOpenAI(config)
            LLMProviderType.ANTHROPIC -> createAnthropic(config)
            LLMProviderType.GOOGLE -> createGoogle(config)
            LLMProviderType.DEEPSEEK -> createDeepSeek(config)
            LLMProviderType.OLLAMA -> createOllama(config)
            LLMProviderType.OPENROUTER -> createOpenRouter(config)
            LLMProviderType.GLM -> createGLM(config)
            LLMProviderType.QWEN -> createQwen(config)
            LLMProviderType.KIMI -> createKimi(config)
            LLMProviderType.GITHUB_COPILOT -> throw IllegalStateException(
                "GitHub Copilot requires async initialization. Use createAsync() instead, " +
                "or ensure GithubCopilotClientProvider is registered via LLMClientRegistry on JVM."
            )
            LLMProviderType.CUSTOM_OPENAI_BASE -> createCustomOpenAI(config)
        }
    }
    
    /**
     * 根据模型配置异步创建 Executor
     * 
     * This method supports providers that require async initialization,
     * such as GitHub Copilot which needs to fetch API tokens.
     * 
     * @param config Model configuration
     * @return SingleLLMPromptExecutor or throws if creation fails
     */
    suspend fun createAsync(config: ModelConfig): SingleLLMPromptExecutor {
        // First, check if there's a registered provider in the registry
        val registryExecutor = LLMClientRegistry.createExecutor(config)
        if (registryExecutor != null) {
            return registryExecutor
        }
        
        // Fall back to built-in providers
        return when (config.provider) {
            LLMProviderType.GITHUB_COPILOT -> throw IllegalStateException(
                "GitHub Copilot provider is not available. " +
                "Make sure to register GithubCopilotClientProvider on JVM platform: " +
                "LLMClientRegistry.register(GithubCopilotClientProvider())"
            )
            else -> create(config)
        }
    }
    
    /**
     * Check if a provider is available (either built-in or registered)
     */
    fun isProviderAvailable(providerType: LLMProviderType): Boolean {
        // Check registry first for extensible providers
        if (LLMClientRegistry.isProviderAvailable(providerType)) {
            return true
        }
        
        // Built-in providers are always available (except GITHUB_COPILOT which requires registration)
        return providerType != LLMProviderType.GITHUB_COPILOT
    }

    private fun createOpenAI(config: ModelConfig): SingleLLMPromptExecutor {
        return simpleOpenAIExecutor(config.apiKey)
    }

    private fun createAnthropic(config: ModelConfig): SingleLLMPromptExecutor {
        return simpleAnthropicExecutor(config.apiKey)
    }

    private fun createGoogle(config: ModelConfig): SingleLLMPromptExecutor {
        return simpleGoogleAIExecutor(config.apiKey)
    }

    private fun createDeepSeek(config: ModelConfig): SingleLLMPromptExecutor {
        return SingleLLMPromptExecutor(DeepSeekLLMClient(config.apiKey))
    }

    private fun createOllama(config: ModelConfig): SingleLLMPromptExecutor {
        val baseUrl = config.baseUrl.ifEmpty { "http://localhost:11434" }
        return simpleOllamaAIExecutor(baseUrl = baseUrl)
    }

    private fun createOpenRouter(config: ModelConfig): SingleLLMPromptExecutor {
        return simpleOpenRouterExecutor(config.apiKey)
    }

    private fun createGLM(config: ModelConfig): SingleLLMPromptExecutor {
        val baseUrl = config.baseUrl.ifEmpty { ModelRegistry.getDefaultBaseUrl(LLMProviderType.GLM) }
        return SingleLLMPromptExecutor(
            CustomOpenAILLMClient(
                apiKey = config.apiKey,
                baseUrl = baseUrl,
                customHeaders = config.customHeaders
            )
        )
    }

    private fun createQwen(config: ModelConfig): SingleLLMPromptExecutor {
        val baseUrl = config.baseUrl.ifEmpty { ModelRegistry.getDefaultBaseUrl(LLMProviderType.QWEN) }
        return SingleLLMPromptExecutor(
            CustomOpenAILLMClient(
                apiKey = config.apiKey,
                baseUrl = baseUrl,
                customHeaders = config.customHeaders
            )
        )
    }

    private fun createKimi(config: ModelConfig): SingleLLMPromptExecutor {
        val baseUrl = config.baseUrl.ifEmpty { ModelRegistry.getDefaultBaseUrl(LLMProviderType.KIMI) }
        return SingleLLMPromptExecutor(
            CustomOpenAILLMClient(
                apiKey = config.apiKey,
                baseUrl = baseUrl,
                customHeaders = config.customHeaders
            )
        )
    }

    private fun createCustomOpenAI(config: ModelConfig): SingleLLMPromptExecutor {
        require(config.baseUrl.isNotEmpty()) { "baseUrl is required for custom OpenAI provider" }
        return SingleLLMPromptExecutor(
            CustomOpenAILLMClient(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl,
                customHeaders = config.customHeaders
            )
        )
    }
}
