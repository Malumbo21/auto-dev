package cc.unitmesh.devins.ui.nano

import cc.unitmesh.xuiper.ir.NanoBindingIR
import cc.unitmesh.xuiper.ir.NanoIR
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class NanoTextInterpolationTest {

    @Test
    fun `should interpolate brace and dollar templates`() {
        val state = mapOf(
            "budget" to 2000,
            "travelers" to 2
        )

        assertEquals(
            "SGD 2000",
            NanoRenderUtils.interpolateText("SGD {state.budget}", state)
        )

        // Escape $ for Kotlin source
        assertEquals(
            "SGD 2000",
            NanoRenderUtils.interpolateText("SGD ${'$'}{state.budget}", state)
        )

        assertEquals(
            "2 people",
            NanoRenderUtils.interpolateText("{state.travelers} people", state)
        )
    }

    @Test
    fun `should support len function in templates`() {
        val state = mapOf(
            "salesData" to listOf(1, 2, 3)
        )

        assertEquals(
            "共 3 条记录",
            NanoRenderUtils.interpolateText("共 {len(salesData)} 条记录", state)
        )

        assertEquals(
            "3 selected",
            NanoRenderUtils.interpolateText("{len(state.salesData)} selected", state)
        )
    }

    @Test
    fun `should resolve bound and literal string props`() {
        val state = mapOf("budget" to 2000)

        val literalBadge = NanoIR(
            type = "Badge",
            props = mapOf("text" to JsonPrimitive("SGD {state.budget}"))
        )
        val rawLiteral = NanoRenderUtils.resolveStringProp(literalBadge, "text", state)
        assertEquals("SGD {state.budget}", rawLiteral)
        assertEquals("SGD 2000", NanoRenderUtils.interpolateText(rawLiteral, state))

        val boundBadge = NanoIR(
            type = "Badge",
            bindings = mapOf("text" to NanoBindingIR(mode = "subscribe", expression = "state.budget"))
        )
        val rawBound = NanoRenderUtils.resolveStringProp(boundBadge, "text", state)
        assertEquals("2000", rawBound)
    }
}
