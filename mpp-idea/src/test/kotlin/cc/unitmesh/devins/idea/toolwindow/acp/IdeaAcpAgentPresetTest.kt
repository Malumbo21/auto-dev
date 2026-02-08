package cc.unitmesh.devins.idea.toolwindow.acp

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for OpenCode ACP agent preset detection and configuration.
 */
class IdeaAcpAgentPresetTest {

    @Test
    fun `should include OpenCode in ALL_PRESETS`() {
        val allPresets = IdeaAcpAgentPreset::class.java
            .getDeclaredField("Companion")
            .get(null)!!::class.java
            .getDeclaredField("ALL_PRESETS")
            .apply { isAccessible = true }
            .get(IdeaAcpAgentPreset.Companion) as List<IdeaAcpAgentPreset>

        val openCodePreset = allPresets.find { it.id == "opencode" }
        assertNotNull("OpenCode preset should be in ALL_PRESETS", openCodePreset)
        
        assertEquals("OpenCode", openCodePreset?.name)
        assertEquals("opencode", openCodePreset?.command)
        assertEquals("acp", openCodePreset?.args)
        assertEquals("OpenCode AI coding agent via ACP", openCodePreset?.description)
    }

    @Test
    fun `should detect OpenCode if installed`() {
        val installedPresets = IdeaAcpAgentPreset.detectInstalled()
        
        // This test will pass if opencode is in PATH
        // If opencode is not installed, the test should still pass but skip the check
        val openCodePreset = installedPresets.find { it.id == "opencode" }
        
        if (isCommandAvailable("opencode")) {
            assertNotNull("OpenCode should be detected when installed", openCodePreset)
            assertEquals("opencode", openCodePreset?.command)
            assertEquals("acp", openCodePreset?.args)
        } else {
            println("OpenCode is not installed, skipping detection test")
        }
    }

    @Test
    fun `OpenCode preset should convert to config correctly`() {
        val preset = IdeaAcpAgentPreset(
            id = "opencode",
            name = "OpenCode",
            command = "opencode",
            args = "acp",
            env = "",
            description = "OpenCode AI coding agent via ACP"
        )
        
        val config = preset.toConfig()
        
        assertEquals("OpenCode", config.name)
        assertEquals("opencode", config.command)
        assertEquals("acp", config.args)
        assertEquals("", config.env)
    }

    @Test
    fun `all presets should have unique IDs`() {
        val allPresets = IdeaAcpAgentPreset::class.java
            .getDeclaredField("Companion")
            .get(null)!!::class.java
            .getDeclaredField("ALL_PRESETS")
            .apply { isAccessible = true }
            .get(IdeaAcpAgentPreset.Companion) as List<IdeaAcpAgentPreset>

        val ids = allPresets.map { it.id }
        val uniqueIds = ids.toSet()
        
        assertEquals("All preset IDs should be unique", ids.size, uniqueIds.size)
    }

    @Test
    fun `OpenCode should be first in presets list`() {
        val allPresets = IdeaAcpAgentPreset::class.java
            .getDeclaredField("Companion")
            .get(null)!!::class.java
            .getDeclaredField("ALL_PRESETS")
            .apply { isAccessible = true }
            .get(IdeaAcpAgentPreset.Companion) as List<IdeaAcpAgentPreset>

        assertEquals("OpenCode should be the first preset", "opencode", allPresets.firstOrNull()?.id)
    }

    private fun isCommandAvailable(command: String): Boolean {
        return try {
            val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
            val checkCmd = if (isWindows) listOf("where", command) else listOf("which", command)
            val process = ProcessBuilder(checkCmd)
                .redirectErrorStream(true)
                .start()
            val result = process.waitFor()
            result == 0
        } catch (e: Exception) {
            false
        }
    }
}
