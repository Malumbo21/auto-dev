package cc.unitmesh.xuiper.render

import cc.unitmesh.xuiper.dsl.NanoDSL
import cc.unitmesh.xuiper.ir.NanoIR
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class HtmlRendererTest {

    private val renderer = HtmlRenderer()

    @Test
    fun `should render simple text component`() {
        val ir = NanoIR.text("Hello World", "h2")

        val html = renderer.renderNode(ir)

        assertContains(html, "<h2")
        assertContains(html, "Hello World")
        assertContains(html, "style-h2")
    }

    @Test
    fun `should render VStack with children`() {
        val ir = NanoIR.vstack(
            spacing = "md",
            children = listOf(
                NanoIR.text("First"),
                NanoIR.text("Second")
            )
        )

        val html = renderer.renderNode(ir)

        assertContains(html, "nano-vstack")
        assertContains(html, "spacing-md")
        assertContains(html, "First")
        assertContains(html, "Second")
    }

    @Test
    fun `should render HStack with alignment`() {
        val ir = NanoIR.hstack(
            spacing = "sm",
            align = "center",
            justify = "between",
            children = listOf(
                NanoIR.text("Left"),
                NanoIR.text("Right")
            )
        )

        val html = renderer.renderNode(ir)

        assertContains(html, "nano-hstack")
        assertContains(html, "align-center")
        assertContains(html, "justify-between")
    }

    @Test
    fun `should render Card with padding and shadow`() {
        val ir = NanoIR.card(
            padding = "lg",
            shadow = "md",
            children = listOf(NanoIR.text("Card Content"))
        )

        val html = renderer.renderNode(ir)

        assertContains(html, "nano-card")
        assertContains(html, "padding-lg")
        assertContains(html, "shadow-md")
        assertContains(html, "Card Content")
    }

    @Test
    fun `should render Button with intent`() {
        val ir = NanoIR.button("Click me", "primary")

        val html = renderer.renderNode(ir)

        assertContains(html, "<button")
        assertContains(html, "nano-button")
        assertContains(html, "intent-primary")
        assertContains(html, "Click me")
    }

    @Test
    fun `should render full HTML document`() {
        val ir = NanoIR.card(
            padding = "md",
            children = listOf(NanoIR.text("Hello"))
        )
        
        val html = renderer.render(ir)
        
        assertContains(html, "<!DOCTYPE html>")
        assertContains(html, "<html>")
        assertContains(html, "<style>")
        assertContains(html, "</html>")
    }

    @Test
    fun `should render from NanoDSL source`() {
        val source = """
component GreetingCard:
    Card:
        VStack(spacing="sm"):
            Text("Hello!", style="h2")
            Text("Welcome to NanoDSL")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        val html = renderer.renderNode(ir)

        assertContains(html, "nano-card")
        assertContains(html, "nano-vstack")
        assertContains(html, "Hello!")
        assertContains(html, "Welcome to NanoDSL")
    }

    @Test
    fun `should render divider`() {
        val ir = NanoIR(type = "Divider")

        val html = renderer.renderNode(ir)

        assertContains(html, "<hr")
        assertContains(html, "nano-divider")
    }

    @Test
    fun `should render all basic component types`() {
        val types = listOf("VStack", "HStack", "Card", "Text", "Button", "Image", "Badge", "Divider")

        types.forEach { type ->
            val ir = NanoIR(type = type)
            val html = renderer.renderNode(ir)
            assertTrue(html.isNotEmpty(), "Should render $type")
        }
    }

    // ============================================================================
    // Layout Tests
    // ============================================================================

    @Test
    fun `should render HStack with justify-between`() {
        val ir = NanoIR.hstack(
            justify = "between",
            children = listOf(
                NanoIR.text("Label"),
                NanoIR.button("Action")
            )
        )

        val html = renderer.renderNode(ir)

        assertContains(html, "nano-hstack")
        assertContains(html, "justify-between")
        // HStack should have width: 100% for justify to work
        // This is now in the CSS
    }

    @Test
    fun `should render HStack with justify-around`() {
        val ir = NanoIR.hstack(
            justify = "around",
            children = listOf(
                NanoIR.text("A"),
                NanoIR.text("B"),
                NanoIR.text("C")
            )
        )

        val html = renderer.renderNode(ir)

        assertContains(html, "justify-around")
    }

    @Test
    fun `should render nested HStack inside VStack`() {
        val ir = NanoIR.vstack(
            spacing = "md",
            children = listOf(
                NanoIR.hstack(
                    justify = "between",
                    children = listOf(
                        NanoIR.text("Left"),
                        NanoIR.text("Right")
                    )
                )
            )
        )

        val html = renderer.renderNode(ir)

        assertContains(html, "nano-vstack")
        assertContains(html, "nano-hstack")
        assertContains(html, "justify-between")
        assertContains(html, "Left")
        assertContains(html, "Right")
    }

    @Test
    fun `should render VStack with full width`() {
        val ir = NanoIR.vstack(
            children = listOf(
                NanoIR.text("Item 1"),
                NanoIR.text("Item 2")
            )
        )

        val html = renderer.render(ir)

        // VStack should have width: 100% in the CSS
        assertContains(html, "nano-vstack { display: flex; flex-direction: column; width: 100%; }")
    }

    @Test
    fun `should render HStack with full width`() {
        val ir = NanoIR.hstack(
            children = listOf(
                NanoIR.text("A"),
                NanoIR.text("B")
            )
        )

        val html = renderer.render(ir)

        // HStack should have width: 100% in the CSS
        assertContains(html, "nano-hstack { display: flex; flex-direction: row; align-items: center; width: 100%; }")
    }

    @Test
    fun `should render complex trip planner layout`() {
        // This test simulates the Singapore Trip Planner layout issue
        val ir = NanoIR.vstack(
            spacing = "lg",
            children = listOf(
                NanoIR.card(
                    padding = "md",
                    children = listOf(
                        NanoIR.vstack(
                            spacing = "md",
                            children = listOf(
                                NanoIR.text("Budget Calculator", "h2"),
                                NanoIR.hstack(
                                    justify = "between",
                                    children = listOf(
                                        NanoIR.text("Total Budget (\$)"),
                                        NanoIR(type = "Input")
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val html = renderer.renderNode(ir)

        assertContains(html, "nano-card")
        assertContains(html, "nano-vstack")
        assertContains(html, "nano-hstack")
        assertContains(html, "justify-between")
        assertContains(html, "Budget Calculator")
    }
}

