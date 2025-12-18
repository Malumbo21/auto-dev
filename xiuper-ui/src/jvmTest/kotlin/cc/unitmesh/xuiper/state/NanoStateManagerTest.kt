package cc.unitmesh.xuiper.state

import cc.unitmesh.xuiper.action.MutationOp
import cc.unitmesh.xuiper.action.NanoAction
import cc.unitmesh.xuiper.ir.NanoStateIR
import cc.unitmesh.xuiper.ir.NanoStateVarIR
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NanoStateManagerTest {

    @Test
    fun `should initialize from IR state definition`() {
        val manager = NanoStateManager()
        
        val stateIR = NanoStateIR(
            variables = mapOf(
                "count" to NanoStateVarIR("int", JsonPrimitive(0)),
                "name" to NanoStateVarIR("string", JsonPrimitive("Hello"))
            )
        )
        
        manager.initFromIR(stateIR)
        
        assertEquals(0, manager["count"])
        assertEquals("Hello", manager["name"])
    }

    @Test
    fun `should handle SET mutation`() {
        val manager = NanoStateManager()
        manager["count"] = 0
        
        manager.dispatch(NanoAction.StateMutation(
            path = "count",
            value = "42",
            operation = MutationOp.SET
        ))
        
        assertEquals(42, manager["count"])
    }

    @Test
    fun `should handle ADD mutation`() {
        val manager = NanoStateManager()
        manager["count"] = 10
        
        manager.dispatch(NanoAction.StateMutation(
            path = "count",
            value = "5",
            operation = MutationOp.ADD
        ))
        
        assertEquals(15, manager["count"])
    }

    @Test
    fun `should handle SUBTRACT mutation`() {
        val manager = NanoStateManager()
        manager["count"] = 10
        
        manager.dispatch(NanoAction.StateMutation(
            path = "count",
            value = "3",
            operation = MutationOp.SUBTRACT
        ))
        
        assertEquals(7, manager["count"])
    }

    @Test
    fun `should handle APPEND mutation for lists`() {
        val manager = NanoStateManager()
        manager["items"] = mutableListOf("a", "b")
        
        manager.dispatch(NanoAction.StateMutation(
            path = "items",
            value = "c",
            operation = MutationOp.APPEND
        ))
        
        assertEquals(listOf("a", "b", "c"), manager["items"])
    }

    @Test
    fun `should evaluate simple state path`() {
        val manager = NanoStateManager()
        manager["count"] = 42
        
        val result = manager.evaluate("state.count")
        
        assertEquals(42, result)
    }

    @Test
    fun `should evaluate f-string interpolation`() {
        val manager = NanoStateManager()
        manager["name"] = "World"
        manager["count"] = 5
        
        val result = manager.evaluate("""f"Hello ${"\${state.name}"}, count is ${"\${state.count}"}"""")
        
        assertEquals("Hello World, count is 5", result)
    }

    @Test
    fun `should parse JsonArray and JsonObject defaults into Kotlin collections`() {
        val manager = NanoStateManager()

        val flights = JsonArray(
            listOf(
                JsonObject(
                    mapOf(
                        "flightNumber" to JsonPrimitive("CA1234"),
                        "price" to JsonPrimitive(1280)
                    )
                ),
                JsonObject(
                    mapOf(
                        "flightNumber" to JsonPrimitive("MU5678"),
                        "price" to JsonPrimitive(1150)
                    )
                )
            )
        )

        val stateIR = NanoStateIR(
            variables = mapOf(
                "selectedFlight" to NanoStateVarIR("dict", JsonObject(emptyMap())),
                "flights" to NanoStateVarIR("list", flights)
            )
        )

        manager.initFromIR(stateIR)

        val parsedFlights = manager["flights"] as List<*>
        assertEquals(2, parsedFlights.size)
        val first = parsedFlights[0] as Map<*, *>
        assertEquals("CA1234", first["flightNumber"])
        assertEquals(1280, first["price"])

        val selected = manager["selectedFlight"] as Map<*, *>
        assertTrue(selected.isEmpty())
        assertFalse(selected.isNotEmpty())
    }

    @Test
    fun `should handle action sequence`() {
        val manager = NanoStateManager()
        manager["a"] = 0
        manager["b"] = 0
        
        manager.dispatch(NanoAction.Sequence(listOf(
            NanoAction.StateMutation("a", MutationOp.SET, "1"),
            NanoAction.StateMutation("b", MutationOp.SET, "2")
        )))
        
        assertEquals(1, manager["a"])
        assertEquals(2, manager["b"])
    }

    @Test
    fun `should notify external action handlers`() {
        val manager = NanoStateManager()
        var receivedAction: NanoAction? = null
        
        manager.onAction { action ->
            receivedAction = action
        }
        
        val action = NanoAction.Navigate("/home")
        manager.dispatch(action)
        
        assertTrue(receivedAction is NanoAction.Navigate)
        assertEquals("/home", (receivedAction as NanoAction.Navigate).to)
    }

    @Test
    fun `should handle navigation action`() {
        val manager = NanoStateManager()
        
        manager.dispatch(NanoAction.Navigate("/profile"))
        
        assertEquals("/profile", manager["__navigation__"])
    }

    @Test
    fun `should handle show toast action`() {
        val manager = NanoStateManager()
        
        manager.dispatch(NanoAction.ShowToast("Success!"))
        
        assertEquals("Success!", manager["__toast__"])
    }

    @Test
    fun `should create derived values`() {
        val manager = NanoStateManager()
        manager["count"] = 10
        
        var derivedValue: Any? = null
        manager.derived("state.count") { value ->
            derivedValue = value
        }
        
        // Initial value
        assertEquals(10, derivedValue)
        
        // Update propagates
        manager["count"] = 20
        assertEquals(20, derivedValue)
    }
}

