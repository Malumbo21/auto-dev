package cc.unitmesh.devins.ui.nano.components.feedback

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
import cc.unitmesh.devins.ui.nano.ComposeNodeContext
import cc.unitmesh.devins.ui.nano.NanoPropsResolver
import cc.unitmesh.devins.ui.nano.renderAllChildren
import cc.unitmesh.llm.LLMService
import cc.unitmesh.xuiper.action.NanoActionFactory
import cc.unitmesh.xuiper.eval.evaluator.NanoExpressionEvaluator
import cc.unitmesh.xuiper.ir.booleanProp
import cc.unitmesh.xuiper.ir.stringProp
import cc.unitmesh.xuiper.render.stateful.NanoNodeRenderer

/**
 * Feedback components for NanoUI Compose renderer.
 * Includes: Modal, Alert, Progress, Spinner
 *
 * All components use the unified NanoNodeContext interface.
 */
object FeedbackComponents {

    val modalRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderModal(ctx) }
    }

    val alertRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderAlert(ctx) }
    }

    val progressRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderProgress(ctx) }
    }

    val spinnerRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderSpinner(ctx) }
    }

    @Composable
    fun RenderModal(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val rawTitle = ir.stringProp("title")
        val closable = ir.booleanProp("closable") ?: true

        // NanoSpec uses bindings.open for Modal
        val binding = ir.bindings?.get("open")
        val statePath = binding?.expression?.removePrefix("state.")
        val isOpen = statePath?.let { ctx.state[it] as? Boolean } ?: true

        val onCloseAction = ir.actions?.get("onClose")

        fun closeModal() {
            when {
                onCloseAction != null -> ctx.onAction(onCloseAction)
                statePath != null -> ctx.onAction(NanoActionFactory.set(statePath, false))
            }
        }

        // Optional LLM fallback if Modal has no title and no children
        var llmService by remember { mutableStateOf<LLMService?>(null) }
        var generatedTitle by remember(ir.type, ir.props) { mutableStateOf<String?>(null) }
        var generatedBody by remember(ir.type, ir.props) { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            try {
                val wrapper = ConfigManager.load()
                val active = wrapper.getActiveModelConfig()
                if (active != null) {
                    llmService = LLMService(active)
                }
            } catch (_: Exception) {
                llmService = null
            }
        }

        LaunchedEffect(isOpen, rawTitle, ir.children, llmService) {
            val service = llmService
            if (isOpen && rawTitle.isNullOrBlank() && ir.children.isNullOrEmpty() && generatedTitle == null && service != null) {
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
                    modifier = ctx.payload
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
                            ctx.renderAllChildren()
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
    fun RenderAlert(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val type = ir.stringProp("type") ?: "info"
        val rawMessage = NanoPropsResolver.resolveString(ir, "message", ctx.state)
        val closable = ir.booleanProp("closable") ?: false
        val onCloseAction = ir.actions?.get("onClose")

        // Optional LLM fallback if message is missing
        var llmService by remember { mutableStateOf<LLMService?>(null) }
        var generatedMessage by remember(type) { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            try {
                val wrapper = ConfigManager.load()
                val active = wrapper.getActiveModelConfig()
                if (active != null) {
                    llmService = LLMService(active)
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

        val message = rawMessage.ifBlank { generatedMessage ?: "" }

        val (backgroundColor, borderColor) = when (type) {
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
            modifier = ctx.payload
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
                            if (onCloseAction != null) ctx.onAction(onCloseAction)
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = borderColor)
                    }
                }
            }
        }
    }


    @Composable
    fun RenderProgress(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val valueStr = ir.stringProp("value")
        val maxStr = ir.stringProp("max")
        val showText = ir.booleanProp("showText") ?: true

        // Resolve binding / expression values
        val value = NanoExpressionEvaluator.evaluateNumberOrNull(valueStr, ctx.state)?.toFloat() ?: 0f
        val max = NanoExpressionEvaluator.evaluateNumberOrNull(maxStr, ctx.state)?.toFloat() ?: 100f
        val progress = if (max > 0f) (value / max).coerceIn(0f, 1f) else 0f

        Column(modifier = ctx.payload.fillMaxWidth()) {
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
    fun RenderSpinner(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val text = ir.stringProp("text")

        Row(
            modifier = ctx.payload,
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
