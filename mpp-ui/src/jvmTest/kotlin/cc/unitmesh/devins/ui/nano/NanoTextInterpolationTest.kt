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
            NanoExpressionEvaluator.interpolateText("SGD {state.budget}", state)
        )

        // Escape $ for Kotlin source
        assertEquals(
            "SGD 2000",
            NanoExpressionEvaluator.interpolateText("SGD ${'$'}{state.budget}", state)
        )

        assertEquals(
            "2 people",
            NanoExpressionEvaluator.interpolateText("{state.travelers} people", state)
        )
    }

    @Test
    fun `should support len function in templates`() {
        val state = mapOf(
            "salesData" to listOf(1, 2, 3)
        )

        assertEquals(
            "共 3 条记录",
            NanoExpressionEvaluator.interpolateText("共 {len(salesData)} 条记录", state)
        )

        assertEquals(
            "3 selected",
            NanoExpressionEvaluator.interpolateText("{len(state.salesData)} selected", state)
        )
    }

    @Test
    fun `should resolve bound and literal string props`() {
        val state = mapOf("budget" to 2000)

        val literalBadge = NanoIR(
            type = "Badge",
            props = mapOf("text" to JsonPrimitive("SGD {state.budget}"))
        )
        val rawLiteral = NanoExpressionEvaluator.resolveStringProp(literalBadge, "text", state)
        assertEquals("SGD {state.budget}", rawLiteral)
        assertEquals("SGD 2000", NanoExpressionEvaluator.interpolateText(rawLiteral, state))

        val boundBadge = NanoIR(
            type = "Badge",
            bindings = mapOf("text" to NanoBindingIR(mode = "subscribe", expression = "state.budget"))
        )
        val rawBound = NanoExpressionEvaluator.resolveStringProp(boundBadge, "text", state)
        assertEquals("2000", rawBound)
    }

    @Test
    fun `should resolve bare path expressions in literal props`() {
        val state = mapOf(
            "item" to mapOf(
                "name" to "护照",
                "checked" to true
            )
        )

        val checkbox = NanoIR(
            type = "Checkbox",
            props = mapOf(
                "label" to JsonPrimitive("item.name")
            )
        )

        assertEquals("护照", NanoExpressionEvaluator.resolveStringProp(checkbox, "label", state))

        // Guardrail: date-like literals must not be treated as arithmetic expressions.
        val text = NanoIR(type = "Text", props = mapOf("content" to JsonPrimitive("2024-06-15")))
        assertEquals("2024-06-15", NanoExpressionEvaluator.resolveStringProp(text, "content", emptyMap()))
    }

    @Test
    fun `should resolve nested paths for loop variables`() {
        val state = mapOf(
            "day_plan" to mapOf(
                "day" to 1,
                "title" to "抵达北京",
                "activities" to listOf("入住酒店", "王府井逛街")
            )
        )

        assertEquals("1", NanoExpressionEvaluator.evaluateExpression("day_plan.day", state))
        assertEquals("抵达北京", NanoExpressionEvaluator.evaluateExpression("day_plan.title", state))
        assertEquals("入住酒店", NanoExpressionEvaluator.evaluateExpression("day_plan.activities[0]", state))
    }
}
