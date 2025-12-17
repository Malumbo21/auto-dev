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
import cc.unitmesh.agent.tool.schema.SchemaPropertyBuilder.string
import cc.unitmesh.devins.parser.CodeFence
import cc.unitmesh.llm.ModelConfig
import cc.unitmesh.llm.KoogLLMService
import cc.unitmesh.llm.KoogPromptStreamingServiceAdapter
import cc.unitmesh.llm.PromptStreamingService
import cc.unitmesh.llm.image.ImageGenerationResult
import cc.unitmesh.llm.image.ImageGenerationService
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
    private val llmService: PromptStreamingService,
    private val promptTemplate: String = DEFAULT_PROMPT,
    private val maxRetries: Int = 3,
    private val imageGenerationService: ImageGenerationService? = null
) : SubAgent<NanoDSLContext, ToolResult.AgentResult>(
    definition = createDefinition()
) {

    constructor(
        llmService: KoogLLMService,
        promptTemplate: String = DEFAULT_PROMPT,
        maxRetries: Int = 3,
        imageGenerationService: ImageGenerationService? = null
    ) : this(
        llmService = KoogPromptStreamingServiceAdapter(llmService),
        promptTemplate = promptTemplate,
        maxRetries = maxRetries,
        imageGenerationService = imageGenerationService
    )

    private val logger = getLogger("NanoDSLAgent")
    private val validator = NanoDSLValidator()

    /**
     * Regex pattern to match Image components in NanoDSL code.
     * Matches multiline Image(src="...", ...) or Image(src=..., ...)
     * Uses [\s\S] instead of . to match any character including newlines (JS compatible).
     */
    private val imagePattern = Regex(
        """Image\s*\([\s\S]*?src\s*=\s*["']([^"']+)["']"""
    )

    /**
     * Check if a src value should be replaced with AI-generated image.
     * Since LLM often generates fake/hallucinated URLs, we replace all src values
     * except for data: URLs (which are actual embedded images).
     */
    private fun shouldGenerateImage(src: String): Boolean {
        val trimmed = src.trim()
        // Only skip data: URLs (actual embedded image data)
        // All other URLs (including http/https) should be replaced since they're usually fake
        return !trimmed.startsWith("data:")
    }

    override val priority: Int = 50 // Higher priority for UI generation tasks

    override fun validateInput(input: Map<String, Any>): NanoDSLContext {
        val description = input["description"] as? String
            ?: throw IllegalArgumentException("Missing required parameter: description")

        return NanoDSLContext(description = description)
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

                onProgress(
                    if (attempt == 1) {
                        "Calling LLM for code generation..."
                    } else {
                        "Retry attempt $attempt/$maxRetries - fixing validation errors..."
                    }
                )

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

                    when (parseResult) {
                        is NanoDSLParseResult.Success -> {
                            irJson = parseResult.irJson

                            onProgress(
                                "✅ Generated ${generatedCode.lines().size} lines of valid NanoDSL code" +
                                        (if (attempt > 1) " (after $attempt attempts)" else "")
                            )

                            // Log warnings if any
                            if (validationResult.warnings.isNotEmpty()) {
                                validationResult.warnings.forEach { warning ->
                                    onProgress("⚠️ $warning")
                                }
                            }

                            // Generate images for Image components if ImageGenerationService is available
                            val finalCode = if (imageGenerationService != null) {
                                generateImagesForCode(generatedCode, onProgress)
                            } else {
                                generatedCode
                            }

                            // Return formatted output with nanodsl code fence so LLM can display it to user
                            val formattedContent = buildString {
                                appendLine("I've generated the following NanoDSL UI code based on your description:")
                                appendLine()
                                appendLine("```nanodsl")
                                appendLine(finalCode)
                                appendLine("```")
                                appendLine()
                                appendLine("This code can be rendered as an interactive UI component.")
                            }

                            return ToolResult.AgentResult(
                                success = true,
                                content = formattedContent,
                                metadata = buildMetadata(input, finalCode, attempt, irJson, validationResult)
                            )
                        }

                        is NanoDSLParseResult.Failure -> {
                            // Parse error - code is too complex, need to retry with simpler request
                            val errorMessage = parseResult.errors.joinToString("; ") { it.message }
                            lastErrors = listOf(
                                ValidationError(
                                    message = "Parse failed: $errorMessage. The generated code is too complex.",
                                    line = 0,
                                    suggestion = "Generate simpler code with fewer components and less nesting"
                                )
                            )

                            if (attempt < maxRetries) {
                                onProgress("⚠️ Parse error (code too complex), will retry with simpler approach: $errorMessage")
                                logger.warn { "NanoDSL parse failed (attempt $attempt): $errorMessage" }
                                continue // Continue to next retry
                            } else {
                                onProgress("❌ Parse failed after $maxRetries attempts: $errorMessage")
                                logger.error { "NanoDSL parse failed after all retries: $errorMessage" }
                            }
                        }
                    }
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
     * Generate images for Image components.
     * Replaces all src values (including fake URLs) with actual AI-generated image URLs.
     */
    private suspend fun generateImagesForCode(
        code: String,
        onProgress: (String) -> Unit
    ): String {
        val service = imageGenerationService ?: return code

        // Find all Image components
        val matches = imagePattern.findAll(code).toList()
        val imagesToGenerate = matches.filter { shouldGenerateImage(it.groupValues[1]) }

        if (imagesToGenerate.isEmpty()) {
            return code
        }

        onProgress("🖼️ Generating ${imagesToGenerate.size} image(s)...")

        var resultCode = code
        for (match in imagesToGenerate) {
            val originalSrc = match.groupValues[1]

            // Extract surrounding context for better prompt generation
            // Get ~100 characters before and after the match
            val matchStart = match.range.first
            val matchEnd = match.range.last
            val contextStart = maxOf(0, matchStart - 200)
            val contextEnd = minOf(code.length, matchEnd + 200)
            val surroundingContext = code.substring(contextStart, contextEnd)

            val prompt = extractImagePrompt(originalSrc, surroundingContext)

            onProgress("  Generating image: $prompt")

            when (val result = service.generateImage(prompt)) {
                is ImageGenerationResult.Success -> {
                    // Replace the placeholder src with the generated image URL
                    val originalMatch = match.value
                    val newMatch = originalMatch.replace(originalSrc, result.imageUrl)
                    resultCode = resultCode.replace(originalMatch, newMatch)
                    onProgress("  ✅ Image generated successfully")
                }
                is ImageGenerationResult.Error -> {
                    logger.warn { "Failed to generate image for '$prompt': ${result.message}" }
                    onProgress("  ⚠️ Failed to generate image: ${result.message}")
                    // Keep the original placeholder
                }
            }
        }

        return resultCode
    }

    /**
     * Extract a meaningful prompt from the src value and surrounding context.
     * Works with URLs (including fake Unsplash links), paths, and placeholders.
     */
    private fun extractImagePrompt(src: String, surroundingContext: String = ""): String {
        // First, try to extract meaningful text from the surrounding context
        // Look for nearby Text components that might describe the image
        val contextPrompt = extractContextPrompt(surroundingContext)
        if (contextPrompt.isNotEmpty()) {
            return contextPrompt
        }

        // Handle URLs - try to extract meaningful parts
        val urlCleaned = if (src.startsWith("http://") || src.startsWith("https://")) {
            // For URLs, try to extract path segments that might be meaningful
            val pathPart = src
                .replace(Regex("^https?://[^/]+/"), "") // Remove domain
                .replace(Regex("\\?.*$"), "") // Remove query string
                .replace(Regex("photo-[0-9a-f-]+"), "") // Remove Unsplash photo IDs
                .replace(Regex("[0-9]+x[0-9]+"), "") // Remove dimensions
            pathPart
        } else {
            src
        }

        // Clean up the path/URL
        val cleaned = urlCleaned
            .replace(Regex("^[./]+"), "")
            .replace(Regex("\\.(jpg|jpeg|png|gif|webp|svg)$", RegexOption.IGNORE_CASE), "")
            .replace("/", " ")
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // If it looks like a variable reference (e.g., item.image), extract the meaningful part
        if (cleaned.contains(".")) {
            val parts = cleaned.split(".")
            val extracted = parts.lastOrNull()?.replace(Regex("[^a-zA-Z0-9 ]"), " ")?.trim() ?: cleaned
            if (extracted.isNotEmpty()) return extracted
        }

        // If we got a meaningful string, use it
        if (cleaned.length > 3 && cleaned.any { it.isLetter() }) {
            return cleaned
        }

        // Fallback: return a generic prompt based on context or default
        return "high quality image"
    }

    /**
     * Extract a prompt from surrounding NanoDSL context.
     * Looks for nearby Text components that might describe the image.
     */
    private fun extractContextPrompt(context: String): String {
        if (context.isEmpty()) return ""

        // Look for Text components with meaningful content
        val textPattern = Regex("""Text\s*\(\s*["']([^"']+)["']""")
        val textMatches = textPattern.findAll(context).toList()

        // Filter out common UI labels and get meaningful descriptions
        val meaningfulTexts = textMatches
            .map { it.groupValues[1] }
            .filter { text ->
                text.length > 3 &&
                !text.matches(Regex("^(Click|Submit|Cancel|OK|Yes|No|Close|Open|Edit|Delete|Save|Back|Next|Previous)$", RegexOption.IGNORE_CASE))
            }

        return meaningfulTexts.firstOrNull() ?: ""
    }

    /**
     * Build a retry prompt that includes previous errors for self-correction
     */
    private fun buildRetryPrompt(
        input: NanoDSLContext,
        previousCode: String,
        errors: List<ValidationError>
    ): String {
        // Check if this is a parse error (code too complex)
        val isParseError = errors.any { it.message.contains("Parse failed") || it.message.contains("too complex") }

        val errorFeedback = buildString {
            appendLine("## Previous Attempt (INVALID)")
            appendLine("```nanodsl")
            appendLine(previousCode)
            appendLine("```")
            appendLine()

            if (isParseError) {
                appendLine("## ⚠️ CRITICAL: Code Too Complex")
                appendLine("The previous code was too complex and failed to parse.")
                appendLine()
                appendLine("## Requirements for Simpler Code:")
                appendLine("1. Use FEWER components (aim for 5-8 components maximum)")
                appendLine("2. Reduce nesting depth (maximum 2-3 levels)")
                appendLine("3. Simplify state management (use only essential state variables)")
                appendLine("4. Avoid complex expressions in bindings")
                appendLine("5. Keep actions simple and straightforward")
                appendLine()
                appendLine("## Errors:")
                errors.forEach { error ->
                    appendLine("- ${error.message}")
                    error.suggestion?.let { appendLine("  💡 $it") }
                }
            } else {
                appendLine("## Validation Errors to Fix:")
                errors.forEach { error ->
                    appendLine("- Line ${error.line}: ${error.message}")
                    error.suggestion?.let { appendLine("  Suggestion: $it") }
                }
            }

            appendLine()
            appendLine("## Instructions:")
            if (isParseError) {
                appendLine("Generate a MUCH SIMPLER version that achieves the core functionality.")
                appendLine("Prioritize simplicity over completeness.")
            } else {
                appendLine("Please fix the above errors and generate a corrected version.")
            }
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
            put("linesOfCode", code.lines().size.toString())
            put("attempts", attempts.toString())
            put("isValid", validationResult.isValid.toString())
            irJson?.let { put("irJson", it) }
            if (validationResult.warnings.isNotEmpty()) {
                put("warnings", validationResult.warnings.joinToString("; "))
            }
        }
    }

    private fun buildPrompt(input: NanoDSLContext): String {
        return """
$promptTemplate

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

    ## Layout Guidelines (Important)

    - When using `HStack(justify="between")` with a text column (e.g., `VStack` of `Text`) on one side and an `Image(aspect=...)` on the other, ALWAYS give both sides explicit width control (recommended: `flex`/`weight` on the two children). Otherwise the text column may take almost all width and the image becomes very narrow, making its computed height very small.
    - If the layout is narrow, prefer `wrap="wrap"` (allow wrapping) or use `SplitView` instead of forcing two cramped columns.

    ### Optional Child Sizing
    - Any component inside `HStack` may specify `flex`/`weight` (number) to share available width.

### Layout Components
- `VStack(spacing="sm", align="center")` - Vertical stack layout
    - `HStack(spacing="sm", align="center", justify="between", wrap="wrap")` - Horizontal stack layout (use `wrap` on narrow layouts)
- `Card(padding="md", shadow="sm")` - Container with padding/shadow
- `SplitView(ratio=0.5)` - Split screen layout (left/right panels)

### Display Components
- `Text("content", style="h1|h2|h3|body|caption")` - Text display
- `Image(src="/{scene}.jpg", aspect=16/9, radius="md")` - Image display. Use descriptive scene path like "/singapore-skyline.jpg", "/beach-sunset.jpg". The image will be AI-generated based on the scene name. Do NOT use external URLs.
- `Badge("text", color="green|red|blue")` - Status indicator
- `Icon("name", size="sm|md|lg", color="primary")` - Icon display
- `Divider` - Horizontal line separator

### Form Input Components
- `Input(value=binding, placeholder="...", type="text|email|password")` - Text input
- `TextArea(value=binding, placeholder="...", rows=4)` - Multi-line text input
- `Select(value=binding, options=[...], placeholder="...")` - Dropdown select
- `Checkbox(checked=binding, label="...")` - Checkbox input
- `DatePicker(value=binding, format="YYYY-MM-DD", placeholder="...")` - Date picker
- `Radio(option="value", label="...", name="group")` - Single radio button
- `RadioGroup(value=binding, options=[...], name="group")` - Radio button group
- `Switch(checked=binding, label="...", size="sm|md")` - Toggle switch
- `NumberInput(value=binding, min=0, max=100, step=1, placeholder="...")` - Number input with +/- buttons
- `SmartTextField(label="...", bind=binding, validation="...", placeholder="...")` - Text input with validation
- `Slider(label="...", bind=binding, min=0, max=100, step=1)` - Range slider
- `DateRangePicker(bind=binding)` - Date range picker

### Action Components
- `Button("label", intent="primary|secondary|danger", icon="...", disabled_if="condition")` - Clickable button
- `Form(onSubmit=action)` - Form container

### Feedback Components
- `Modal(open=binding, title="...", size="sm|md|lg", closable=true)` - Modal dialog
- `Alert(type="info|success|error|warning", message="...", closable=true)` - Alert banner
- `Progress(value=binding, max=100, showText=true, status="normal|success|error")` - Progress bar
- `Spinner(size="sm|md|lg", text="...")` - Loading spinner

### Data Components
- `DataChart(type="line|bar|pie", data=binding)` - Data chart visualization
- `DataTable(columns=[...], data=binding)` - Data table

### State Management
```
component Example:
    state:
        count: int = 0
        name: str = ""
        enabled: bool = false
    
    Card:
        VStack:
            Text("Count: {count}")
            Button("Increment"):
                on_click: state.count += 1
```

### Bindings
- `<<` - One-way binding (subscribe to state)
- `:=` - Two-way binding (for inputs)

Examples:
```
Text(content << f"{state.count}")
Input(value := state.name)
Button("Submit", disabled_if="!state.name")
```

### Actions
- `on_click: state.var += 1` - State mutation
- `Navigate(to="/path", params={...}, query={...}, replace=false)` - Navigation
- `ShowToast("message")` - Show notification
- `Fetch(url="/api/...", method="POST", body={...}, on_success=..., on_error=...)` - HTTP request

### HTTP Requests
```
Button("Submit"):
    on_click:
        Fetch(
            url="/api/login",
            method="POST",
            body={"email": state.email, "password": state.password},
            headers={"Content-Type": "application/json"},
            on_success: Navigate(to="/dashboard"),
            on_error: ShowToast("Login failed")
        )
```

HTTP Methods: GET, POST, PUT, PATCH, DELETE
Content Types: JSON, FORM_URLENCODED, FORM_DATA

### Conditional Rendering
```
if state.isLoggedIn:
    Text("Welcome back!")
```

### Size Tokens
- Sizes: "xs", "sm", "md", "lg", "xl"
- Text styles: "h1", "h2", "h3", "body", "caption"
- Intents: "primary", "secondary", "danger", "success"
- Colors: "green", "red", "blue", "yellow", "gray"

## Output Rules
1. Output ONLY NanoDSL code, no explanations
2. Use 4 spaces for indentation
3. Keep it minimal - no redundant components
4. Wrap output in ```nanodsl code fence
5. Use appropriate components: DatePicker for dates, Switch for toggles, Modal for dialogs, etc.
6. For Image components: Use descriptive scene paths like "/singapore-marina-bay.jpg", "/tropical-beach.jpg". Do NOT use external URLs - images will be AI-generated from the scene name."""
    }
}

/**
 * NanoDSL generation context
 */
@Serializable
data class NanoDSLContext(
    val description: String
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
            description = "Natural language description of the UI to generate. Be specific about components, layout, interactions, state management, and HTTP requests needed.",
            required = true
        )
    )
) {
    override fun getExampleUsage(toolName: String): String {
        return """/$toolName description="Create a contact form with name, email, message fields and a submit button that sends to /api/contact""""
    }
}

