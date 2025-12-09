package cc.unitmesh.devins.ui.provider

import cc.unitmesh.llm.NamedModelConfig

/**
 * iOS implementation - GitHub Copilot not supported on mobile
 */
actual object CopilotModelRefresher {
    actual fun isAvailable(): Boolean = false
    actual suspend fun refreshModels(): List<NamedModelConfig> = emptyList()
}

