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

    @Test
    fun `should fall back to nearest Text when src is a tokenized watermark url`() {
        val src = "https://maas-watermark-prod.cn-wlcb.ufileos.com/202512182322008c733db7af5646e1_watermark.png?UCloudPublicKey=TOKEN_75a9ae85-4f15-4045-940f-e94c0f82ae90&Signature=1iwNc4b9q2t44fmQ9duOmVQrppA%3D&Expires=1766157725"
        val context = """
            VStack():
                Text("Page Title")
                Text("Real Product Photo")
                Image(src="$src", aspect=1, radius="sm")
                Text("Footer")
        """.trimIndent()

        assertEquals("Real Product Photo", extractImagePrompt(src, context))
    }
}
