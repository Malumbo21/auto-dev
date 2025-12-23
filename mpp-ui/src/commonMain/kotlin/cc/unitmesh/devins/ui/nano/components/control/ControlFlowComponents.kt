package cc.unitmesh.devins.ui.nano.components.control

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.nano.ComposeNodeContext
import cc.unitmesh.xuiper.eval.evaluator.NanoExpressionEvaluator
import cc.unitmesh.xuiper.render.stateful.NanoNodeRenderer

/**
 * Control flow components for NanoUI Compose renderer.
 * Includes: Conditional, ForLoop, Unknown
 *
 * All components use the unified NanoNodeContext interface.
 */
object ControlFlowComponents {

    val conditionalRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderConditional(ctx) }
    }

    val forLoopRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderForLoop(ctx) }
    }

    val unknownRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderUnknown(ctx) }
    }

    @Composable
    fun RenderConditional(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val condition = ir.condition
        val isVisible = NanoExpressionEvaluator.evaluateCondition(condition, ctx.state)

        if (isVisible) {
            Column(modifier = ctx.payload) {
                ir.children?.forEach { child ->
                    ctx.renderChild(child, Modifier).invoke()
                }
            }
        }
    }

    @Composable
    fun RenderForLoop(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val loop = ir.loop
        val variable = loop?.variable
        val iterableExpr = loop?.iterable?.trim().orEmpty()
        val resolved = if (iterableExpr.isNotBlank()) {
            NanoExpressionEvaluator.resolveAny(iterableExpr, ctx.state)
        } else null

        val items: List<Any?> = when (resolved) {
            is List<*> -> resolved
            else -> emptyList()
        }

        Column(modifier = ctx.payload, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEachIndexed { index, item ->
                // Note: ForLoop creates a new state context for each item
                // The renderChild function uses the original state, so we need to handle this specially
                // For now, we render children with the original context
                // TODO: Consider passing item state through a different mechanism
                ir.children?.forEach { child ->
                    ctx.renderChild(child, Modifier).invoke()
                }
            }
        }
    }

    @Composable
    fun RenderUnknown(ctx: ComposeNodeContext) {
        val border = MaterialTheme.colorScheme.error
        val background = MaterialTheme.colorScheme.errorContainer
        Surface(
            modifier = ctx.payload.border(1.dp, border, RoundedCornerShape(4.dp)),
            color = background
        ) {
            Text(
                text = "Unknown: ${ctx.node.type}",
                modifier = Modifier.padding(8.dp),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
