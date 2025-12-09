package cc.unitmesh.llm.provider

import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import cc.unitmesh.llm.LLMProviderType
import cc.unitmesh.llm.ModelConfig
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Registry for extensible LLM client providers.
 * 
 * Allows platform-specific providers (like GitHub Copilot on JVM) to be 
 * registered without requiring cross-platform expect/actual implementations.
 * 
 * Usage:
 * ```kotlin
 * // Register a provider (typically at app initialization)
 * LLMClientRegistry.register(MyCustomProvider())
 * 
 * // Check if a provider is available
 * val isAvailable = LLMClientRegistry.isProviderAvailable(LLMProviderType.GITHUB_COPILOT)
 * 
 * // Create an executor
 * val executor = LLMClientRegistry.createExecutor(config)
 * ```
 */
object LLMClientRegistry {
    private val providers = mutableMapOf<LLMProviderType, LLMClientProvider>()
    
    /**
     * Register a provider for a specific type
     * 
     * @param provider The provider to register
     */
    fun register(provider: LLMClientProvider) {
        if (provider.isAvailable()) {
            providers[provider.providerType] = provider
            logger.info { "Registered LLM provider: ${provider.providerType.displayName}" }
        } else {
            logger.debug { "LLM provider ${provider.providerType.displayName} is not available on this platform" }
        }
    }
    
    /**
     * Unregister a provider
     * 
     * @param providerType The provider type to unregister
     */
    fun unregister(providerType: LLMProviderType) {
        providers.remove(providerType)
    }
    
    /**
     * Check if a provider is registered and available
     * 
     * @param providerType The provider type to check
     * @return true if provider is registered and available
     */
    fun isProviderAvailable(providerType: LLMProviderType): Boolean {
        return providers[providerType]?.isAvailable() == true
    }
    
    /**
     * Get a registered provider
     * 
     * @param providerType The provider type
     * @return The provider or null if not registered
     */
    fun getProvider(providerType: LLMProviderType): LLMClientProvider? {
        return providers[providerType]
    }
    
    /**
     * Create an executor using a registered provider
     * 
     * @param config The model configuration
     * @return SingleLLMPromptExecutor or null if provider not found or creation fails
     */
    suspend fun createExecutor(config: ModelConfig): SingleLLMPromptExecutor? {
        val provider = providers[config.provider]
        if (provider == null) {
            logger.debug { "No registered provider for ${config.provider.displayName}" }
            return null
        }
        
        return provider.createExecutor(config)
    }
    
    /**
     * Get all registered provider types
     * 
     * @return Set of registered provider types
     */
    fun getRegisteredProviderTypes(): Set<LLMProviderType> {
        return providers.keys.toSet()
    }
    
    /**
     * Get cached available models for a provider (synchronous)
     * 
     * @param providerType The provider type
     * @return List of model names or empty if not available
     */
    fun getAvailableModels(providerType: LLMProviderType): List<String> {
        return providers[providerType]?.getAvailableModels() ?: emptyList()
    }
    
    /**
     * Fetch available models from provider API (asynchronous)
     * 
     * This method fetches fresh model list from the provider's API.
     * 
     * @param providerType The provider type
     * @param forceRefresh Force refresh even if cache is valid
     * @return List of model names or empty if not available
     */
    suspend fun fetchAvailableModelsAsync(providerType: LLMProviderType, forceRefresh: Boolean = false): List<String> {
        val provider = providers[providerType]
        if (provider == null) {
            logger.debug { "No registered provider for ${providerType.displayName}" }
            return emptyList()
        }
        return provider.fetchAvailableModelsAsync(forceRefresh)
    }
    
    /**
     * Clear all registered providers (useful for testing)
     */
    fun clear() {
        providers.clear()
    }
}

