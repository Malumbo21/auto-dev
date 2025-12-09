package cc.unitmesh.llm.provider

import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import cc.unitmesh.llm.LLMProviderType
import cc.unitmesh.llm.ModelConfig

/**
 * Interface for extensible LLM client providers.
 * 
 * Allows platform-specific or third-party LLM integrations to be registered
 * and used without modifying the core ExecutorFactory.
 * 
 * Example usage:
 * ```kotlin
 * // In jvmMain, register GitHub Copilot provider
 * LLMClientRegistry.register(GithubCopilotClientProvider())
 * ```
 */
interface LLMClientProvider {
    /**
     * The provider type this provider handles
     */
    val providerType: LLMProviderType
    
    /**
     * Check if this provider is available on the current platform
     * 
     * @return true if the provider can be used
     */
    fun isAvailable(): Boolean
    
    /**
     * Create an executor for the given model config
     * 
     * @param config The model configuration
     * @return SingleLLMPromptExecutor or null if creation fails
     */
    suspend fun createExecutor(config: ModelConfig): SingleLLMPromptExecutor?
    
    /**
     * Get cached available models for this provider (synchronous)
     * 
     * For providers that need to fetch models from API, this returns cached models.
     * Use [fetchAvailableModelsAsync] to refresh the cache.
     * 
     * @return List of model IDs, empty if not available or not yet fetched
     */
    fun getAvailableModels(): List<String> = emptyList()
    
    /**
     * Fetch available models from the provider API (asynchronous)
     * 
     * This method fetches fresh model list from the API and updates the cache.
     * For providers that don't support dynamic model discovery, this returns
     * the same as [getAvailableModels].
     * 
     * @param forceRefresh Force refresh even if cache is valid
     * @return List of model IDs
     */
    suspend fun fetchAvailableModelsAsync(forceRefresh: Boolean = false): List<String> = getAvailableModels()
    
    /**
     * Get the default base URL for this provider
     * 
     * @return Base URL or empty string if not applicable
     */
    fun getDefaultBaseUrl(): String = ""
}

