package cc.unitmesh.agent.subagent

import cc.unitmesh.agent.core.SubAgent
import cc.unitmesh.agent.logging.getLogger
import cc.unitmesh.agent.model.AgentDefinition
import cc.unitmesh.agent.model.PromptConfig
import cc.unitmesh.agent.model.RunConfig
import cc.unitmesh.agent.parser.NanoDSLParseResult
import cc.unitmesh.agent.parser.NanoDSLValidationResult
import cc.unitmesh.agent.parser.NanoDSLValidator
import cc.unitmesh.agent.parser.ValidationError
import cc.unitmesh.agent.tool.ToolResult
import cc.unitmesh.agent.tool.schema.DeclarativeToolSchema
import cc.unitmesh.agent.tool.schema.SchemaPropertyBuilder.boolean
import cc.unitmesh.agent.tool.schema.SchemaPropertyBuilder.string
import cc.unitmesh.devins.parser.CodeFence
import cc.unitmesh.llm.KoogLLMService
import cc.unitmesh.llm.ModelConfig
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable

/**
 * NanoDSL Agent - Generates NanoDSL UI code from natural language descriptions.
 *
 * NanoDSL is a Python-style indentation-based DSL for AI-generated UI,
 * prioritizing token efficiency and human readability.
 *
 * Features:
 * - Converts natural language UI descriptions to NanoDSL code
 * - Supports component generation (Card, VStack, HStack, Button, etc.)
 * - Supports state management and event handling
 * - Supports HTTP requests with Fetch action
 *
 * Cross-platform support:
 * - JVM: Full support with prompt templates
 * - JS/WASM: Available via JsNanoDSLAgent wrapper
 */
