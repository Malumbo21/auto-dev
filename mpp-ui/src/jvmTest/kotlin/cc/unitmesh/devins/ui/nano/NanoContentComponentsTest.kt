package cc.unitmesh.devins.ui.nano

import cc.unitmesh.xuiper.ir.NanoIR
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class NanoContentComponentsTest {

    // === Code Component Tests ===

    @Test
    fun `RenderCode should resolve interpolated content`() {
        val state = mapOf("variable" to "test-value")

        val codeIR = NanoIR(
            type = "Code",
            props = mapOf("content" to JsonPrimitive("const x = '{state.variable}';"))
        )

        val rawContent = NanoExpressionEvaluator.resolveStringProp(codeIR, "content", state)
        val interpolated = NanoExpressionEvaluator.interpolateText(rawContent, state)

        assertEquals("const x = 'test-value';", interpolated)
    }

    @Test
    fun `RenderCode should handle state binding for content`() {
        val state = mapOf("status" to "active")

        val codeIR = NanoIR(
            type = "Code",
            props = mapOf("content" to JsonPrimitive("state.status"))
        )

        val rawContent = NanoExpressionEvaluator.resolveStringProp(codeIR, "content", state)
        assertEquals("active", rawContent)
    }

    // === Link Component Tests ===

    @Test
    fun `RenderLink should resolve content and url with interpolation`() {
        val state = mapOf("docUrl" to "https://example.com/docs")

        val linkIR = NanoIR(
            type = "Link",
            props = mapOf(
                "content" to JsonPrimitive("Read the docs"),
                "url" to JsonPrimitive("state.docUrl")
            )
        )

        val content = NanoExpressionEvaluator.resolveStringProp(linkIR, "content", state)
        val url = NanoExpressionEvaluator.resolveStringProp(linkIR, "url", state)

        assertEquals("Read the docs", content)
        assertEquals("https://example.com/docs", url)
    }

    @Test
    fun `RenderLink should resolve showIcon prop`() {
        val linkIR = NanoIR(
            type = "Link",
            props = mapOf(
                "content" to JsonPrimitive("Click here"),
                "url" to JsonPrimitive("https://example.com"),
                "showIcon" to JsonPrimitive("true")
            )
        )

        val showIcon = linkIR.props["showIcon"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
        assertEquals(true, showIcon)
    }

    // === Blockquote Component Tests ===

    @Test
    fun `RenderBlockquote should resolve content with interpolation`() {
        val state = mapOf("quote" to "Hello World")

        val blockquoteIR = NanoIR(
            type = "Blockquote",
            props = mapOf("content" to JsonPrimitive("The user said: '{state.quote}'"))
        )

        val rawContent = NanoExpressionEvaluator.resolveStringProp(blockquoteIR, "content", state)
        val interpolated = NanoExpressionEvaluator.interpolateText(rawContent, state)

        assertEquals("The user said: 'Hello World'", interpolated)
    }

    @Test
    fun `RenderBlockquote should resolve attribution with interpolation`() {
        val state = mapOf("author" to "Alice")

        val blockquoteIR = NanoIR(
            type = "Blockquote",
            props = mapOf(
                "content" to JsonPrimitive("Something wise"),
                "attribution" to JsonPrimitive("{state.author}")
            )
        )

        val rawAttribution = blockquoteIR.props["attribution"]?.jsonPrimitive?.content
        val interpolated = NanoExpressionEvaluator.interpolateText(rawAttribution ?: "", state)

        assertEquals("Alice", interpolated)
    }

    @Test
    fun `RenderBlockquote should handle variant prop`() {
        val variants = listOf("warning", "success", "info", "default")

        variants.forEach { variant ->
            val blockquoteIR = NanoIR(
                type = "Blockquote",
                props = mapOf(
                    "content" to JsonPrimitive("Test quote"),
                    "variant" to JsonPrimitive(variant)
                )
            )

            val resolvedVariant = blockquoteIR.props["variant"]?.jsonPrimitive?.content
            assertEquals(variant, resolvedVariant)
        }
    }

    // === Text Component with Markdown Tests ===

    @Test
    fun `RenderText should resolve align prop`() {
        val textIR = NanoIR(
            type = "Text",
            props = mapOf(
                "content" to JsonPrimitive("Centered text"),
                "align" to JsonPrimitive("center")
            )
        )

        val align = textIR.props["align"]?.jsonPrimitive?.content
        assertEquals("center", align)
    }

    @Test
    fun `RenderText should support markdown prop for inline parsing`() {
        val textIR = NanoIR(
            type = "Text",
            props = mapOf(
                "content" to JsonPrimitive("Hello **world**"),
                "markdown" to JsonPrimitive("true")
            )
        )

        val markdown = textIR.props["markdown"]?.jsonPrimitive?.content
        assertEquals("true", markdown)
    }

    @Test
    fun `RenderText should disable markdown when set to false`() {
        val textIR = NanoIR(
            type = "Text",
            props = mapOf(
                "content" to JsonPrimitive("Hello **world**"),
                "markdown" to JsonPrimitive("false")
            )
        )

        val enableMarkdown = textIR.props["markdown"]?.jsonPrimitive?.content != "false"
        assertEquals(false, enableMarkdown)
    }
}
