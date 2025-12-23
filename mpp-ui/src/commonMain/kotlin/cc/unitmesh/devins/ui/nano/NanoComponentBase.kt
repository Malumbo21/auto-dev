package cc.unitmesh.devins.ui.nano

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cc.unitmesh.xuiper.eval.evaluator.NanoExpressionEvaluator
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.render.stateful.NanoNodeContext

/**
 * Type aliases for Compose-specific NanoNodeContext and renderer.
 * These provide cleaner code throughout the component implementations.
 */
typealias ComposeNodeContext = NanoNodeContext<Modifier, @Composable () -> Unit>

/**
 * Legacy render function type for backward compatibility.
 * Used by components that need to render children with full state access.
 */
typealias LegacyRenderNode = @Composable (NanoIR, Map<String, Any>, (NanoActionIR) -> Unit, Modifier) -> Unit

/**
 * Extension function to convert NanoNodeContext to legacy renderNode function.
 * This bridges the new context-based API with existing component implementations.
 */
fun ComposeNodeContext.toLegacyRenderNode(): LegacyRenderNode {
    return { childIr, _, _, childModifier ->
        renderChild(childIr, childModifier).invoke()
    }
}

/**
 * Extension function to render all children using the context.
 */
@Composable
fun ComposeNodeContext.renderAllChildren(childModifier: Modifier = Modifier) {
    node.children?.forEach { child ->
        renderChild(child, childModifier).invoke()
    }
}

/**
 * Helper functions for resolving props with state interpolation.
 */
object NanoPropsResolver {
    /**
     * Resolve a string prop with state interpolation.
     */
    fun resolveString(ir: NanoIR, propKey: String, state: Map<String, Any>): String {
        val raw = NanoExpressionEvaluator.resolveStringProp(ir, propKey, state)
        return NanoExpressionEvaluator.interpolateText(raw, state)
    }

    /**
     * Resolve a string prop without interpolation.
     */
    fun resolveStringRaw(ir: NanoIR, propKey: String, state: Map<String, Any>): String {
        return NanoExpressionEvaluator.resolveStringProp(ir, propKey, state)
    }
}