class NanoDSLAgent(
    private val llmService: KoogLLMService,
    private val promptTemplate: String = DEFAULT_PROMPT,
    private val maxRetries: Int = 3
) : SubAgent<NanoDSLContext, ToolResult.AgentResult>(
    definition = createDefinition()
) {
    private val logger = getLogger("NanoDSLAgent")
    private val validator = NanoDSLValidator()

    override val priority: Int = 50 // Higher priority for UI generation tasks

    override fun validateInput(input: Map<String, Any>): NanoDSLContext {
        val description = input["description"] as? String
            ?: throw IllegalArgumentException("Missing required parameter: description")
        val componentType = input["componentType"] as? String
        val includeState = input["includeState"] as? Boolean ?: true
        val includeHttp = input["includeHttp"] as? Boolean ?: false

        return NanoDSLContext(
            description = description,
            componentType = componentType,
            includeState = includeState,
            includeHttp = includeHttp
        )
    }

    override suspend fun execute(
        input: NanoDSLContext,
        onProgress: (String) -> Unit
    ): ToolResult.AgentResult {
        onProgress("🎨 NanoDSL Agent: Generating UI from description")
        onProgress("Description: ${input.description.take(80)}...")

        var lastGeneratedCode = ""
        var lastErrors: List<ValidationError> = emptyList()
        var irJson: String? = null

        for (attempt in 1..maxRetries) {
            try {
                val prompt = if (attempt == 1) {
                    buildPrompt(input)
                } else {
                    // Include previous errors in retry prompt
                    buildRetryPrompt(input, lastGeneratedCode, lastErrors)
                }

                onProgress(if (attempt == 1) {
                    "Calling LLM for code generation..."
                } else {
                    "Retry attempt $attempt/$maxRetries - fixing validation errors..."
                })

                val responseBuilder = StringBuilder()
                llmService.streamPrompt(
                    userPrompt = prompt,
                    compileDevIns = false
                ).toList().forEach { chunk ->
                    responseBuilder.append(chunk)
                }

                val llmResponse = responseBuilder.toString()

                // Extract NanoDSL code from markdown code fence
                val codeFence = CodeFence.parse(llmResponse)
                val generatedCode = if (codeFence.text.isNotBlank()) {
                    codeFence.text.trim()
                } else {
                    llmResponse.trim()
                }

                lastGeneratedCode = generatedCode

                // Validate the generated code
                val validationResult = validator.validate(generatedCode)
                
                if (validationResult.isValid) {
                    // Try to parse and get IR JSON
                    val parseResult = validator.parse(generatedCode)
                    if (parseResult is NanoDSLParseResult.Success) {
                        irJson = parseResult.irJson
                    }

                    onProgress("✅ Generated ${generatedCode.lines().size} lines of valid NanoDSL code" +
                        (if (attempt > 1) " (after $attempt attempts)" else ""))

                    // Log warnings if any
                    if (validationResult.warnings.isNotEmpty()) {
                        validationResult.warnings.forEach { warning ->
                            onProgress("⚠️ $warning")
                        }
                    }

                    // Return formatted output with nanodsl code fence so LLM can display it to user
                    val formattedContent = buildString {
                        appendLine("I've generated the following NanoDSL UI code based on your description:")
                        appendLine()
                        appendLine("```nanodsl")
                        appendLine(generatedCode)
                        appendLine("```")
                        appendLine()
                        appendLine("This code can be rendered as an interactive UI component.")
                    }

                    return ToolResult.AgentResult(
                        success = true,
                        content = formattedContent,
                        metadata = buildMetadata(input, generatedCode, attempt, irJson, validationResult)
                    )
                } else {
                    // Validation failed, prepare for retry
                    lastErrors = validationResult.errors
                    val errorMessages = validationResult.errors.joinToString("\n") { 
                        "Line ${it.line}: ${it.message}" + (it.suggestion?.let { s -> " ($s)" } ?: "")
                    }
                    
                    if (attempt < maxRetries) {
                        onProgress("⚠️ Validation failed, will retry: $errorMessages")
                        logger.warn { "NanoDSL validation failed (attempt $attempt): $errorMessages" }
                    } else {
                        onProgress("❌ Validation failed after $maxRetries attempts: $errorMessages")
                        logger.error { "NanoDSL validation failed after all retries: $errorMessages" }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "NanoDSL generation failed on attempt $attempt" }
                // Capture exception as pseudo-error for retry prompt context
                lastErrors = listOf(ValidationError("Generation error: ${e.message}", 0))
                if (attempt == maxRetries) {
                    onProgress("❌ Generation failed: ${e.message}")
                    return ToolResult.AgentResult(
                        success = false,
                        content = "Failed to generate NanoDSL: ${e.message}",
                        metadata = mapOf(
                            "error" to (e.message ?: "Unknown error"),
                            "attempts" to attempt.toString()
                        )
                    )
                }
                onProgress("⚠️ Generation error, will retry: ${e.message}")
            }
        }

        // Return the last generated code even if invalid (best effort)
        return ToolResult.AgentResult(
            success = false,
            content = lastGeneratedCode.ifEmpty { "Failed to generate valid NanoDSL code" },
            metadata = mapOf(
                "description" to input.description,
                "attempts" to maxRetries.toString(),
                "validationErrors" to lastErrors.joinToString("; ") { it.message },
                "isValid" to "false"
            )
        )
    }

    /**
     * Build a retry prompt that includes previous errors for self-correction
     */
    private fun buildRetryPrompt(
        input: NanoDSLContext, 
        previousCode: String, 
        errors: List<ValidationError>
    ): String {
        val errorFeedback = buildString {
            appendLine("## Previous Attempt (INVALID)")
            appendLine("```nanodsl")
            appendLine(previousCode)
            appendLine("```")
            appendLine()
            appendLine("## Validation Errors to Fix:")
            errors.forEach { error ->
                appendLine("- Line ${error.line}: ${error.message}")
                error.suggestion?.let { appendLine("  Suggestion: $it") }
            }
            appendLine()
            appendLine("## Instructions:")
            appendLine("Please fix the above errors and generate a corrected version.")
            appendLine("Output ONLY the corrected NanoDSL code, no explanations.")
        }

        return """
${buildPrompt(input)}

$errorFeedback
""".trim()
    }

    /**
     * Build metadata map for the result
     */
    private fun buildMetadata(
        input: NanoDSLContext,
        code: String,
        attempts: Int,
        irJson: String?,
        validationResult: NanoDSLValidationResult
    ): Map<String, String> {
        return buildMap {
            put("description", input.description)
            put("componentType", input.componentType ?: "auto")
            put("linesOfCode", code.lines().size.toString())
            put("includesState", input.includeState.toString())
            put("includesHttp", input.includeHttp.toString())
            put("attempts", attempts.toString())
            put("isValid", validationResult.isValid.toString())
            irJson?.let { put("irJson", it) }
            if (validationResult.warnings.isNotEmpty()) {
                put("warnings", validationResult.warnings.joinToString("; "))
            }
        }
    }

    private fun buildPrompt(input: NanoDSLContext): String {
        val featureHints = buildList {
            if (input.includeState) add("Include state management if needed")
            if (input.includeHttp) add("Include HTTP request actions (Fetch) if applicable")
            input.componentType?.let { add("Focus on creating a $it component") }
        }.joinToString("\n- ")

        return """
$promptTemplate

${if (featureHints.isNotEmpty()) "## Additional Requirements:\n- $featureHints\n" else ""}
## User Request:
${input.description}
""".trim()
    }

    override fun formatOutput(output: ToolResult.AgentResult): String {
        // Content already contains formatted nanodsl code block
        return if (output.success) {
            output.content
        } else {
            "Error generating NanoDSL: ${output.content}"
        }
    }

    override fun getParameterClass(): String = "NanoDSLContext"

    override fun shouldTrigger(context: Map<String, Any>): Boolean {
        val content = context["content"] as? String ?: return false
        val keywords = listOf("ui", "interface", "form", "card", "button", "component", "layout")
        return keywords.any { content.lowercase().contains(it) }
    }

    override suspend fun handleQuestion(
        question: String,
        context: Map<String, Any>
    ): ToolResult.AgentResult {
        // Treat questions as UI generation requests
        return execute(
            NanoDSLContext(description = question),
            onProgress = {}
        )
    }

    override fun getStateSummary(): Map<String, Any> = mapOf(
        "name" to name,
        "description" to description,
        "priority" to priority,
        "supportedFeatures" to listOf("components", "state", "actions", "http-requests")
    )

    companion object {
        private fun createDefinition() = AgentDefinition(
            name = "nanodsl-agent",
            displayName = "NanoDSL Agent",
            description = NanoDSLAgentSchema.description,
            promptConfig = PromptConfig(systemPrompt = DEFAULT_PROMPT),
            modelConfig = ModelConfig.default(),
            runConfig = RunConfig(maxTurns = 3, maxTimeMinutes = 2)
        )

        const val DEFAULT_PROMPT = """You are a NanoDSL expert. Generate UI code using NanoDSL syntax.

## NanoDSL Syntax

NanoDSL uses Python-style indentation (4 spaces) to represent hierarchy.

### Components
- `component Name:` - Define a component
- `VStack(spacing="sm"):` - Vertical stack layout
- `HStack(align="center", justify="between"):` - Horizontal stack layout
- `Card:` - Container with padding/shadow
- `Text("content", style="h1|h2|h3|body|caption")` - Text display
- `Button("label", intent="primary|secondary")` - Clickable button
- `Image(src=path, aspect=16/9, radius="md")` - Image display
- `Input(value=binding, placeholder="...")` - Text input
- `Badge("text", color="green|red|blue")` - Status badge

### Properties
- `padding: "sm|md|lg"` - Padding size
- `shadow: "sm|md|lg"` - Shadow depth
- `spacing: "sm|md|lg"` - Gap between items

### State (for interactive components)
```
state:
    count: int = 0
    name: str = ""
```

### Bindings
- `<<` - One-way binding (subscribe)
- `:=` - Two-way binding

### Actions
- `on_click: state.var += 1` - State mutation
- `Navigate(to="/path")` - Navigation
- `ShowToast("message")` - Show notification
- `Fetch(url="/api/...", method="POST", body={...})` - HTTP request

### HTTP Requests
```
Button("Submit"):
    on_click:
        Fetch(
            url="/api/login",
            method="POST",
            body={"email": state.email, "password": state.password},
            on_success: Navigate(to="/dashboard"),
            on_error: ShowToast("Failed")
        )
```

## Output Rules
1. Output ONLY NanoDSL code, no explanations
2. Use 4 spaces for indentation
3. Keep it minimal - no redundant components
4. Wrap output in ```nanodsl code fence"""
    }
}

/**
 * NanoDSL generation context
 */
@Serializable
data class NanoDSLContext(
    val description: String,
    val componentType: String? = null,
    val includeState: Boolean = true,
    val includeHttp: Boolean = false
)

/**
 * Schema for NanoDSL Agent tool
 */
object NanoDSLAgentSchema : DeclarativeToolSchema(
    description = """Generate Nano DSL UI code from natural language description.

This tool uses a specialized sub-agent to generate token-efficient UI code in NanoDSL syntax.
The generated code will be returned in a ```nanodsl code block that can be rendered as an interactive UI.

IMPORTANT: After calling this tool, you MUST include the returned ```nanodsl code block in your response
to the user so they can see the generated UI. Do not summarize or describe the code - show it directly.

Use this tool when the user asks for:
- UI components (forms, cards, buttons, lists)
- Interactive interfaces
- Data display layouts
- User input forms""",
    properties = mapOf(
        "description" to string(
            description = "Natural language description of the UI to generate. Be specific about components, layout, and interactions needed.",
            required = true
        ),
        "componentType" to string(
            description = "Optional: Specific component type to focus on (card, form, list, dashboard, etc.)",
            required = false
        ),
        "includeState" to boolean(
            description = "Whether to include state management for interactive components (default: true)",
            required = false
        ),
        "includeHttp" to boolean(
            description = "Whether to include HTTP request actions like Fetch for API calls (default: false)",
            required = false
        )
    )
) {
    override fun getExampleUsage(toolName: String): String {
        return """/$toolName description="Create a contact form with name, email, message fields and a submit button that sends to /api/contact" includeHttp=true"""
    }
}

