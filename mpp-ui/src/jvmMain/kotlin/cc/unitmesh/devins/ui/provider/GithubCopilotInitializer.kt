package cc.unitmesh.devins.ui.provider

import cc.unitmesh.llm.LLMProviderType
import cc.unitmesh.llm.ModelConfig
import cc.unitmesh.llm.NamedModelConfig
import cc.unitmesh.llm.provider.GithubCopilotClientProvider
import cc.unitmesh.llm.provider.LLMClientRegistry

/**
 * JVM implementation of CopilotModelRefresher.
 * 
 * Checks for local OAuth token and fetches models from GitHub Copilot API.
 */
actual object CopilotModelRefresher {
    private var provider: GithubCopilotClientProvider? = null
    
    /**
     * Check if GitHub Copilot refresh is available.
     * Returns true only when local OAuth token exists.
     */
    actual fun isAvailable(): Boolean {
        if (provider == null) {
            provider = GithubCopilotClientProvider()
        }
        return provider?.isAvailable() == true
    }
    
    /**
     * Refresh GitHub Copilot models from API and convert to NamedModelConfig list.
     */
    actual suspend fun refreshModels(): List<NamedModelConfig> {
        val copilotProvider = provider ?: GithubCopilotClientProvider().also { provider = it }
        
        if (!copilotProvider.isAvailable()) {
            println("GitHub Copilot is not configured")
            return emptyList()
        }
        
        // Register provider if not already
        LLMClientRegistry.register(copilotProvider)
        
        println("Fetching GitHub Copilot models...")
        
        // Fetch models with full metadata
        val copilotModels = copilotProvider.fetchCopilotModelsAsync(forceRefresh = true)
        
        if (copilotModels.isNullOrEmpty()) {
            println("No models returned from GitHub Copilot API")
            return emptyList()
        }
        
        // Filter out embedding models and convert to NamedModelConfig
        val configs = copilotModels
            .filter { !it.isEmbedding && it.isEnabled }
            .map { model ->
                NamedModelConfig(
                    name = "copilot-${model.id}",
                    provider = "github-copilot",
                    apiKey = "", // Not needed, uses local OAuth token
                    model = model.id,
                    baseUrl = "",
                    temperature = 0.0,
                    maxTokens = model.getMaxOutputTokens()?.toInt() ?: 4096
                )
            }
        
        println("Created ${configs.size} GitHub Copilot configurations")
        return configs
    }
}

