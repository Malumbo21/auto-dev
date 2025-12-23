package cc.unitmesh.xuiper.render.stateful

import cc.unitmesh.xuiper.ir.NanoIR

/**
 * StatefulNanoNodeDispatcher - Registry-based stateful renderer
 *
 * This is the new, cleaner implementation that uses NanoNodeRegistry
 * for component dispatch. It replaces the callback-based StatefulNanoTreeDispatcher
 * with a more structured approach.
 *
 * Benefits:
 * - Clear separation between registry (what to render) and dispatcher (how to traverse)
 * - Easy to extend with new components
 * - Platform implementations only need to provide a registry
 * - Consistent traversal logic across all platforms
 *
 * Example usage:
 * ```kotlin
 * // Create registry
 * val registry = NanoNodeRegistry.build<Modifier, @Composable () -> Unit> {
 *     register("Text") { ctx -> { Text(ctx.node.stringProp("content") ?: "") } }
 *     register("VStack") { ctx -> { Column { ctx.renderChildren().forEach { it() } } } }
 *     fallback { ctx -> { Text("Unknown: ${ctx.node.type}") } }
 * }
 *
 * // Create dispatcher
 * val dispatcher = StatefulNanoNodeDispatcher(registry)
 *
 * // Render with session
 * val result = dispatcher.render(session, rootIR, Modifier)
 * ```
 */
class StatefulNanoNodeDispatcher<P, R>(
    private val registry: NanoNodeRegistry<P, R>
) {
    /**
     * Render a NanoIR tree with state management.
     *
     * @param session The stateful session containing state and action handling
     * @param root The root NanoIR node to render
     * @param payload Platform-specific payload (e.g., Compose Modifier)
     * @return Platform-specific render result
     */
    fun render(session: StatefulNanoSession, root: NanoIR, payload: P): R {
        val stateSnapshot = session.snapshot()
        
        fun renderNode(node: NanoIR, p: P): R {
            val context = NanoNodeContext(
                node = node,
                state = stateSnapshot,
                onAction = session::apply,
                payload = p,
                renderChild = ::renderNode
            )
            val renderer = registry.getRenderer(node.type)
            return renderer.render(context)
        }
        
        return renderNode(root, payload)
    }

    /**
     * Render a NanoIR tree without session (for static rendering).
     *
     * @param root The root NanoIR node to render
     * @param state Static state snapshot
     * @param payload Platform-specific payload
     * @return Platform-specific render result
     */
    fun renderStatic(root: NanoIR, state: Map<String, Any>, payload: P): R {
        fun renderNode(node: NanoIR, p: P): R {
            val context = NanoNodeContext(
                node = node,
                state = state,
                onAction = { /* no-op for static rendering */ },
                payload = p,
                renderChild = ::renderNode
            )
            val renderer = registry.getRenderer(node.type)
            return renderer.render(context)
        }
        
        return renderNode(root, payload)
    }
}

/**
 * Convenience function to create a dispatcher from a registry builder.
 */
fun <P, R> statefulDispatcher(block: RegistryBuilder<P, R>.() -> Unit): StatefulNanoNodeDispatcher<P, R> {
    val registry = NanoNodeRegistry.build(block)
    return StatefulNanoNodeDispatcher(registry)
}
