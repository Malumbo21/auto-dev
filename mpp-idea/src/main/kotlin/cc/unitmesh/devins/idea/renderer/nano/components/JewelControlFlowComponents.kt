package cc.unitmesh.devins.idea.renderer.nano.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.xuiper.eval.evaluator.NanoExpressionEvaluator
import cc.unitmesh.xuiper.render.stateful.NanoNodeRenderer
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * Jewel control flow components for NanoUI IntelliJ IDEA renderer.
 * Includes: Conditional, ForLoop, Unknown
 */
object JewelControlFlowComponents {

    val conditionalRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
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
    }

    val forLoopRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val ir = ctx.node
            val loop = ir.loop
            val iterableExpr = loop?.iterable?.trim().orEmpty()
            val resolved = if (iterableExpr.isNotBlank()) {
                NanoExpressionEvaluator.resolveAny(iterableExpr, ctx.state)
            } else null

            val items: List<Any?> = when (resolved) {
                is List<*> -> resolved
                else -> emptyList()
            }

            Column(modifier = ctx.payload, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEachIndexed { _, _ ->
                    ir.children?.forEach { child ->
                        ctx.renderChild(child, Modifier).invoke()
                    }
                }
            }
        }
    }

    val unknownRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            Box(
                modifier = ctx.payload
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFFB74D).copy(alpha = 0.1f))
                    .border(1.dp, Color(0xFFFFB74D).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "Unknown: ${ctx.node.type}",
                    color = JewelTheme.globalColors.text.normal,
                    fontSize = 12.sp
                )
            }
        }
    }
}
