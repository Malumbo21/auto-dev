package cc.unitmesh.xuiper.ir

import cc.unitmesh.xuiper.dsl.NanoDSL
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cross-platform tests for NanoIR converter.
 * These tests verify that AST to IR conversion works correctly on all platforms.
 */
class NanoIRConverterCommonTest {

    @Test
    fun shouldConvertSimpleComponentToIR() {
        val source = """
component GreetingCard:
    Card:
        Text("Hello World!", style="h2")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)

        assertEquals("Component", ir.type)
        assertEquals("GreetingCard", ir.props["name"]?.jsonPrimitive?.content)
        assertNotNull(ir.children)
        assertEquals(1, ir.children!!.size)

        val card = ir.children!![0]
        assertEquals("Card", card.type)
        assertNotNull(card.children)
        assertEquals(1, card.children!!.size)

        val text = card.children!![0]
        assertEquals("Text", text.type)
        assertEquals("Hello World!", text.props["content"]?.jsonPrimitive?.content)
        assertEquals("h2", text.props["style"]?.jsonPrimitive?.content)
    }

    @Test
    fun shouldConvertVStackToIR() {
        val source = """
component LayoutTest:
    VStack(spacing="md", padding="lg"):
        Text("First")
        Text("Second")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)

        assertNotNull(ir.children)
        val vstack = ir.children!![0]
        assertEquals("VStack", vstack.type)
        assertEquals("md", vstack.props["spacing"]?.jsonPrimitive?.content)
        assertEquals("lg", vstack.props["padding"]?.jsonPrimitive?.content)
        assertNotNull(vstack.children)
        assertEquals(2, vstack.children!!.size)
    }

    @Test
    fun shouldConvertButtonToIR() {
        val source = """
component ButtonTest:
    Button("Click Me", intent="primary")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)

        assertNotNull(ir.children)
        val button = ir.children!![0]
        assertEquals("Button", button.type)
        assertEquals("Click Me", button.props["label"]?.jsonPrimitive?.content)
        assertEquals("primary", button.props["intent"]?.jsonPrimitive?.content)
    }

    @Test
    fun shouldConvertNestedStructureToIR() {
        val source = """
component NestedTest:
    Card:
        VStack:
            Text("Title")
            Text("Content")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)

        assertNotNull(ir.children)
        val card = ir.children!![0]
        assertEquals("Card", card.type)

        assertNotNull(card.children)
        val vstack = card.children!![0]
        assertEquals("VStack", vstack.type)

        assertNotNull(vstack.children)
        assertEquals(2, vstack.children!!.size)
    }
}

