package cc.unitmesh.devins.idea.renderer.nano.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.xuiper.eval.evaluator.NanoExpressionEvaluator
import cc.unitmesh.xuiper.ir.stringProp
import cc.unitmesh.xuiper.render.stateful.NanoNodeRenderer
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import java.awt.Desktop
import java.net.URI

/**
 * Jewel content components for NanoUI IntelliJ IDEA renderer.
 * Includes: Text, Badge, Icon, Divider, Code, Link, Blockquote
 */
object JewelContentComponents {

    private fun resolveText(ctx: JewelContext, propKey: String): String {
        val raw = NanoExpressionEvaluator.resolveStringProp(ctx.node, propKey, ctx.state)
        return NanoExpressionEvaluator.interpolateText(raw, ctx.state)
    }

    val textRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val content = resolveText(ctx, "content")
            val style = ctx.node.stringProp("style") ?: "body"
            Text(
                text = content,
                modifier = ctx.payload,
                color = JewelTheme.globalColors.text.normal,
                fontSize = when (style) {
                    "h1" -> 24.sp
                    "h2" -> 20.sp
                    "h3" -> 18.sp
                    "h4" -> 16.sp
                    "caption" -> 12.sp
                    else -> 14.sp
                },
                fontWeight = when (style) {
                    "h1", "h2", "h3", "h4" -> FontWeight.Bold
                    else -> FontWeight.Normal
                }
            )
        }
    }

    val badgeRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val content = resolveText(ctx, "content")
            val variant = ctx.node.stringProp("variant") ?: "default"
            val bgColor = when (variant) {
                "success" -> Color(0xFF81C784).copy(alpha = 0.2f)
                "warning" -> Color(0xFFFFB74D).copy(alpha = 0.2f)
                "error" -> Color(0xFFE57373).copy(alpha = 0.2f)
                "info" -> Color(0xFF64B5F6).copy(alpha = 0.2f)
                else -> JewelTheme.globalColors.panelBackground
            }
            Box(
                modifier = ctx.payload
                    .clip(RoundedCornerShape(4.dp))
                    .background(bgColor)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = content,
                    color = JewelTheme.globalColors.text.normal,
                    fontSize = 12.sp
                )
            }
        }
    }

    val iconRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val name = ctx.node.stringProp("name") ?: "circle"
            val iconText = when (name) {
                "check" -> "OK"
                "close", "x" -> "X"
                "warning" -> "!"
                "info" -> "i"
                "error" -> "X"
                "success" -> "OK"
                "train" -> "[T]"
                "hotel" -> "[H]"
                "food" -> "[F]"
                "ticket" -> "[K]"
                else -> "o"
            }
            Text(text = iconText, modifier = ctx.payload, color = JewelTheme.globalColors.text.normal)
        }
    }

    val dividerRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            Box(
                modifier = ctx.payload
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(JewelTheme.globalColors.borders.normal)
            )
        }
    }

    val codeRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val content = resolveText(ctx, "content")
            val language = ctx.node.stringProp("language") ?: ""
            val inline = ctx.node.stringProp("inline")?.toBoolean() ?: false

            if (inline) {
                Text(
                    text = content,
                    modifier = ctx.payload
                        .clip(RoundedCornerShape(4.dp))
                        .background(JewelTheme.globalColors.panelBackground)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = JewelTheme.globalColors.text.normal
                )
            } else {
                Column(
                    modifier = ctx.payload
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(JewelTheme.globalColors.panelBackground)
                        .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(4.dp))
                ) {
                    if (language.isNotBlank()) {
                        Text(
                            text = language,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                        )
                    }
                    Text(
                        text = content,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = JewelTheme.globalColors.text.normal
                    )
                }
            }
        }
    }

    val linkRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val content = resolveText(ctx, "content")
            val href = ctx.node.stringProp("href") ?: ""
            val label = content.ifBlank { href }

            Text(
                text = label,
                modifier = ctx.payload.clickable {
                    if (href.isNotBlank()) {
                        try {
                            Desktop.getDesktop().browse(URI(href))
                        } catch (_: Exception) {
                            // Ignore errors
                        }
                    }
                },
                color = JewelTheme.globalColors.text.info,
                fontSize = 14.sp
            )
        }
    }

    val blockquoteRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val content = resolveText(ctx, "content")
            Row(
                modifier = ctx.payload
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(JewelTheme.globalColors.borders.normal)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = content,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            }
        }
    }
}
