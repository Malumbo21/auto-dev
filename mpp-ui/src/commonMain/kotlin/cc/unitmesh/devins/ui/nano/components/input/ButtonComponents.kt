package cc.unitmesh.devins.ui.nano.components.input

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cc.unitmesh.agent.subagent.NanoDSLAgent
import cc.unitmesh.agent.subagent.NanoDSLContext
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devins.ui.nano.ComposeNodeContext
import cc.unitmesh.devins.ui.nano.NanoPropsResolver
import cc.unitmesh.llm.KoogLLMService
import cc.unitmesh.xuiper.eval.evaluator.NanoExpressionEvaluator
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.ir.stringProp
import cc.unitmesh.xuiper.render.stateful.NanoNodeRenderer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * Button components for NanoUI Compose renderer.
 * Includes: Button with dynamic dialog support
 *
 * All components use the unified NanoNodeContext interface.
 */
object ButtonComponents {

    val buttonRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderButton(ctx) }
    }

    /**
     * Check if an action type is supported by NanoStateRuntime.
     */
    fun isActionSupported(action: NanoActionIR): Boolean {
        return action.type in setOf("sequence", "stateMutation")
    }

    @Composable
    fun RenderButton(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val rawLabel = NanoPropsResolver.resolveString(ir, "label", ctx.state).ifBlank { "Button" }
        val label = rawLabel
        val intent = ir.stringProp("intent")
        val disabledIf = ir.stringProp("disabled_if")
        val isDisabled = !disabledIf.isNullOrBlank() && NanoExpressionEvaluator.evaluateCondition(disabledIf, ctx.state)
        val onClick = ir.actions?.get("onClick")

        var showDynamicDialog by remember { mutableStateOf(false) }

        val handleClick: () -> Unit = {
            if (!isDisabled) {
                when {
                    onClick == null -> showDynamicDialog = true
                    isActionSupported(onClick) -> ctx.onAction(onClick)
                    else -> showDynamicDialog = true
                }
            }
        }

        when (intent) {
            "secondary" -> OutlinedButton(
                onClick = handleClick,
                enabled = !isDisabled,
                modifier = ctx.payload
            ) {
                Text(label)
            }
            else -> {
                val colors = when (intent) {
                    "danger" -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    else -> ButtonDefaults.buttonColors()
                }
                Button(
                    onClick = handleClick,
                    enabled = !isDisabled,
                    colors = colors,
                    modifier = ctx.payload
                ) {
                    Text(label)
                }
            }
        }

        if (showDynamicDialog) {
            DynamicButtonDialog(
                buttonLabel = label,
                state = ctx.state,
                onDismiss = { showDynamicDialog = false },
                onAction = ctx.onAction
            )
        }
    }

    @Composable
    private fun DynamicButtonDialog(
        buttonLabel: String,
        state: Map<String, Any>,
        onDismiss: () -> Unit,
        onAction: (NanoActionIR) -> Unit
    ) {
        var llmService by remember { mutableStateOf<KoogLLMService?>(null) }
        var generatedIR by remember { mutableStateOf<NanoIR?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            try {
                val wrapper = ConfigManager.load()
                val active = wrapper.getActiveModelConfig()
                if (active != null) {
                    llmService = KoogLLMService(active)
                } else {
                    errorMessage = "No LLM configuration found"
                    isLoading = false
                }
            } catch (e: Exception) {
                errorMessage = "Failed to load LLM config: ${e.message}"
                isLoading = false
            }
        }

        LaunchedEffect(llmService) {
            val service = llmService ?: return@LaunchedEffect

            try {
                val stateDescription = state.entries.joinToString(", ") { "${it.key}=${it.value}" }
                val description = buildString {
                    appendLine("Generate a simple dialog content for a button labeled '$buttonLabel'.")
                    if (stateDescription.isNotBlank()) {
                        appendLine("Current state: $stateDescription")
                    }
                    appendLine("The dialog should provide relevant information or actions related to the button's purpose.")
                    appendLine("Keep it simple with 2-3 components maximum.")
                }

                val agent = NanoDSLAgent(service, maxRetries = 1)
                val context = NanoDSLContext(description = description)
                val result = agent.execute(context) { }

                if (result.success) {
                    val irJson = result.metadata["irJson"]
                    if (irJson != null) {
                        try {
                            generatedIR = Json.decodeFromString<NanoIR>(irJson)
                        } catch (e: Exception) {
                            generatedIR = NanoIR(
                                type = "VStack",
                                props = mapOf("spacing" to JsonPrimitive("md")),
                                children = listOf(
                                    NanoIR(
                                        type = "Text",
                                        props = mapOf("content" to JsonPrimitive(result.content.take(500)))
                                    )
                                )
                            )
                        }
                    } else {
                        generatedIR = NanoIR(
                            type = "VStack",
                            props = mapOf("spacing" to JsonPrimitive("md")),
                            children = listOf(
                                NanoIR(
                                    type = "Text",
                                    props = mapOf("content" to JsonPrimitive(result.content.take(500)))
                                )
                            )
                        )
                    }
                } else {
                    errorMessage = result.content
                }
            } catch (e: Exception) {
                errorMessage = "Generation failed: ${e.message}"
            } finally {
                isLoading = false
            }
        }

        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = buttonLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when {
                        isLoading -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generating content...")
                            }
                        }
                        errorMessage != null -> {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        generatedIR != null -> {
                            RenderGeneratedContent(ir = generatedIR!!, state = state, onAction = onAction)
                        }
                        else -> {
                            Text(text = "No content available", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = onDismiss) { Text("Close") }
                    }
                }
            }
        }
    }

    @Composable
    private fun RenderGeneratedContent(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit
    ) {
        when (ir.type) {
            "VStack" -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ir.children?.forEach { child -> RenderGeneratedContent(child, state, onAction) }
                }
            }
            "HStack" -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ir.children?.forEach { child -> RenderGeneratedContent(child, state, onAction) }
                }
            }
            "Text" -> {
                val content = ir.stringProp("content") ?: ""
                val style = ir.stringProp("style")
                Text(
                    text = NanoExpressionEvaluator.interpolateText(content, state),
                    style = when (style) {
                        "h1" -> MaterialTheme.typography.headlineLarge
                        "h2" -> MaterialTheme.typography.headlineMedium
                        "h3" -> MaterialTheme.typography.headlineSmall
                        "caption" -> MaterialTheme.typography.bodySmall
                        else -> MaterialTheme.typography.bodyMedium
                    }
                )
            }
            "Button" -> {
                val label = ir.stringProp("label") ?: "Button"
                val intent = ir.stringProp("intent")
                val onClick = ir.actions?.get("onClick")

                if (intent == "secondary") {
                    OutlinedButton(onClick = { onClick?.let { onAction(it) } }) { Text(label) }
                } else {
                    Button(onClick = { onClick?.let { onAction(it) } }) { Text(label) }
                }
            }
            "Card" -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        ir.children?.forEach { child -> RenderGeneratedContent(child, state, onAction) }
                    }
                }
            }
            else -> {
                val children = ir.children
                if (!children.isNullOrEmpty()) {
                    Column {
                        children.forEach { child -> RenderGeneratedContent(child, state, onAction) }
                    }
                } else {
                    Text(
                        text = "[${ir.type}]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
