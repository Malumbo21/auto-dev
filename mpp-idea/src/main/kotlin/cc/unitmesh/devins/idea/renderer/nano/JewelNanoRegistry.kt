package cc.unitmesh.devins.idea.renderer.nano

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.xuiper.eval.evaluator.NanoExpressionEvaluator
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.ir.booleanProp
import cc.unitmesh.xuiper.ir.doubleProp
import cc.unitmesh.xuiper.ir.stringProp
import cc.unitmesh.xuiper.props.NanoSpacingUtils
import cc.unitmesh.xuiper.render.stateful.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*

// Type aliases for cleaner code (must be at top level in Kotlin)
private typealias JewelRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit>
private typealias JewelContext = NanoNodeContext<Modifier, @Composable () -> Unit>

/**
 * JewelNanoRegistry - IntelliJ IDEA Jewel-specific component registry
 *
 * Provides a pre-configured NanoNodeRegistry for Jewel (IntelliJ IDEA) rendering.
 * Maps all standard NanoDSL component types to their Jewel implementations.
 *
 * This registry enables mpp-idea to reuse the entire stateful rendering mechanism
 * from xiuper-ui while providing native IntelliJ look and feel.
 *
 * Usage:
 * ```kotlin
 * val registry = JewelNanoRegistry.create()
 * val dispatcher = StatefulNanoNodeDispatcher(registry)
 * dispatcher.render(session, ir, Modifier)
 * ```
 */
object JewelNanoRegistry {

    /**
     * Create a standard Jewel registry with all built-in components.
     */
    fun create(): NanoNodeRegistry<Modifier, @Composable () -> Unit> {
        return NanoNodeRegistry.build {
            // Layout
            register(NanoComponentTypes.VSTACK, vstackRenderer)
            register(NanoComponentTypes.HSTACK, hstackRenderer)

            // Container
            register(NanoComponentTypes.CARD, cardRenderer)
            register(NanoComponentTypes.FORM, formRenderer)
            register(NanoComponentTypes.COMPONENT, componentRenderer)

            // Content
            register(NanoComponentTypes.TEXT, textRenderer)
            register(NanoComponentTypes.BADGE, badgeRenderer)
            register(NanoComponentTypes.ICON, iconRenderer)
            register(NanoComponentTypes.DIVIDER, dividerRenderer)

            // Input
            register(NanoComponentTypes.BUTTON, buttonRenderer)
            register(NanoComponentTypes.CHECKBOX, checkboxRenderer)

            // Selection (using JewelSelectionRenderer)
            register(NanoComponentTypes.SELECT, selectRenderer)
            register(NanoComponentTypes.RADIO, radioRenderer)
            register(NanoComponentTypes.RADIO_GROUP, radioGroupRenderer)

            // Feedback
            register(NanoComponentTypes.ALERT, alertRenderer)
            register(NanoComponentTypes.PROGRESS, progressRenderer)
            register(NanoComponentTypes.SPINNER, spinnerRenderer)

            // Fallback
            fallback(unknownRenderer)
        }
    }

    /**
     * Create an extended registry with additional custom renderers.
     */
    fun createExtended(
        additionalRenderers: Map<String, NanoNodeRenderer<Modifier, @Composable () -> Unit>>
    ): NanoNodeRegistry<Modifier, @Composable () -> Unit> {
        return create().extend(additionalRenderers)
    }

    private fun resolveText(ir: NanoIR, propKey: String, state: Map<String, Any>): String {
        val raw = NanoExpressionEvaluator.resolveStringProp(ir, propKey, state)
        return NanoExpressionEvaluator.interpolateText(raw, state)
    }

    // ==================== Layout Renderers ====================

