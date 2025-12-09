package cc.unitmesh.devins.ui.provider

import cc.unitmesh.llm.NamedModelConfig

/**
 * JS implementation - GitHub Copilot not supported in browser/Node.js CLI
 * (Would need to implement Node.js file system access for CLI mode)
 */
actual object CopilotModelRefresher {
    actual fun isAvailable(): Boolean = false
    actual suspend fun refreshModels(): List<NamedModelConfig> = emptyList()
}

