package cc.unitmesh.devins.ui.compose.config

/**
 * Android: ACP agents not supported (no subprocess spawn).
 */
actual fun detectInstalledPresets(): List<AcpAgentPreset> = emptyList()
