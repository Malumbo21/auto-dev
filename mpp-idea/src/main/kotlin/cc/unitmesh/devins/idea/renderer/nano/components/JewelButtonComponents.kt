package cc.unitmesh.devins.idea.renderer.nano.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cc.unitmesh.xuiper.eval.evaluator.NanoExpressionEvaluator
import cc.unitmesh.xuiper.ir.stringProp
import cc.unitmesh.xuiper.render.stateful.NanoNodeRenderer
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

/**
 * Jewel button components for NanoUI IntelliJ IDEA renderer.
 * Includes: Button with dynamic dialog support
 */
object JewelButtonComponents {

    private fun resolveText(ctx: JewelContext, propKey: String): String {
        val raw = NanoExpressionEvaluator.resolveStringProp(ctx.node, propKey, ctx.state)
        return NanoExpressionEvaluator.interpolateText(raw, ctx.state)
    }

    val buttonRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val ir = ctx.node
            val label = resolveText(ctx, "label").ifBlank { "Button" }
            val variant = ir.stringProp("variant") ?: "primary"
            val hasChildren = !ir.children.isNullOrEmpty()

            var showDialog by remember { mutableStateOf(false) }

            val onClick: () -> Unit = {
                if (hasChildren) {
                    showDialog = true
                } else {
                    ir.actions?.get("onClick")?.let { ctx.onAction(it) }
                }
            }

            if (variant == "outline" || variant == "secondary") {
                OutlinedButton(onClick = onClick, modifier = ctx.payload) {
                    Text(label)
                }
            } else {
                DefaultButton(onClick = onClick, modifier = ctx.payload) {
                    Text(label)
                }
            }

            // Dynamic dialog for button with children
            if (showDialog && hasChildren) {
                Dialog(onDismissRequest = { showDialog = false }) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(JewelTheme.globalColors.panelBackground)
                            .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
                            .padding(16.dp)
                            .widthIn(min = 300.dp, max = 500.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 18.sp,
                            color = JewelTheme.globalColors.text.normal
                        )
                        Spacer(Modifier.height(12.dp))

                        ir.children?.forEach { child ->
                            ctx.renderChild(child, Modifier.fillMaxWidth()).invoke()
                        }

                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = { showDialog = false }) {
                                Text("Cancel")
                            }
                            Spacer(Modifier.width(8.dp))
                            DefaultButton(onClick = {
                                showDialog = false
                                ir.actions?.get("onClick")?.let { ctx.onAction(it) }
                            }) {
                                Text("Confirm")
                            }
                        }
                    }
                }
            }
        }
    }
}
