package cc.unitmesh.xuiper.render.stateful

import cc.unitmesh.xuiper.ir.NanoIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NanoNodeRegistryTest {

    @Test
    fun `registry should return registered renderer for known type`() {
        val textRenderer = NanoNodeRenderer<String, String> { ctx ->
            "Text: ${ctx.node.type}"
        }
        val fallbackRenderer = NanoNodeRenderer<String, String> { ctx ->
            "Unknown: ${ctx.node.type}"
        }

        val registry = NanoNodeRegistry(
            renderers = mapOf("Text" to textRenderer),
            fallbackRenderer = fallbackRenderer
        )

        assertTrue(registry.hasRenderer("Text"))
        assertFalse(registry.hasRenderer("Button"))
        assertEquals(setOf("Text"), registry.registeredTypes())
    }

    @Test
    fun `registry should return fallback renderer for unknown type`() {
        val textRenderer = NanoNodeRenderer<String, String> { ctx ->
            "Text: ${ctx.node.type}"
        }
        val fallbackRenderer = NanoNodeRenderer<String, String> { ctx ->
            "Unknown: ${ctx.node.type}"
        }

        val registry = NanoNodeRegistry(
            renderers = mapOf("Text" to textRenderer),
            fallbackRenderer = fallbackRenderer
        )

        val unknownNode = NanoIR(type = "CustomWidget")
        val context = NanoNodeContext(
            node = unknownNode,
            state = emptyMap(),
            onAction = {},
            payload = "test",
            renderChild = { _, _ -> "" }
        )

        val renderer = registry.getRenderer("CustomWidget")
        val result = renderer.render(context)
        assertEquals("Unknown: CustomWidget", result)
    }

    @Test
    fun `registry builder should create registry with DSL`() {
        val registry = NanoNodeRegistry.build<String, String> {
            register("Text") { ctx -> "Text: ${ctx.node.type}" }
            register("Button") { ctx -> "Button: ${ctx.node.type}" }
            fallback { ctx -> "Unknown: ${ctx.node.type}" }
        }

        assertTrue(registry.hasRenderer("Text"))
        assertTrue(registry.hasRenderer("Button"))
        assertFalse(registry.hasRenderer("Card"))
        assertEquals(setOf("Text", "Button"), registry.registeredTypes())
    }

    @Test
    fun `registry extend should add new renderers`() {
        val baseRegistry = NanoNodeRegistry.build<String, String> {
            register("Text") { ctx -> "Text: ${ctx.node.type}" }
            fallback { ctx -> "Unknown: ${ctx.node.type}" }
        }

        val extendedRegistry = baseRegistry.extend(
            mapOf("Button" to NanoNodeRenderer { ctx -> "Button: ${ctx.node.type}" })
        )

        assertTrue(extendedRegistry.hasRenderer("Text"))
        assertTrue(extendedRegistry.hasRenderer("Button"))
    }

    @Test
    fun `dispatcher should render tree with registry`() {
        val registry = NanoNodeRegistry.build<String, String> {
            register("VStack") { ctx ->
                val children = ctx.node.children?.map { child ->
                    ctx.renderChild(child, ctx.payload)
                }?.joinToString(", ") ?: ""
                "VStack[$children]"
            }
            register("Text") { ctx -> "Text" }
            fallback { ctx -> "Unknown(${ctx.node.type})" }
        }

        val dispatcher = StatefulNanoNodeDispatcher(registry)

        val ir = NanoIR(
            type = "VStack",
            children = listOf(
                NanoIR(type = "Text"),
                NanoIR(type = "Text")
            )
        )

        val result = dispatcher.renderStatic(ir, emptyMap(), "payload")
        assertEquals("VStack[Text, Text]", result)
    }

    @Test
    fun `NanoComponentTypes should contain all standard types`() {
        assertTrue(NanoComponentTypes.LAYOUT_TYPES.contains("VStack"))
        assertTrue(NanoComponentTypes.LAYOUT_TYPES.contains("HStack"))
        assertTrue(NanoComponentTypes.CONTAINER_TYPES.contains("Card"))
        assertTrue(NanoComponentTypes.CONTENT_TYPES.contains("Text"))
        assertTrue(NanoComponentTypes.INPUT_TYPES.contains("Button"))
        assertTrue(NanoComponentTypes.FEEDBACK_TYPES.contains("Alert"))
        assertTrue(NanoComponentTypes.DATA_TYPES.contains("DataTable"))
        assertTrue(NanoComponentTypes.CONTROL_FLOW_TYPES.contains("Conditional"))

        // All types should be in ALL_TYPES
        assertTrue(NanoComponentTypes.ALL_TYPES.containsAll(NanoComponentTypes.LAYOUT_TYPES))
        assertTrue(NanoComponentTypes.ALL_TYPES.containsAll(NanoComponentTypes.CONTAINER_TYPES))
        assertTrue(NanoComponentTypes.ALL_TYPES.containsAll(NanoComponentTypes.CONTENT_TYPES))
        assertTrue(NanoComponentTypes.ALL_TYPES.containsAll(NanoComponentTypes.INPUT_TYPES))
    }
}