    private val vstackRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val spacing = NanoSpacingUtils.parseSpacing(ctx.node.stringProp("spacing"), default = 8).dp
            Column(
                modifier = ctx.payload,
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                ctx.node.children?.forEach { child ->
                    ctx.renderChild(child, Modifier).invoke()
                }
            }
        }
    }

    private val hstackRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val spacing = NanoSpacingUtils.parseSpacing(ctx.node.stringProp("spacing"), default = 8).dp
            val justify = ctx.node.stringProp("justify") ?: "start"
            Row(
                modifier = ctx.payload,
                horizontalArrangement = when (justify) {
                    "between" -> Arrangement.SpaceBetween
                    "around" -> Arrangement.SpaceAround
                    "evenly" -> Arrangement.SpaceEvenly
                    "center" -> Arrangement.Center
                    "end" -> Arrangement.End
                    else -> Arrangement.spacedBy(spacing)
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                ctx.node.children?.forEach { child ->
                    ctx.renderChild(child, Modifier).invoke()
                }
            }
        }
    }

    // ==================== Container Renderers ====================

    private val cardRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val padding = NanoSpacingUtils.parsePadding(ctx.node.stringProp("padding"), default = 16).dp
            Box(
                modifier = ctx.payload
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
                    .background(JewelTheme.globalColors.panelBackground)
                    .padding(padding)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ctx.node.children?.forEach { child ->
                        ctx.renderChild(child, Modifier).invoke()
                    }
                }
            }
        }
    }

    private val formRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            Column(
                modifier = ctx.payload,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ctx.node.children?.forEach { child ->
                    ctx.renderChild(child, Modifier.fillMaxWidth()).invoke()
                }
            }
        }
    }

    private val componentRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            Column(modifier = ctx.payload) {
                ctx.node.children?.forEach { child ->
                    ctx.renderChild(child, Modifier).invoke()
                }
            }
        }
    }

    // ==================== Content Renderers ====================

    private val textRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val content = resolveText(ctx.node, propKey = "content", state = ctx.state)
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

    private val badgeRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val content = resolveText(ctx.node, propKey = "content", state = ctx.state)
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

    private val iconRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
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
            Text(text = iconText, modifier = ctx.payload)
        }
    }

    private val dividerRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            Box(
                modifier = ctx.payload
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(JewelTheme.globalColors.borders.normal)
            )
        }
    }

    // ==================== Input Renderers ====================

    private val buttonRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val label = resolveText(ctx.node, propKey = "label", state = ctx.state).ifBlank { "Button" }
            val variant = ctx.node.stringProp("variant") ?: "primary"
            val onClick: () -> Unit = {
                ctx.node.actions?.get("onClick")?.let { ctx.onAction(it) }
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
        }
    }

    private val checkboxRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val label = resolveText(ctx.node, propKey = "label", state = ctx.state)
            val bindPath = ctx.node.bindings?.get("checked")?.expression?.removePrefix("state.")
            val initialChecked = bindPath?.let { ctx.state[it] as? Boolean } ?: ctx.node.booleanProp("checked") ?: false
            var checked by remember(initialChecked) { mutableStateOf(initialChecked) }
            Row(
                modifier = ctx.payload,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { newValue ->
                        checked = newValue
                        ctx.node.actions?.get("onChange")?.let { ctx.onAction(it) }
                    }
                )
                if (label.isNotEmpty()) {
                    Text(label, color = JewelTheme.globalColors.text.normal)
                }
            }
        }
    }

    // ==================== Selection Renderers ====================

    private val selectRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        { JewelSelectionRenderer.RenderSelect(ctx.node, ctx.state, ctx.onAction, ctx.payload) }
    }

    private val radioRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        { JewelSelectionRenderer.RenderRadio(ctx.node, ctx.state, ctx.onAction, ctx.payload) }
    }

    private val radioGroupRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            JewelSelectionRenderer.RenderRadioGroup(ctx.node, ctx.state, ctx.onAction, ctx.payload) { child, s, a, m ->
                ctx.renderChild(child, m).invoke()
            }
        }
    }

    // ==================== Feedback Renderers ====================

    private val alertRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
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

    private val progressRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
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

    private val spinnerRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        { CircularProgressIndicator(modifier = ctx.payload.size(24.dp)) }
    }

    // ==================== Fallback Renderer ====================

    private val unknownRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            Box(
                modifier = ctx.payload
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFFB74D).copy(alpha = 0.1f))
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
