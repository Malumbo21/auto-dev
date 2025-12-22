package cc.unitmesh.devins.ui.nano

import cc.unitmesh.xuiper.dsl.NanoDSL
import cc.unitmesh.xuiper.eval.evaluator.NanoExpressionEvaluator
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.state.NanoStateRuntime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NanoStateRuntimeTest {

    @Test
    fun `initial state should be available immediately`() {
        val source = """
component Demo:
    state:
        current_day: int = 1
        show_summary: bool = false

    VStack:
        if state.current_day == 1:
            Text(\"Day1\")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        val runtime = NanoStateRuntime(ir)

        val snapshot = runtime.snapshot()
        assertEquals(1, snapshot["current_day"])
        assertEquals(false, snapshot["show_summary"])

        assertTrue(NanoExpressionEvaluator.evaluateCondition("state.current_day == 1", snapshot))
    }

    @Test
    fun `stateMutation SET should update int and bool`() {
        val source = """
component Demo:
    state:
        current_day: int = 1
        show_summary: bool = false

    VStack:
        Text(\"x\")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        val runtime = NanoStateRuntime(ir)

        runtime.apply(
            NanoActionIR(
                type = "stateMutation",
                payload = mapOf(
                    "path" to JsonPrimitive("state.current_day"),
                    "operation" to JsonPrimitive("SET"),
                    "value" to JsonPrimitive("2")
                )
            )
        )

        runtime.apply(
            NanoActionIR(
                type = "stateMutation",
                payload = mapOf(
                    "path" to JsonPrimitive("show_summary"),
                    "operation" to JsonPrimitive("SET"),
                    "value" to JsonPrimitive("true")
                )
            )
        )

        val snapshot = runtime.snapshot()
        assertEquals(2, snapshot["current_day"])
        assertEquals(true, snapshot["show_summary"])
    }

    @Test
    fun `sequence should apply actions in order`() {
        val source = """
component Demo:
    state:
        current_day: int = 1
        show_summary: bool = false

    VStack:
        Text(\"x\")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        val runtime = NanoStateRuntime(ir)

        val a1 = JsonObject(
            mapOf(
                "type" to JsonPrimitive("stateMutation"),
                "payload" to JsonObject(
                    mapOf(
                        "path" to JsonPrimitive("current_day"),
                        "operation" to JsonPrimitive("SET"),
                        "value" to JsonPrimitive("3")
                    )
                )
            )
        )

        val a2 = JsonObject(
            mapOf(
                "type" to JsonPrimitive("stateMutation"),
                "payload" to JsonObject(
                    mapOf(
                        "path" to JsonPrimitive("state.show_summary"),
                        "operation" to JsonPrimitive("SET"),
                        "value" to JsonPrimitive("true")
                    )
                )
            )
        )

        runtime.apply(
            NanoActionIR(
                type = "sequence",
                payload = mapOf(
                    "actions" to JsonArray(listOf(a1, a2))
                )
            )
        )

        val snapshot = runtime.snapshot()
        assertEquals(3, snapshot["current_day"])
        assertEquals(true, snapshot["show_summary"])
    }

    @Test
    fun `initial state should support JsonArray defaults`() {
        val source = """
component TravelPlan:
    state:
        flights: list = [
            {
                "flightNumber": "CA1234",
                "price": 1280
            }
        ]

    VStack:
        Text("x")
        """.trimIndent()

        val ir = NanoDSL.toIR(source)
        val runtime = NanoStateRuntime(ir)

        val snapshot = runtime.snapshot()
        val flights = snapshot["flights"] as List<*>
        assertEquals(1, flights.size)

        val first = flights[0] as Map<*, *>
        assertEquals("CA1234", first["flightNumber"])
        val price = first["price"] as Number
        assertEquals(1280, price.toInt())
    }
}
