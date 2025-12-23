package cc.unitmesh.xuiper.render.stateful

import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR

/**
 * NanoNodeRegistry - Component registry and dispatcher
 *
 * Manages the mapping from component type to renderer.
 * Platform implementations register their renderers here.
 *
 * Design goals:
 * - Type-safe registration of renderers
 * - Fallback handling for unknown components
 * - Easy extension for new component types
 * - Reusable across platforms (Compose, Jewel, HTML, etc.)
 *
 * Example usage:
 * ```kotlin
 * // Create registry with builder DSL
 * val registry = NanoNodeRegistry.build<Modifier, @Composable () -> Unit> {
 *     register("Text") { ctx -> { Text(ctx.node.stringProp("content") ?: "") } }
 *     register("VStack") { ctx -> { Column { ctx.renderChildren().forEach { it() } } } }
 *     fallback { ctx -> { Text("Unknown: ${ctx.node.type}") } }
 * }
 *
 * // Or use the map-based constructor
 * val registry = NanoNodeRegistry(
 *     renderers = mapOf(
 *         "Text" to textRenderer,
 *         "VStack" to vstackRenderer
 *     ),
 *     fallbackRenderer = unknownRenderer
 * )
 * ```
 */
class NanoNodeRegistry<P, R>(
    private val renderers: Map<String, NanoNodeRenderer<P, R>>,
    private val fallbackRenderer: NanoNodeRenderer<P, R>
) {
    /**
     * Get renderer for a component type.
     * Returns fallback renderer if type is not registered.
     */
    fun getRenderer(type: String): NanoNodeRenderer<P, R> {
        return renderers[type] ?: fallbackRenderer
    }

    /**
     * Check if a renderer is registered for a type.
     */
    fun hasRenderer(type: String): Boolean = type in renderers

    /**
     * Get all registered component types.
     */
    fun registeredTypes(): Set<String> = renderers.keys

    /**
     * Create a new registry with additional renderers.
     */
    fun extend(additionalRenderers: Map<String, NanoNodeRenderer<P, R>>): NanoNodeRegistry<P, R> {
        return NanoNodeRegistry(renderers + additionalRenderers, fallbackRenderer)
    }

    /**
     * Create a new registry with a different fallback renderer.
     */
    fun withFallback(newFallback: NanoNodeRenderer<P, R>): NanoNodeRegistry<P, R> {
        return NanoNodeRegistry(renderers, newFallback)
    }

    companion object {
        /**
         * Build a registry using DSL.
         */
        fun <P, R> build(block: RegistryBuilder<P, R>.() -> Unit): NanoNodeRegistry<P, R> {
            val builder = RegistryBuilder<P, R>()
            builder.block()
            return builder.build()
        }
    }
}

/**
 * Builder for NanoNodeRegistry with DSL support.
 */
class RegistryBuilder<P, R> {
    private val renderers = mutableMapOf<String, NanoNodeRenderer<P, R>>()
    private var fallbackRenderer: NanoNodeRenderer<P, R>? = null

    /**
     * Register a renderer for a component type.
     */
    fun register(type: String, renderer: NanoNodeRenderer<P, R>) {
        renderers[type] = renderer
    }

    /**
     * Register multiple renderers at once.
     */
    fun registerAll(vararg pairs: Pair<String, NanoNodeRenderer<P, R>>) {
        pairs.forEach { (type, renderer) -> renderers[type] = renderer }
    }

    /**
     * Set the fallback renderer for unknown component types.
     */
    fun fallback(renderer: NanoNodeRenderer<P, R>) {
        fallbackRenderer = renderer
    }

    internal fun build(): NanoNodeRegistry<P, R> {
        val fb = fallbackRenderer ?: throw IllegalStateException("Fallback renderer must be set")
        return NanoNodeRegistry(renderers.toMap(), fb)
    }
}

/**
 * Extension function to create a dispatcher from a registry.
 */
fun <P, R> NanoNodeRegistry<P, R>.toDispatcher(): StatefulNanoNodeDispatcher<P, R> {
    return StatefulNanoNodeDispatcher(this)
}
