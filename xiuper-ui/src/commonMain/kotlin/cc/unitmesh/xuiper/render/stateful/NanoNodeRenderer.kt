package cc.unitmesh.xuiper.render.stateful

import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR

/**
 * NanoNodeRenderer - Single component renderer interface
 *
 * Defines the contract for rendering a single NanoIR node.
 * Platform implementations provide concrete renderers for each component type.
 *
 * Type parameters:
 * - [P]: Payload type (e.g., Compose Modifier, CSS class string)
 * - [R]: Render result type (e.g., @Composable () -> Unit, HTML string)
 *
 * Example usage:
 * ```kotlin
 * // Compose implementation
 * class ComposeTextRenderer : NanoNodeRenderer<Modifier, @Composable () -> Unit> {
 *     override fun render(context: NanoNodeContext<Modifier, @Composable () -> Unit>): @Composable () -> Unit {
 *         return { Text(context.node.stringProp("content") ?: "") }
 *     }
 * }
 * ```
 */
fun interface NanoNodeRenderer<P, R> {
    /**
     * Render a single NanoIR node.
     *
     * @param context The rendering context containing node, state, action handler, and child renderer
     * @return Platform-specific render result
     */
    fun render(context: NanoNodeContext<P, R>): R
}

/**
 * NanoNodeContext - Context passed to each node renderer
 *
 * Contains all information needed to render a single node:
 * - The NanoIR node itself
 * - Current state snapshot
 * - Action dispatcher for handling user interactions
 * - Payload (platform-specific data like Modifier)
 * - Child renderer function for recursive rendering
 */
data class NanoNodeContext<P, R>(
    /** The NanoIR node to render */
    val node: NanoIR,
    
    /** Current state snapshot */
    val state: Map<String, Any>,
    
    /** Action dispatcher for handling user interactions */
    val onAction: (NanoActionIR) -> Unit,
    
    /** Platform-specific payload (e.g., Compose Modifier) */
    val payload: P,
    
    /** Function to render child nodes recursively */
    val renderChild: (NanoIR, P) -> R
) {
    /**
     * Convenience method to render all children with the same payload
     */
    fun renderChildren(): List<R> {
        return node.children?.map { child -> renderChild(child, payload) } ?: emptyList()
    }
    
    /**
     * Convenience method to render all children with a custom payload
     */
    fun renderChildrenWith(childPayload: P): List<R> {
        return node.children?.map { child -> renderChild(child, childPayload) } ?: emptyList()
    }
}
