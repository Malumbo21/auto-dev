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
    fun `should render Button with disabled_if`() {
        val ir = NanoIR(
            type = "Button",
            props = mapOf(
                "label" to kotlinx.serialization.json.JsonPrimitive("Submit"),
                "disabled_if" to kotlinx.serialization.json.JsonPrimitive("!state.name")
            )
        )

        val html = renderer.renderNode(ir)

        assertContains(html, "data-disabled-if=\"!state.name\"")
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

    @Test
    fun `should render HStack with VStack children containing Text and Input`() {
        // Regression test for the "From" text being displayed vertically (F-R-O-M)
        // This was caused by HStack > * { flex: 1 1 0 } squeezing all children
        val ir = NanoIR.hstack(
            align = "center",
            justify = "between",
            children = listOf(
                NanoIR.vstack(
                    children = listOf(
                        NanoIR.text("From", "body"),
                        NanoIR(type = "Input")
                    )
                ),
                NanoIR.vstack(
                    children = listOf(
                        NanoIR.text("To", "body"),
                        NanoIR(type = "Input")
                    )
                )
            )
        )

        val html = renderer.render(ir)

        // Check that CSS rules are correct
        assertContains(html, ".nano-hstack > .nano-vstack { flex: 1 1 0; min-width: 0; }")
        assertContains(html, ".nano-hstack > .nano-text { flex: 0 0 auto; width: auto; }")
        assertContains(html, ".nano-hstack > .nano-input { flex: 1 1 0; min-width: 0; }")

        // Check that structure is correct
        assertContains(html, "nano-hstack")
        assertContains(html, "nano-vstack")
        assertContains(html, "From")
        assertContains(html, "To")
    }

    // ============================================================================
    // Slider Tests
    // ============================================================================

    @Test
    fun `should render slider with value display`() {
        val source = """
component PriceSlider:
    state:
        max_price: 2000
    VStack(spacing="sm"):
        Text(f"Max Price: ${"\$"}{state.max_price}", style="caption")
        Slider(
            label="Price Range",
            bind := state.max_price,
            min=500,
            max=5000,
            step=100
        )
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        val html = renderer.renderNode(ir)

        // Should have slider container
        assertContains(html, "nano-slider-container")

        // Should have label
        assertContains(html, "Price Range")
        assertContains(html, "nano-slider-label")

        // Should have value display span
        assertContains(html, "nano-slider-value")
        assertContains(html, "data-bind=\"state.max_price\"")

        // Should have slider input with correct attributes
        assertContains(html, "<input type=\"range\"")
        assertContains(html, "class=\"nano-slider\"")
        assertContains(html, "min=\"500.0\"")
        assertContains(html, "max=\"5000.0\"")
        assertContains(html, "step=\"100.0\"")

        // Should have binding attribute
        assertContains(html, "data-bindings")
    }

    @Test
    fun `should render slider without label but with value`() {
        val source = """
component SimpleSlider:
    state:
        volume: 50
    Slider(
        bind := state.volume,
        min=0,
        max=100,
        step=1
    )
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        val html = renderer.renderNode(ir)

        // Should have value display even without label
        assertContains(html, "nano-slider-value")
        assertContains(html, "data-bind=\"state.volume\"")

        // Should have slider with correct range
        assertContains(html, "min=\"0.0\"")
        assertContains(html, "max=\"100.0\"")
    }

    // ============================================================================
    // Image Tests
    // ============================================================================

    @Test
    fun `should render image with data URL`() {
        val ir = NanoIR(
            type = "Image",
            props = mapOf(
                "src" to kotlinx.serialization.json.JsonPrimitive("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAUA"),
                "alt" to kotlinx.serialization.json.JsonPrimitive("Test image")
            )
        )

        val html = renderer.renderNode(ir)

        assertContains(html, "<img")
        assertContains(html, "src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAUA\"")
        assertContains(html, "alt=\"Test image\"")
        assertTrue(html.contains("nano-image"), "Should have nano-image class")
    }

    @Test
    fun `should render placeholder for http URL to prevent CORS errors`() {
        val ir = NanoIR(
            type = "Image",
            props = mapOf(
                "src" to kotlinx.serialization.json.JsonPrimitive("https://example.com/fake-image.jpg"),
                "alt" to kotlinx.serialization.json.JsonPrimitive("Fake image")
            )
        )

        val html = renderer.renderNode(ir)

        // Should NOT render an img tag
        assertTrue(!html.contains("<img"), "Should not render img tag for http URL")
        
        // Should render a placeholder div instead
        assertContains(html, "nano-image-placeholder")
        assertContains(html, "placeholder-text")
        assertContains(html, "🖼️ Image: Fake image")
        assertContains(html, "data-original-src=\"https://example.com/fake-image.jpg\"")
    }

    @Test
    fun `should render placeholder for LLM-generated watermark URL`() {
        val src = "https://maas-watermark-prod.cn-wlcb.ufileos.com/20251220142749d25e14e8b2c74d00_watermark.png?UCloudPublicKey=TOKEN_75a9ae85-4f15-4045-940f-e94c0f82ae90&Signature=x%2B35HR9q2ZmUzKiq8CSu9l6%2FSUk%3D&Expires=1766816876"
        val ir = NanoIR(
            type = "Image",
            props = mapOf(
                "src" to kotlinx.serialization.json.JsonPrimitive(src),
                "aspect" to kotlinx.serialization.json.JsonPrimitive("16/9"),
                "radius" to kotlinx.serialization.json.JsonPrimitive("md")
            )
        )

        val html = renderer.renderNode(ir)

        // Should render placeholder, not actual img tag
        assertContains(html, "nano-image-placeholder")
        assertContains(html, "aspect-16-9")
        assertContains(html, "radius-md")
        assertTrue(!html.contains("<img"), "Should not render img tag for fake URL")
    }

    @Test
    fun `should render placeholder for relative path URL`() {
        val ir = NanoIR(
            type = "Image",
            props = mapOf(
                "src" to kotlinx.serialization.json.JsonPrimitive("/path/to/image.jpg"),
                "alt" to kotlinx.serialization.json.JsonPrimitive("Local image")
            )
        )

        val html = renderer.renderNode(ir)

        // Should render placeholder for non-data URLs
        assertContains(html, "nano-image-placeholder")
        assertContains(html, "🖼️ Image: Local image")
    }

    @Test
    fun `should include CSS for image placeholder`() {
        val ir = NanoIR.vstack(
            children = listOf(NanoIR(type = "Image"))
        )

        val html = renderer.render(ir)

        // Should include placeholder CSS
        assertContains(html, ".nano-image-placeholder")
        assertContains(html, "border: 2px dashed")
        assertContains(html, ".placeholder-text")
    }
}
