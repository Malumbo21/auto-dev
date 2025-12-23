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
import cc.unitmesh.llm.KoogLLMService
import cc.unitmesh.xuiper.action.NanoActionFactory
import cc.unitmesh.xuiper.eval.evaluator.NanoExpressionEvaluator
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.ir.booleanProp
import cc.unitmesh.xuiper.ir.stringProp

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
        val rawTitle = ir.stringProp("title")
        val closable = ir.booleanProp("closable") ?: true

        // NanoSpec uses bindings.open for Modal
        val binding = ir.bindings?.get("open")
        val statePath = binding?.expression?.removePrefix("state.")
        val isOpen = statePath?.let { state[it] as? Boolean } ?: true

        val onCloseAction = ir.actions?.get("onClose")

        fun closeModal() {
            when {
                onCloseAction != null -> onAction(onCloseAction)
                statePath != null -> onAction(NanoActionFactory.set(statePath, false))
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
            val service = llmService
            if (isOpen && rawTitle.isNullOrBlank() && (ir.children.isNullOrEmpty()) && generatedTitle == null && service != null) {
                val prompt = "Generate a short modal dialog title and one-sentence body for a UI. Return as: Title: ...\\nBody: ..."
                val response = service.sendPrompt(prompt)
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
        val type = ir.stringProp("type") ?: "info"
        val rawMessage = ir.stringProp("message") ?: ""
        val closable = ir.booleanProp("closable") ?: false
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
            val service = llmService
            if (rawMessage.isBlank() && generatedMessage == null && service != null) {
                val prompt = "Generate a short ${type} alert message for a UI (<= 80 chars)."
                val response = service.sendPrompt(prompt)
                generatedMessage = response.trim().lineSequence().firstOrNull()?.take(80)
            }
        }

        val message = if (rawMessage.isNotBlank()) rawMessage else (generatedMessage ?: "")

        val (backgroundColor, borderColor) = when (type) {
            // Treat alert types as semantic intents from the active theme.
            "success" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
            "warning" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
            "error" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
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
        val valueStr = ir.stringProp("value")
        val maxStr = ir.stringProp("max")
        val showText = ir.booleanProp("showText") ?: true

            // Resolve binding / expression values
            val value = NanoExpressionEvaluator.evaluateNumberOrNull(valueStr, state)?.toFloat() ?: 0f
            val max = NanoExpressionEvaluator.evaluateNumberOrNull(maxStr, state)?.toFloat() ?: 100f
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
        val text = ir.stringProp("text")

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
}
