package cc.unitmesh.devins.ui.provider

import cc.unitmesh.llm.NamedModelConfig

/**
 * Platform-specific GitHub Copilot model refresher.
 * 
 * On JVM/Desktop: Checks for local OAuth token and fetches models from API
 * On other platforms: Returns not available
 */
expect object CopilotModelRefresher {
    /**
     * Check if GitHub Copilot refresh is available on this platform.
     * Returns true only on JVM when local OAuth token exists.
     */
    fun isAvailable(): Boolean
    
    /**
     * Refresh GitHub Copilot models from API and convert to NamedModelConfig list.
     * 
     * @return List of NamedModelConfig for each available Copilot model, or empty if failed
     */
    suspend fun refreshModels(): List<NamedModelConfig>
}

