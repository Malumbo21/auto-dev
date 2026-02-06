package cc.unitmesh.devins.ui.compose.config

import cc.unitmesh.config.AcpAgentConfig

/**
 * Well-known ACP agent presets with auto-detection.
 *
 * Provides common ACP-compliant CLI tools (Kimi, Gemini, Claude, Codex)
 * with their standard command-line invocations. Platform-specific `actual`
 * implementations detect which agents are installed on the system.
 */
data class AcpAgentPreset(
    val id: String,
    val name: String,
    val command: String,
    val args: String,
    val env: String = "",
    val description: String
) {
    fun toConfig(): AcpAgentConfig = AcpAgentConfig(name, command, args, env)
}

/**
 * Platform-agnostic ACP agent presets.
 */
object AcpAgentPresets {
    /**
     * All known ACP agent presets (not all may be installed).
     */
    val allPresets = listOf(
        AcpAgentPreset(
            id = "kimi",
            name = "Kimi",
            command = "kimi",
            args = "acp",
            description = "Moonshot AI's Kimi CLI with ACP support"
        ),
        AcpAgentPreset(
            id = "gemini",
            name = "Gemini",
            command = "gemini",
            args = "--experimental-acp",
            description = "Google Gemini CLI (experimental ACP mode)"
        ),
        AcpAgentPreset(
            id = "claude",
            name = "Claude",
            command = "claude",
            args = "--acp",
            description = "Anthropic Claude CLI with ACP support"
        ),
        AcpAgentPreset(
            id = "codex",
            name = "Codex",
            command = "codex",
            args = "--acp",
            description = "OpenAI Codex CLI with ACP support"
        )
    )

    /**
     * Detect which presets are actually installed on this system.
     * Returns a list of presets with resolved absolute paths.
     */
    fun detectInstalled(): List<AcpAgentPreset> = detectInstalledPresets()
}

/**
 * Platform-specific detection of installed ACP agents.
 * Uses `which` on Unix/macOS, `where` on Windows, or checks common paths.
 */
expect fun detectInstalledPresets(): List<AcpAgentPreset>
