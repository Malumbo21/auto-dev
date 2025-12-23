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
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.dsl.NanoDSL
import cc.unitmesh.xuiper.render.stateful.StatefulNanoSession
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.CircularProgressIndicator

/**
 * IntelliJ IDEA implementation of NanoRenderer using Jewel components.
 *
 * This renderer parses NanoDSL source code and renders it to Jewel UI components.
 * It provides a native IntelliJ look and feel for NanoDSL previews.
 *
 * Uses [NanoStateRuntime] from mpp-ui for state management, ensuring consistent
 * behavior with other platform renderers.
 *
 * Supported components:
 * - Layout: VStack, HStack, Card, Form, Component
 * - Content: Text, Badge, Icon, Divider
 * - Input: Button, Checkbox
 * - Selection: Select, Radio, RadioGroup (via [JewelSelectionRenderer])
 * - Feedback: Alert, Progress, Spinner
 */
object IdeaNanoRenderer {

    /**
     * Parse NanoDSL source code and render it.
     */
    @Composable
    fun RenderFromSource(
        source: String,
        modifier: Modifier = Modifier
    ) {
        if (source.isBlank()) {
            RenderEmpty(modifier)
            return
        }

        val ir = remember(source) {
            try {
                NanoDSL.toIR(source)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        if (ir == null) {
            RenderError("Failed to parse NanoDSL", modifier)
            return
        }

        Render(ir, modifier)
    }

    /**
     * Render a NanoIR tree with state management.
     * Uses NanoStateRuntime for consistent state handling across platforms.
     */
    @Composable
    fun Render(
        ir: NanoIR,
        modifier: Modifier = Modifier
    ) {
        val sessionResult = remember(ir) { StatefulNanoSession.create(ir) }
        val session = sessionResult.getOrNull()
        val runtimeError = sessionResult.exceptionOrNull()

        if (session == null) {
            RenderError("Init failed: ${runtimeError?.message ?: "Unknown error"}", modifier)
            return
        }

        // Subscribe to declared state keys for recomposition
        val observedKeys = remember(session) { session.observedKeys }
        observedKeys.forEach { key ->
            session.flow(key).collectAsState().value
        }

        val snapshot = session.snapshot()
        val handleAction: (NanoActionIR) -> Unit = { action -> session.apply(action) }

        RenderNode(ir, snapshot, handleAction, modifier)
    }

    @Composable
    private fun RenderNode(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier = Modifier
    ) {
        when (ir.type) {
            // Layout
            "VStack" -> RenderVStack(ir, state, onAction, modifier)
            "HStack" -> RenderHStack(ir, state, onAction, modifier)
            "Card" -> RenderCard(ir, state, onAction, modifier)
            "Form" -> RenderForm(ir, state, onAction, modifier)
            "Component" -> RenderComponent(ir, state, onAction, modifier)
            // Content
            "Text" -> RenderText(ir, state, modifier)
            "Badge" -> RenderBadge(ir, state, modifier)
            "Icon" -> RenderIcon(ir, modifier)
            "Divider" -> RenderDivider(modifier)
            // Input
            "Button" -> RenderButton(ir, state, onAction, modifier)
            "Checkbox" -> RenderCheckbox(ir, state, onAction, modifier)
            // Selection (using JewelSelectionRenderer)
            "Select" -> JewelSelectionRenderer.RenderSelect(ir, state, onAction, modifier)
            "Radio" -> JewelSelectionRenderer.RenderRadio(ir, state, onAction, modifier)
            "RadioGroup" -> JewelSelectionRenderer.RenderRadioGroup(ir, state, onAction, modifier) { child, s, a, m ->
                RenderNode(child, s, a, m)
            }
            // Feedback
            "Alert" -> RenderAlert(ir, modifier)
            "Progress" -> RenderProgress(ir, state, modifier)
            "Spinner" -> RenderSpinner(modifier)
            // Unknown
            else -> RenderUnknown(ir, modifier)
        }
    }

    // Helper to get string prop
    private fun NanoIR.getStringProp(key: String): String? {
        val element = props[key] ?: return null
        return if (element is JsonPrimitive) element.content else element.toString()
    }

    // Helper to get int prop
    private fun NanoIR.getIntProp(key: String): Int? {
        val element = props[key] ?: return null
        return if (element is JsonPrimitive) element.intOrNull else null
    }

    // Helper to get boolean prop
    private fun NanoIR.getBoolProp(key: String): Boolean? {
        val element = props[key] ?: return null
        return if (element is JsonPrimitive) element.booleanOrNull else null
    }

    // Helper to get double prop
    private fun NanoIR.getDoubleProp(key: String): Double? {
        val element = props[key] ?: return null
        return if (element is JsonPrimitive) element.doubleOrNull else null
    }

    // Helper to resolve bindings in text
    private fun resolveBindings(text: String, state: Map<String, Any>): String {
        var result = text
        // Replace ${state.key} patterns with actual values
        val pattern = Regex("""\$\{state\.(\w+)\}""")
        pattern.findAll(text).forEach { match ->
            val key = match.groupValues[1]
            val value = state[key]?.toString() ?: ""
            result = result.replace(match.value, value)
        }
        return result
    }

    // ==================== Layout Components ====================

    @Composable
    private fun RenderVStack(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val spacing = ir.getStringProp("spacing")?.toSpacing() ?: 8.dp

        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            ir.children?.forEach { child ->
                RenderNode(child, state, onAction, Modifier)
            }
        }
    }

    @Composable
    private fun RenderHStack(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val spacing = ir.getStringProp("spacing")?.toSpacing() ?: 8.dp
        val justify = ir.getStringProp("justify") ?: "start"

        Row(
            modifier = modifier,
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
            ir.children?.forEach { child ->
                RenderNode(child, state, onAction, Modifier)
            }
        }
    }

    @Composable
    private fun RenderCard(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val padding = ir.getStringProp("padding")?.toSpacing() ?: 16.dp

        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
                .background(JewelTheme.globalColors.panelBackground)
                .padding(padding)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ir.children?.forEach { child ->
                    RenderNode(child, state, onAction, Modifier)
                }
            }
        }
    }

    @Composable
    private fun RenderForm(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ir.children?.forEach { child ->
                RenderNode(child, state, onAction, Modifier.fillMaxWidth())
            }
        }
    }

    @Composable
    private fun RenderComponent(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        Column(modifier = modifier) {
            ir.children?.forEach { child ->
                RenderNode(child, state, onAction, Modifier)
            }
        }
    }

    // ==================== Content Components ====================

    @Composable
    private fun RenderText(ir: NanoIR, state: Map<String, Any>, modifier: Modifier) {
        val rawContent = ir.getStringProp("content") ?: ""
        val content = resolveBindings(rawContent, state)
        val style = ir.getStringProp("style") ?: "body"

        Text(
            text = content,
            modifier = modifier,
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

    @Composable
    private fun RenderBadge(ir: NanoIR, state: Map<String, Any>, modifier: Modifier) {
        val rawContent = ir.getStringProp("content") ?: ""
        val content = resolveBindings(rawContent, state)
        val variant = ir.getStringProp("variant") ?: "default"

        val bgColor = when (variant) {
            "success" -> Color(0xFF81C784).copy(alpha = 0.2f)
            "warning" -> Color(0xFFFFB74D).copy(alpha = 0.2f)
            "error" -> Color(0xFFE57373).copy(alpha = 0.2f)
            "info" -> Color(0xFF64B5F6).copy(alpha = 0.2f)
            else -> JewelTheme.globalColors.panelBackground
        }

        Box(
            modifier = modifier
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

    @Composable
    private fun RenderIcon(ir: NanoIR, modifier: Modifier) {
        val name = ir.getStringProp("name") ?: "circle"
        // Simple icon representation using emoji/text
        val iconText = when (name) {
            "check" -> "✓"
            "close", "x" -> "✕"
            "warning" -> "⚠"
            "info" -> "ℹ"
            "error" -> "✕"
            "success" -> "✓"
            "train" -> "🚂"
            "hotel" -> "🏨"
            "food" -> "🍽"
            "ticket" -> "🎫"
            else -> "●"
        }
        Text(text = iconText, modifier = modifier)
    }

    @Composable
    private fun RenderDivider(modifier: Modifier) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(JewelTheme.globalColors.borders.normal)
        )
    }


    // ==================== Input Components ====================

    @Composable
    private fun RenderButton(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val rawLabel = ir.getStringProp("label") ?: "Button"
        val label = resolveBindings(rawLabel, state)
        val variant = ir.getStringProp("variant") ?: "primary"

        val onClick: () -> Unit = {
            ir.actions?.get("onClick")?.let { onAction(it) }
        }

        if (variant == "outline" || variant == "secondary") {
            OutlinedButton(onClick = onClick, modifier = modifier) {
                Text(label)
            }
        } else {
            DefaultButton(onClick = onClick, modifier = modifier) {
                Text(label)
            }
        }
    }

    @Composable
    private fun RenderCheckbox(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val label = ir.getStringProp("label") ?: ""
        val bindPath = ir.bindings?.get("checked")?.expression?.removePrefix("state.")
        val initialChecked = bindPath?.let { state[it] as? Boolean } ?: ir.getBoolProp("checked") ?: false
        var checked by remember(initialChecked) { mutableStateOf(initialChecked) }

        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { newValue ->
                    checked = newValue
                    ir.actions?.get("onChange")?.let { onAction(it) }
                }
            )
            if (label.isNotEmpty()) {
                Text(label, color = JewelTheme.globalColors.text.normal)
            }
        }
    }

    // ==================== Feedback Components ====================

    @Composable
    private fun RenderAlert(ir: NanoIR, modifier: Modifier) {
        val message = ir.getStringProp("message") ?: ir.getStringProp("content") ?: ""
        val variant = ir.getStringProp("variant") ?: "info"

        val (bgColor, borderColor) = when (variant) {
            "success" -> Color(0xFF81C784).copy(alpha = 0.1f) to Color(0xFF81C784).copy(alpha = 0.3f)
            "warning" -> Color(0xFFFFB74D).copy(alpha = 0.1f) to Color(0xFFFFB74D).copy(alpha = 0.3f)
            "error" -> Color(0xFFE57373).copy(alpha = 0.1f) to Color(0xFFE57373).copy(alpha = 0.3f)
            else -> Color(0xFF64B5F6).copy(alpha = 0.1f) to Color(0xFF64B5F6).copy(alpha = 0.3f)
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                .padding(12.dp)
        ) {
            Text(message, color = JewelTheme.globalColors.text.normal)
        }
    }

    @Composable
    private fun RenderProgress(ir: NanoIR, state: Map<String, Any>, modifier: Modifier) {
        // Check for binding first
        val bindPath = ir.bindings?.get("value")?.expression?.removePrefix("state.")
        val boundValue = bindPath?.let { (state[it] as? Number)?.toFloat() }
        val value = boundValue ?: ir.getDoubleProp("value")?.toFloat() ?: 0f
        val max = ir.getDoubleProp("max")?.toFloat() ?: 100f
        val progress = (value / max).coerceIn(0f, 1f)

        Box(
            modifier = modifier
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

    @Composable
    private fun RenderSpinner(modifier: Modifier) {
        CircularProgressIndicator(modifier = modifier.size(24.dp))
    }

    // ==================== Utility ====================

    @Composable
    private fun RenderUnknown(ir: NanoIR, modifier: Modifier) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFFFB74D).copy(alpha = 0.1f))
                .padding(8.dp)
        ) {
            Text(
                text = "Unknown: ${ir.type}",
                color = JewelTheme.globalColors.text.normal,
                fontSize = 12.sp
            )
        }
    }

    @Composable
    private fun RenderEmpty(modifier: Modifier) {
        Box(
            modifier = modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No NanoDSL content",
                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
            )
        }
    }

    @Composable
    private fun RenderError(message: String, modifier: Modifier) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE57373).copy(alpha = 0.1f))
                .padding(16.dp)
        ) {
            Text(
                text = message,
                color = JewelTheme.globalColors.text.normal
            )
        }
    }

    // ==================== Helper Functions ====================

    private fun String.toSpacing() = when (this) {
        "xs" -> 4.dp
        "sm" -> 8.dp
        "md" -> 16.dp
        "lg" -> 24.dp
        "xl" -> 32.dp
        else -> toIntOrNull()?.dp ?: 8.dp
    }
}

