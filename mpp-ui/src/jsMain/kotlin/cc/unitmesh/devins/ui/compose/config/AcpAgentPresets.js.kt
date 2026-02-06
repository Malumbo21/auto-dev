package cc.unitmesh.devins.ui.compose.config

/**
 * JS/Browser: ACP agents not supported (no subprocess spawn).
 */
actual fun detectInstalledPresets(): List<AcpAgentPreset> = emptyList()
