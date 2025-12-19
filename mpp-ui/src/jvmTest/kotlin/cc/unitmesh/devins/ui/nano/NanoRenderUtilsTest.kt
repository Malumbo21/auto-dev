package cc.unitmesh.devins.ui.nano

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NanoRenderUtilsTest {

    /**
     * Tests text interpolation with state variable substitution
     */
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

    /**
     * Tests numeric expression evaluation against state
     */
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

    /**
     * Tests boolean expression evaluation against state
     */
    @Test
    fun `evaluateCondition supports negation and comparisons`() {
        val state = mapOf(
            "name" to "",
            "count" to 3,
            "enabled" to true,
            "mode" to "primary"
        )

        assertFalse(NanoRenderUtils.evaluateCondition("state.name", state))
        assertTrue(NanoRenderUtils.evaluateCondition("!state.name", state))

        assertTrue(NanoRenderUtils.evaluateCondition("state.enabled == true", state))
        assertTrue(NanoRenderUtils.evaluateCondition("state.count >= 3", state))
        assertFalse(NanoRenderUtils.evaluateCondition("state.count > 3", state))
        assertTrue(NanoRenderUtils.evaluateCondition("state.mode == 'primary'", state))
        assertFalse(NanoRenderUtils.evaluateCondition("state.mode != 'primary'", state))
    }

    @Test
    fun `evaluateCondition supports boolean operators and complex paths`() {
        // Defines test data with nested properties
        val state = mapOf(
            "selectedAirline" to "all",
            "priceRange" to listOf(0, 5000),
            "flight" to mapOf(
                "airline" to "中国国航",
                "price" to 1280
            ),
            "enabled" to true,
            "count" to 3
        )

        assertTrue(
            NanoRenderUtils.evaluateCondition(
                "state.selectedAirline == \"all\" or flight.airline == state.selectedAirline",
                state
            )
        )
        assertTrue(
            NanoRenderUtils.evaluateCondition(
                "flight.price <= state.priceRange[1]",
                state
            )
        )
        assertTrue(
            NanoRenderUtils.evaluateCondition(
                "state.enabled == true and state.count >= 3",
                state
            )
        )
    }

    /**
     * Verifies bracketed map indexing within interpolated text
     */
    @Test
    fun `interpolateText supports map indexing with brackets`() {
        val state = mapOf(
            "flight" to mapOf(
                "airline" to "中国国航",
                "price" to 1280
            )
        )

        assertEquals("中国国航", NanoRenderUtils.interpolateText("{flight['airline']}", state))
        assertEquals("1280", NanoRenderUtils.interpolateText("{flight[\"price\"]}", state))
    }

    /**
     * Verifies arithmetic interpolation over nested map paths
     */
    @Test
    fun `interpolateText supports arithmetic over nested map paths`() {
        val state = mapOf(
            "budget" to mapOf(
                "transport" to 800,
                "hotel" to 1200,
                "food" to 600,
                "tickets" to 400
            )
        )

        assertEquals(
            "¥3000",
            NanoRenderUtils.interpolateText(
                "¥{state.budget.transport + state.budget.hotel + state.budget.food + state.budget.tickets}",
                state
            )
        )
    }

    /**
     * Verifies empty collections evaluate to false
     */
    @Test
    fun `evaluateCondition treats empty collections as false`() {
        val state = mapOf(
            "selectedFlight" to emptyMap<String, Any>(),
            "flights" to emptyList<Any>()
        )

        assertFalse(NanoRenderUtils.evaluateCondition("state.selectedFlight", state))
        assertFalse(NanoRenderUtils.evaluateCondition("state.flights", state))
    }

    @Test
    fun `resolveAny supports JSON list literals for for-loop`() {
        val expr = """
            [
              {"name": "Gardens by the Bay", "icon": "leaf", "color": "green"},
              {"name": "Marina Bay Sands", "icon": "building", "color": "blue"}
            ]
        """.trimIndent()

        val resolved = NanoRenderUtils.resolveAny(expr, emptyMap())
        assertNotNull(resolved)

        val list = resolved as List<*>
        assertEquals(2, list.size)

        val first = list[0] as Map<*, *>
        assertEquals("Gardens by the Bay", first["name"])
        assertEquals("leaf", first["icon"])
        assertEquals("green", first["color"])
    }
}
