package cc.unitmesh.devins.ui.nano

import cc.unitmesh.xuiper.dsl.NanoDSL
import cc.unitmesh.xuiper.ir.NanoIR
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for markdown inline parsing functionality in NanoContentComponents.RenderText
 * These tests verify the DSL parsing and IR structure, not the visual rendering.
 */
class NanoMarkdownInlineTest {

    @Test
    fun `should parse DSL with bold markdown syntax`() {
        val source = """
component Demo:
    VStack:
        Text("This is **bold** text")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        assertNotNull(ir)
        
        val textNode = ir.children?.firstOrNull()?.children?.firstOrNull()
        assertNotNull(textNode)
        assertEquals("Text", textNode.type)
        
        val content = textNode.props["content"]?.toString()?.trim('"')
        assertTrue(content?.contains("**bold**") == true)
    }

    @Test
    fun `should parse DSL with italic markdown syntax`() {
        val source = """
component Demo:
    VStack:
        Text("This is *italic* text")
        Text("This is _also italic_ text")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        assertNotNull(ir)
        
        val texts = ir.children?.firstOrNull()?.children?.filter { it.type == "Text" }
        assertEquals(2, texts?.size)
    }

    @Test
    fun `should parse DSL with inline code markdown`() {
        val source = """
component Demo:
    VStack:
        Text("Use the `print()` function")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        assertNotNull(ir)
        
        val textNode = ir.children?.firstOrNull()?.children?.firstOrNull()
        val content = textNode?.props?.get("content")?.toString()?.trim('"')
        assertTrue(content?.contains("`print()`") == true)
    }

    @Test
    fun `should parse DSL with strikethrough markdown`() {
        val source = """
component Demo:
    VStack:
        Text("This is ~~deleted~~ text")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        assertNotNull(ir)
        
        val textNode = ir.children?.firstOrNull()?.children?.firstOrNull()
        val content = textNode?.props?.get("content")?.toString()?.trim('"')
        assertTrue(content?.contains("~~deleted~~") == true)
    }

    @Test
    fun `should parse DSL with underline markdown`() {
        val source = """
component Demo:
    VStack:
        Text("This is __underlined__ text")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        assertNotNull(ir)
        
        val textNode = ir.children?.firstOrNull()?.children?.firstOrNull()
        val content = textNode?.props?.get("content")?.toString()?.trim('"')
        assertTrue(content?.contains("__underlined__") == true)
    }

    @Test
    fun `should parse DSL with link markdown`() {
        val source = """
component Demo:
    VStack:
        Text("Visit [our website](https://example.com) for info")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        assertNotNull(ir)
        
        val textNode = ir.children?.firstOrNull()?.children?.firstOrNull()
        val content = textNode?.props?.get("content")?.toString()?.trim('"')
        assertTrue(content?.contains("[our website](https://example.com)") == true)
    }

    @Test
    fun `should parse DSL with mixed markdown`() {
        val source = """
component Demo:
    VStack:
        Text("**bold** and *italic* and `code` and ~~strike~~")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        assertNotNull(ir)
        
        val textNode = ir.children?.firstOrNull()?.children?.firstOrNull()
        val content = textNode?.props?.get("content")?.toString()?.trim('"')
        assertNotNull(content)
        assertTrue(content.contains("**bold**"))
        assertTrue(content.contains("*italic*"))
        assertTrue(content.contains("`code`"))
        assertTrue(content.contains("~~strike~~"))
    }

    @Test
    fun `should parse DSL with markdown disabled`() {
        val source = """
component Demo:
    VStack:
        Text("This **should not** be bold", markdown="false")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        assertNotNull(ir)
        
        val textNode = ir.children?.firstOrNull()?.children?.firstOrNull()
        assertNotNull(textNode)
        // Just verify the node exists and has content
        val content = textNode.props["content"]?.toString()?.trim('"')
        assertEquals("This **should not** be bold", content)
    }

