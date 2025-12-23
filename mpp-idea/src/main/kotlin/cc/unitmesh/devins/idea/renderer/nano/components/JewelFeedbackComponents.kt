package cc.unitmesh.devins.idea.renderer.nano.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cc.unitmesh.xuiper.eval.evaluator.NanoExpressionEvaluator
import cc.unitmesh.xuiper.ir.doubleProp
import cc.unitmesh.xuiper.ir.stringProp
import cc.unitmesh.xuiper.render.stateful.NanoNodeRenderer
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text

/**
 * Jewel feedback components for NanoUI IntelliJ IDEA renderer.
 * Includes: Modal, Alert, Progress, Spinner
 */
object JewelFeedbackComponents {

    private fun resolveText(ctx: JewelContext, propKey: String): String {
        val raw = NanoExpressionEvaluator.resolveStringProp(ctx.node, propKey, ctx.state)
        return NanoExpressionEvaluator.interpolateText(raw, ctx.state)
    }

    val modalRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val ir = ctx.node
            val title = resolveText(ctx, "title")
            var showDialog by remember { mutableStateOf(true) }

            if (showDialog) {
                Dialog(onDismissRequest = { showDialog = false }) {
                    Column(
                        modifier = ctx.payload
                            .clip(RoundedCornerShape(8.dp))
                            .background(JewelTheme.globalColors.panelBackground)
                            .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
                            .padding(16.dp)
                            .widthIn(min = 300.dp, max = 500.dp)
                    ) {
                        if (title.isNotBlank()) {
                            Text(
                                text = title,
                                fontSize = 18.sp,
                                color = JewelTheme.globalColors.text.normal
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        ir.children?.forEach { child ->
                            ctx.renderChild(child, Modifier.fillMaxWidth()).invoke()
                        }

                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            DefaultButton(onClick = { showDialog = false }) {
                                Text("Close")
                            }
                        }
                    }
                }
            }
        }
    }

    val alertRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val message = ctx.node.stringProp("message") ?: ctx.node.stringProp("content") ?: ""
            val variant = ctx.node.stringProp("variant") ?: "info"
            val (bgColor, borderColor) = when (variant) {
                "success" -> Color(0xFF81C784).copy(alpha = 0.1f) to Color(0xFF81C784).copy(alpha = 0.3f)
                "warning" -> Color(0xFFFFB74D).copy(alpha = 0.1f) to Color(0xFFFFB74D).copy(alpha = 0.3f)
                "error" -> Color(0xFFE57373).copy(alpha = 0.1f) to Color(0xFFE57373).copy(alpha = 0.3f)
                else -> Color(0xFF64B5F6).copy(alpha = 0.1f) to Color(0xFF64B5F6).copy(alpha = 0.3f)
            }
            Box(
                modifier = ctx.payload
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(bgColor)
                    .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                    .padding(12.dp)
            ) {
                Text(message, color = JewelTheme.globalColors.text.normal)
            }
        }
    }

    val progressRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val bindPath = ctx.node.bindings?.get("value")?.expression?.removePrefix("state.")
            val boundValue = bindPath?.let { (ctx.state[it] as? Number)?.toFloat() }
            val value = boundValue ?: ctx.node.doubleProp("value")?.toFloat() ?: 0f
            val max = ctx.node.doubleProp("max")?.toFloat() ?: 100f
            val progress = (value / max).coerceIn(0f, 1f)

            Box(
                modifier = ctx.payload
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(JewelTheme.globalColors.borders.normal)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(Color(0xFF64B5F6))
                )
            }
        }
    }

    val spinnerRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            Box(modifier = ctx.payload, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}
