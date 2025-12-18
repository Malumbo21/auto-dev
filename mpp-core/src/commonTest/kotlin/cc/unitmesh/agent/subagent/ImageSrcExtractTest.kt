package cc.unitmesh.agent.subagent

import kotlin.test.Test
import kotlin.test.assertEquals

class ImageSrcExtractTest {

    @Test
    fun `should prefer prompt derived from src over surrounding Text`() {
        val src = "/singapore-marina-bay.jpg"
        val context = """
            k(spacing="lg"):
                    # Header
                    Card(padding="md", shadow="sm"):
                        VStack(spacing="sm", align="center"):
                            Text("Singapore Trip Planner", style="h1")
                            Image(src="/singapore-marina-bay.jpg", aspect=16/3, radius="md")
        """.trimIndent()

        assertEquals("singapore marina bay", extractImagePrompt(src, context))
    }

    @Test
    fun `should fall back to context prompt when src is not meaningful`() {
        val src = "/img.jpg"
        val context = """
            VStack():
                Text("Marina Bay skyline")
                Image(src="/img.jpg")
        """.trimIndent()

        assertEquals("Marina Bay skyline", extractImagePrompt(src, context))
    }
}