    @Test
    fun `should parse DSL with markdown and state interpolation`() {
        val source = """
component Demo:
    state:
        username: string = "Alice"
    
    VStack:
        Text("Welcome **{state.username}**!")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        assertNotNull(ir)
        
        val textNode = ir.children?.firstOrNull()?.children?.firstOrNull()
        val content = textNode?.props?.get("content")?.toString()?.trim('"')
        assertTrue(content?.contains("**{state.username}**") == true)
    }

    @Test
    fun `should parse DSL with markdown in different text styles`() {
        val source = """
component Demo:
    VStack:
        Text("**Bold** heading", style="h1")
        Text("*Italic* body", style="body")
        Text("`Code` in caption", style="caption")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        assertNotNull(ir)
        
        val texts = ir.children?.firstOrNull()?.children?.filter { it.type == "Text" }
        assertEquals(3, texts?.size)
        
        assertEquals("h1", texts?.get(0)?.props?.get("style")?.toString()?.trim('"'))
        assertEquals("body", texts?.get(1)?.props?.get("style")?.toString()?.trim('"'))
        assertEquals("caption", texts?.get(2)?.props?.get("style")?.toString()?.trim('"'))
    }

    @Test
    fun `NanoExpressionEvaluator should handle markdown content with interpolation`() {
        val state = mapOf("name" to "Alice", "status" to "active")
        
        val ir = NanoIR(
            type = "Text",
            props = mapOf(
                "content" to JsonPrimitive("User **{state.name}** is *{state.status}*"),
                "markdown" to JsonPrimitive("true")
            )
        )
        
        val rawContent = NanoExpressionEvaluator.resolveStringProp(ir, "content", state)
        val interpolated = NanoExpressionEvaluator.interpolateText(rawContent, state)
        
        assertEquals("User **Alice** is *active*", interpolated)
    }

    @Test
    fun `should handle complex nested markdown in DSL`() {
        val source = """
component Demo:
    VStack:
        Text("**Bold with *italic* inside**")
        Text("*Italic with `code` inside*")
        Text("`Code with **bold**` mixed")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        assertNotNull(ir)
        
        val texts = ir.children?.firstOrNull()?.children?.filter { it.type == "Text" }
        assertEquals(3, texts?.size)
    }

    @Test
    fun `should handle markdown with special characters`() {
        val source = """
component Demo:
    VStack:
        Text("Code: `x = 1 + 2` and **bold: 3 > 2**")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        assertNotNull(ir)
        
        val textNode = ir.children?.firstOrNull()?.children?.firstOrNull()
        val content = textNode?.props?.get("content")?.toString()?.trim('"')
        assertNotNull(content)
        assertTrue(content.contains("`x = 1 + 2`"))
        assertTrue(content.contains("**bold: 3 > 2**"))
    }

    @Test
    fun `should handle empty markdown content`() {
        val source = """
component Demo:
    VStack:
        Text("")
        Text("**")
        Text("``")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        assertNotNull(ir)
        
        val texts = ir.children?.firstOrNull()?.children?.filter { it.type == "Text" }
        assertEquals(3, texts?.size)
    }

    @Test
    fun `should handle markdown at text boundaries`() {
        val source = """
component Demo:
    VStack:
        Text("**bold**")
        Text("**bold** text")
        Text("text **bold**")
        Text("`code`")
        Text("[link](url)")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        assertNotNull(ir)
        
        val texts = ir.children?.firstOrNull()?.children?.filter { it.type == "Text" }
        assertEquals(5, texts?.size)
    }

    @Test
    fun `should parse real-world markdown examples`() {
        val source = """
component RichTextDemo:
    state:
        docUrl: string = "https://docs.example.com"
        version: string = "v2.0"
    
    VStack(spacing="md"):
        Text("# Documentation", style="h1")
        Text("See [the docs]({state.docUrl}) for **{state.version}** release notes")
        Text("Run `npm install package@{state.version}` to install")
        Text("~~Deprecated~~ Use the new API instead")
        Text("__Important__: Breaking changes in this release")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        assertNotNull(ir)
        
        val texts = ir.children?.firstOrNull()?.children?.filter { it.type == "Text" }
        assertEquals(5, texts?.size)
        
        // Verify state exists
        assertNotNull(ir.state)
    }
}
