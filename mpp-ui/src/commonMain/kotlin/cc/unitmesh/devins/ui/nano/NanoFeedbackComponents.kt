package cc.unitmesh.devins.ui.nano

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Feedback components for NanoUI Compose renderer.
 * Includes: Modal, Alert, Progress, Spinner, DataChart, DataTable
 */
object NanoFeedbackComponents {

    @Composable
    fun RenderModal(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier,
        renderNode: @Composable (NanoIR, Map<String, Any>, (NanoActionIR) -> Unit, Modifier) -> Unit
    ) {
        val title = ir.props["title"]?.jsonPrimitive?.content
        val binding = ir.bindings?.get("visible")
        val statePath = binding?.expression?.removePrefix("state.")
        val isVisible = state[statePath] as? Boolean ?: true

        if (isVisible) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    ir.children?.forEach { child -> renderNode(child, state, onAction, Modifier) }
                }
            }
        }
    }

    @Composable
    fun RenderAlert(ir: NanoIR, modifier: Modifier) {
        val type = ir.props["type"]?.jsonPrimitive?.content ?: "info"
        val message = ir.props["message"]?.jsonPrimitive?.content ?: ""

        val backgroundColor = when (type) {
            "success" -> AutoDevColors.Signal.success.copy(alpha = 0.15f)
            "warning" -> AutoDevColors.Signal.warn.copy(alpha = 0.15f)
            "error" -> AutoDevColors.Signal.error.copy(alpha = 0.15f)
            else -> AutoDevColors.Signal.info.copy(alpha = 0.15f)
        }
        val borderColor = when (type) {
            "success" -> AutoDevColors.Signal.success
            "warning" -> AutoDevColors.Signal.warn
            "error" -> AutoDevColors.Signal.error
            else -> AutoDevColors.Signal.info
        }
        val icon = when (type) {
            "success" -> Icons.Default.CheckCircle
            "warning" -> Icons.Default.Warning
            "error" -> Icons.Default.Error
            else -> Icons.Default.Info
        }

        Surface(
            modifier = modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(8.dp)),
            color = backgroundColor,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = type, tint = borderColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(message, color = borderColor)
            }
        }
    }

    @Composable
    fun RenderProgress(ir: NanoIR, state: Map<String, Any>, modifier: Modifier) {
        val valueStr = ir.props["value"]?.jsonPrimitive?.content
        val maxStr = ir.props["max"]?.jsonPrimitive?.content
        val showText = ir.props["showText"]?.jsonPrimitive?.booleanOrNull ?: true

        // Resolve binding expressions
        val value = NanoRenderUtils.resolveBindingValue(valueStr, state)?.toFloatOrNull() ?: 0f
        val max = NanoRenderUtils.resolveBindingValue(maxStr, state)?.toFloatOrNull() ?: 100f
        val progress = if (max > 0f) (value / max).coerceIn(0f, 1f) else 0f

        Column(modifier = modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            if (showText) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    fun RenderSpinner(ir: NanoIR, modifier: Modifier) {
        val text = ir.props["text"]?.jsonPrimitive?.content

        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            if (text != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(text)
            }
        }
    }

    @Composable
    fun RenderDataChart(ir: NanoIR, state: Map<String, Any>, modifier: Modifier) {
        val chartType = ir.props["type"]?.jsonPrimitive?.content ?: "line"
        val data = ir.props["data"]?.jsonPrimitive?.content

        Surface(
            modifier = modifier.fillMaxWidth().height(200.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Chart: $chartType", style = MaterialTheme.typography.bodyMedium)
                    if (data != null) {
                        Text("Data: $data", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    @Composable
    fun RenderDataTable(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val columns = ir.props["columns"]?.jsonPrimitive?.content
        val data = ir.props["data"]?.jsonPrimitive?.content

        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("DataTable", style = MaterialTheme.typography.titleSmall)
                if (columns != null) Text("Columns: $columns", style = MaterialTheme.typography.bodySmall)
                if (data != null) Text("Data: $data", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
