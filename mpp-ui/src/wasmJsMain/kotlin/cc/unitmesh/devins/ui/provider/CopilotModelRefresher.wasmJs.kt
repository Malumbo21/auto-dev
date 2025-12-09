package cc.unitmesh.devins.ui.provider

import cc.unitmesh.llm.NamedModelConfig

/**
 * WASM implementation - GitHub Copilot not supported in browser
 */
actual object CopilotModelRefresher {
    actual fun isAvailable(): Boolean = false
    actual suspend fun refreshModels(): List<NamedModelConfig> = emptyList()
}

