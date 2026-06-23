package cc.unitmesh.devins.idea.services

import cc.unitmesh.agent.config.ToolConfigFile
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class IdeaToolConfigServiceTest {

    @Test
    fun `fresh reload returns and publishes loaded tool config before caller continues`() = runBlocking {
        val freshConfig = ToolConfigFile(enabledMcpTools = listOf("mcp.search"))
        val events = mutableListOf<String>()

        val returnedConfig = loadFreshToolConfig(
            load = {
                events += "load"
                freshConfig
            },
            publish = {
                events += "publish:${it.enabledMcpTools.single()}"
            }
        )

        assertSame(freshConfig, returnedConfig)
        assertEquals(listOf("load", "publish:mcp.search"), events)
    }
}
