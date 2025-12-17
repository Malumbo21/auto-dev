package cc.unitmesh.devins.ui.nano

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors
import cc.unitmesh.llm.KoogLLMService
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Feedback components for NanoUI Compose renderer.
 * Includes: Modal, Alert, Progress, Spinner
 * 
 * Note: DataChart and DataTable have been moved to [NanoDataComponents]
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
        val rawTitle = ir.props["title"]?.jsonPrimitive?.content
        val closable = ir.props["closable"]?.jsonPrimitive?.booleanOrNull ?: true

        // NanoSpec uses bindings.open for Modal
        val binding = ir.bindings?.get("open")
        val statePath = binding?.expression?.removePrefix("state.")
        val isOpen = statePath?.let { state[it] as? Boolean } ?: true

        val onCloseAction = ir.actions?.get("onClose")

        fun closeModal() {
            when {
                onCloseAction != null -> onAction(onCloseAction)
                statePath != null -> onAction(
                    NanoActionIR(
                        type = "stateMutation",
                        payload = mapOf(
                            "path" to JsonPrimitive(statePath),
                            "operation" to JsonPrimitive("SET"),
                            "value" to JsonPrimitive("false")
                        )
                    )
                )
            }
        }

        // Optional LLM fallback if Modal has no title and no children (content missing)
        var llmService by remember { mutableStateOf<KoogLLMService?>(null) }
        var generatedTitle by remember(ir.type, ir.props) { mutableStateOf<String?>(null) }
        var generatedBody by remember(ir.type, ir.props) { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            try {
                val wrapper = ConfigManager.load()
                val active = wrapper.getActiveModelConfig()
                if (active != null) {
                    llmService = KoogLLMService(active)
                }
            } catch (_: Exception) {
                llmService = null
            }
        }

        LaunchedEffect(isOpen, rawTitle, ir.children, llmService) {
            if (isOpen && rawTitle.isNullOrBlank() && (ir.children.isNullOrEmpty()) && generatedTitle == null && llmService != null) {
                val prompt = "Generate a short modal dialog title and one-sentence body for a UI. Return as: Title: ...\\nBody: ..."
                val response = llmService!!.sendPrompt(prompt)
                val titleLine = response.lineSequence().firstOrNull { it.trim().startsWith("Title:") }
                val bodyLine = response.lineSequence().firstOrNull { it.trim().startsWith("Body:") }
                generatedTitle = titleLine?.substringAfter("Title:")?.trim()?.takeIf { it.isNotEmpty() }
                generatedBody = bodyLine?.substringAfter("Body:")?.trim()?.takeIf { it.isNotEmpty() }
            }
        }

        if (isOpen) {
            Dialog(
                onDismissRequest = {
                    if (closable) closeModal()
                }
            ) {
                Surface(
                    modifier = modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 6.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val title = rawTitle ?: generatedTitle
                            if (!title.isNullOrBlank()) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            if (closable) {
                                IconButton(onClick = { closeModal() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                        }

                        if (!rawTitle.isNullOrBlank() || !generatedTitle.isNullOrBlank() || closable) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        val children = ir.children
                        if (!children.isNullOrEmpty()) {
                            children.forEach { child -> renderNode(child, state, onAction, Modifier) }
                        } else {
                            val body = generatedBody
                            if (!body.isNullOrBlank()) {
                                Text(body, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun RenderAlert(
        ir: NanoIR,
        modifier: Modifier,
        onAction: (NanoActionIR) -> Unit = {}
    ) {
        val type = ir.props["type"]?.jsonPrimitive?.content ?: "info"
        val rawMessage = ir.props["message"]?.jsonPrimitive?.content ?: ""
        val closable = ir.props["closable"]?.jsonPrimitive?.booleanOrNull ?: false
        val onCloseAction = ir.actions?.get("onClose")

        // Optional LLM fallback if message is missing
        var llmService by remember { mutableStateOf<KoogLLMService?>(null) }
        var generatedMessage by remember(type) { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            try {
                val wrapper = ConfigManager.load()
                val active = wrapper.getActiveModelConfig()
                if (active != null) {
                    llmService = KoogLLMService(active)
                }
            } catch (_: Exception) {
                llmService = null
            }
        }

        LaunchedEffect(rawMessage, type, llmService) {
            if (rawMessage.isBlank() && generatedMessage == null && llmService != null) {
                val prompt = "Generate a short ${type} alert message for a UI (<= 80 chars)."
                val response = llmService!!.sendPrompt(prompt)
                generatedMessage = response.trim().lineSequence().firstOrNull()?.take(80)
            }
        }

        val message = if (rawMessage.isNotBlank()) rawMessage else (generatedMessage ?: "")

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
            modifier = modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
            color = backgroundColor,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = type, tint = borderColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(message, color = borderColor, modifier = Modifier.weight(1f))
                if (closable) {
                    IconButton(
                        onClick = {
                            if (onCloseAction != null) onAction(onCloseAction)
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = borderColor)
                    }
                }
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

    /**
     * @deprecated Moved to [NanoDataComponents.RenderDataChart]
     */
    @Deprecated(
        message = "Use NanoDataComponents.RenderDataChart instead",
        replaceWith = ReplaceWith("NanoDataComponents.RenderDataChart(ir, state, modifier)", "cc.unitmesh.devins.ui.nano.NanoDataComponents")
    )
    @Composable
    fun RenderDataChart(ir: NanoIR, state: Map<String, Any>, modifier: Modifier) {
        NanoDataComponents.RenderDataChart(ir, state, modifier)
    }

    /**
     * @deprecated Moved to [NanoDataComponents.RenderDataTable]
     */
    @Deprecated(
        message = "Use NanoDataComponents.RenderDataTable instead",
        replaceWith = ReplaceWith("NanoDataComponents.RenderDataTable(ir, state, onAction, modifier)", "cc.unitmesh.devins.ui.nano.NanoDataComponents")
    )
    @Composable
    fun RenderDataTable(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        NanoDataComponents.RenderDataTable(ir, state, onAction, modifier)
    }
}
