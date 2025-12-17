package cc.unitmesh.devins.ui.nano

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NanoRenderUtilsTest {

    @Test
    fun `interpolateText supports dollar braces templates`() {
        val state = mapOf(
            "flightBudget" to 800,
            "accommodationBudget" to 150,
            "foodBudget" to 200,
            "activitiesBudget" to 300,
            "accommodation" to "hotel",
            "transport" to "mrt"
        )

        assertEquals("Budget: 800", NanoRenderUtils.interpolateText("Budget: \${flightBudget}", state))
        assertEquals("Budget: 800", NanoRenderUtils.interpolateText("Budget: {flightBudget}", state))
        assertEquals(
            "Total: 2050",
            NanoRenderUtils.interpolateText(
                "Total: \${flightBudget + (accommodationBudget * 5) + foodBudget + activitiesBudget}",
                state
            )
        )

        assertEquals("Hotel", NanoRenderUtils.interpolateText("{state.accommodation.title()}", state))
        assertEquals("MRT", NanoRenderUtils.interpolateText("{state.transport.replace('mrt', 'MRT').title()}", state))
        assertEquals("", NanoRenderUtils.interpolateText("{state.unknown.title()}", state))
    }

    @Test
    fun `evaluateNumberOrNull supports subscribe expression`() {
        val state = mapOf(
            "flightBudget" to 800,
            "accommodationBudget" to 150,
            "foodBudget" to 200,
            "activitiesBudget" to 300
        )

        val v = NanoRenderUtils.evaluateNumberOrNull(
            "<< (flightBudget + (accommodationBudget * 5) + foodBudget + activitiesBudget)",
            state
        )
        assertNotNull(v)
        assertEquals(2050.0, v)
    }
}
