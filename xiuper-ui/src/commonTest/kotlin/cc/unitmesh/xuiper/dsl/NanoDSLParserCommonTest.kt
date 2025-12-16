package cc.unitmesh.xuiper.dsl

import cc.unitmesh.xuiper.ast.NanoNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cross-platform tests for NanoDSL parser.
 * These tests run on all platforms (JVM, WasmJs, JS, etc.)
 */
class NanoDSLParserCommonTest {

    @Test
    fun shouldParseSimpleComponent() {
        val source = """
component GreetingCard:
    Card:
        Text("Hello!", style="h2")
        """.trimIndent()

        val result = NanoDSL.parse(source)

        assertEquals("GreetingCard", result.name)
        assertEquals(1, result.children.size)
        assertTrue(result.children[0] is NanoNode.Card)
    }

    @Test
    fun shouldParseVStackWithSpacing() {
        val source = """
component TestComponent:
    VStack(spacing="md"):
        Text("First")
        Text("Second")
        """.trimIndent()

        val result = NanoDSL.parse(source)

        assertEquals("TestComponent", result.name)
        val vstack = result.children[0] as NanoNode.VStack
        assertEquals("md", vstack.spacing)
        assertEquals(2, vstack.children.size)
    }

    @Test
    fun shouldParseHStackWithAlignment() {
        val source = """
component TestComponent:
    HStack(align="center", justify="between"):
        Text("Left")
        Text("Right")
        """.trimIndent()

        val result = NanoDSL.parse(source)

        val hstack = result.children[0] as NanoNode.HStack
        assertEquals("center", hstack.align)
        assertEquals("between", hstack.justify)
        assertEquals(2, hstack.children.size)
    }

    @Test
    fun shouldParseStateDeclaration() {
        val source = """
component Counter:
    state:
        count: Int = 0
        name: String = "test"

    Card:
        Text("Count: {count}")
        """.trimIndent()

        val result = NanoDSL.parse(source)

        assertNotNull(result.state)
        assertEquals(2, result.state!!.variables.size)
        assertEquals("count", result.state!!.variables[0].name)
        assertEquals("name", result.state!!.variables[1].name)
    }

    @Test
    fun shouldParseButtonWithLabel() {
        val source = """
component TestComponent:
    Button("Click Me")
        """.trimIndent()

        val result = NanoDSL.parse(source)

        val button = result.children[0] as NanoNode.Button
        assertEquals("Click Me", button.label)
    }

    @Test
    fun shouldParseNestedComponents() {
        val source = """
component NestedTest:
    Card:
        VStack(spacing="md"):
            Text("Title", style="h2")
            HStack:
                Button("OK")
                Button("Cancel")
        """.trimIndent()

        val result = NanoDSL.parse(source)

        val card = result.children[0] as NanoNode.Card
        val vstack = card.children[0] as NanoNode.VStack
        assertEquals(2, vstack.children.size)
        
        val hstack = vstack.children[1] as NanoNode.HStack
        assertEquals(2, hstack.children.size)
    }

    @Test
    fun shouldParseInputWithPlaceholder() {
        val source = """
component FormTest:
    Input(placeholder="Enter email")
        """.trimIndent()

        val result = NanoDSL.parse(source)

        val input = result.children[0] as NanoNode.Input
        assertEquals("Enter email", input.placeholder)
    }

    @Test
    fun shouldParseImageWithProps() {
        val source = """
component ImageTest:
    Image(src="/path/to/image.jpg", aspect=16/9, radius="md")
        """.trimIndent()

        val result = NanoDSL.parse(source)

        val image = result.children[0] as NanoNode.Image
        assertEquals("/path/to/image.jpg", image.src)
        assertEquals("md", image.radius)
    }

    @Test
    fun shouldParseBadgeWithColor() {
        val source = """
component BadgeTest:
    Badge("Active", color="green")
        """.trimIndent()

        val result = NanoDSL.parse(source)

        val badge = result.children[0] as NanoNode.Badge
        assertEquals("Active", badge.text)
        assertEquals("green", badge.color)
    }
}

