package cc.unitmesh.agent.subagent

import cc.unitmesh.agent.Platform
import cc.unitmesh.agent.core.SubAgent
import cc.unitmesh.agent.logging.getLogger
import cc.unitmesh.agent.model.AgentDefinition
import cc.unitmesh.agent.model.PromptConfig
import cc.unitmesh.agent.model.RunConfig
import cc.unitmesh.agent.tool.ToolResult
import cc.unitmesh.agent.tool.schema.DeclarativeToolSchema
import cc.unitmesh.agent.tool.schema.SchemaPropertyBuilder.string
import cc.unitmesh.devins.parser.CodeFence
import cc.unitmesh.llm.KoogLLMService
import cc.unitmesh.llm.ModelConfig
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable

/**
 * PlotDSL Agent - Generates PlotDSL statistical chart code from natural language descriptions.
 *
 * PlotDSL is a ggplot2-inspired DSL for AI-generated statistical visualizations,
 * designed to work with Lets-Plot Compose on Desktop and Android platforms.
 *
 * Features:
 * - Converts natural language chart descriptions to PlotDSL code
 * - Supports various chart types: bar, line, scatter, histogram, boxplot, etc.
 * - Supports aesthetic mappings (x, y, color, fill, size, shape)
 * - Supports multiple themes (minimal, classic, dark, light)
 *
 * Cross-platform support:
 * - JVM Desktop: Full Lets-Plot Compose rendering
 * - Android: Full Lets-Plot Compose rendering
 * - iOS/JS/WASM: Fallback to code display
 */
class PlotDSLAgent(
    private val llmService: KoogLLMService,
    private val promptTemplate: String = DEFAULT_PROMPT,
    private val maxRetries: Int = 3
) : SubAgent<PlotDSLContext, ToolResult.AgentResult>(
    definition = createDefinition()
) {
    private val logger = getLogger("PlotDSLAgent")

    override val priority: Int = 45 // Slightly lower than NanoDSL for UI tasks
    
    /**
     * PlotDSLAgent is only available on JVM Desktop and Android platforms
     * where Lets-Plot Compose rendering is supported.
     * 
     * On iOS/JS/WASM platforms, this agent is not available as the
     * charting library doesn't support these targets.
     */
    override val isAvailable: Boolean = Platform.isJvm || Platform.isAndroid

    override fun validateInput(input: Map<String, Any>): PlotDSLContext {
        val description = input["description"] as? String
            ?: throw IllegalArgumentException("Missing required parameter: description")

        return PlotDSLContext(description = description)
    }

    override suspend fun execute(
        input: PlotDSLContext,
        onProgress: (String) -> Unit
    ): ToolResult.AgentResult {
        onProgress("📊 PlotDSL Agent: Generating chart from description")
        onProgress("Description: ${input.description.take(80)}...")

        var lastGeneratedCode = ""
        var lastError: String? = null

        for (attempt in 1..maxRetries) {
            try {
                val prompt = if (attempt == 1) {
                    buildPrompt(input)
                } else {
                    // Include previous errors in retry prompt
                    buildRetryPrompt(input, lastGeneratedCode, lastError)
                }

                onProgress(if (attempt == 1) {
                    "Calling LLM for chart generation..."
                } else {
                    "Retry attempt $attempt/$maxRetries - fixing issues..."
                })

                val responseBuilder = StringBuilder()
                llmService.streamPrompt(
                    userPrompt = prompt,
                    compileDevIns = false
                ).collect { chunk ->
                    responseBuilder.append(chunk)
                }

                val llmResponse = responseBuilder.toString()

                // Extract PlotDSL code from markdown code fence
                val codeFence = CodeFence.parse(llmResponse)
                val generatedCode = if (codeFence.text.isNotBlank()) {
                    codeFence.text.trim()
                } else {
                    llmResponse.trim()
                }

                lastGeneratedCode = generatedCode

                // Basic validation - check if it looks like valid PlotDSL
                val validationResult = validatePlotDSL(generatedCode)
                
                if (validationResult.isValid) {
                    onProgress("✅ Generated ${generatedCode.lines().size} lines of PlotDSL code" +
                        (if (attempt > 1) " (after $attempt attempts)" else ""))

                    // Log warnings if any
                    validationResult.warnings.forEach { warning ->
                        onProgress("⚠️ $warning")
                    }

                    // Return formatted output with plotdsl code fence
                    val formattedContent = buildString {
                        appendLine("I've generated the following PlotDSL chart code based on your description:")
                        appendLine()
                        appendLine("```plotdsl")
                        appendLine(generatedCode)
                        appendLine("```")
                        appendLine()
                        appendLine("This code can be rendered as an interactive chart on Desktop and Android.")
                    }

                    return ToolResult.AgentResult(
                        success = true,
                        content = formattedContent,
                        metadata = buildMetadata(input, generatedCode, attempt, validationResult)
                    )
                } else {
                    // Validation failed, prepare for retry
                    lastError = validationResult.errors.joinToString("; ")
                    
                    if (attempt < maxRetries) {
                        onProgress("⚠️ Validation failed, will retry: $lastError")
                        logger.warn { "PlotDSL validation failed (attempt $attempt): $lastError" }
                    } else {
                        onProgress("❌ Validation failed after $maxRetries attempts: $lastError")
                        logger.error { "PlotDSL validation failed after all retries: $lastError" }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "PlotDSL generation failed on attempt $attempt" }
                lastError = e.message
                if (attempt == maxRetries) {
                    onProgress("❌ Generation failed: ${e.message}")
                    return ToolResult.AgentResult(
                        success = false,
                        content = "Failed to generate PlotDSL: ${e.message}",
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
            content = lastGeneratedCode.ifEmpty { "Failed to generate valid PlotDSL code" },
            metadata = mapOf(
                "description" to input.description,
                "attempts" to maxRetries.toString(),
                "error" to (lastError ?: "Unknown validation error"),
                "isValid" to "false"
            )
        )
    }

    /**
     * Validate PlotDSL code - basic structure check
     */
    private fun validatePlotDSL(code: String): PlotDSLValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Check if code is empty
        if (code.isBlank()) {
            errors.add("Generated code is empty")
            return PlotDSLValidationResult(false, errors, warnings)
        }

        // Check for required sections
        val hasDataSection = code.contains("data:") || code.contains("\"data\"")
        val hasGeomSection = code.contains("geom:") || code.contains("\"geom\"")
        
        if (!hasDataSection) {
            errors.add("Missing 'data' section - chart needs data to visualize")
        }
        
        if (!hasGeomSection) {
            warnings.add("Missing 'geom' section - using default chart type")
        }

        // Check for valid YAML/JSON structure
        val hasValidStructure = (code.contains("plot:") || code.startsWith("{") || 
            code.contains("data:") || code.contains("title:"))
        
        if (!hasValidStructure) {
            errors.add("Invalid PlotDSL structure - should be YAML or JSON format")
        }

        return PlotDSLValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * Build a retry prompt that includes previous errors for self-correction
     */
    private fun buildRetryPrompt(
        input: PlotDSLContext, 
        previousCode: String, 
        error: String?
    ): String {
        val errorFeedback = buildString {
            appendLine("## Previous Attempt (INVALID)")
            appendLine("```plotdsl")
            appendLine(previousCode)
            appendLine("```")
            appendLine()
            appendLine("## Error to Fix:")
            appendLine(error ?: "Unknown validation error")
            appendLine()
            appendLine("## Instructions:")
            appendLine("Please fix the above error and generate a corrected version.")
            appendLine("Output ONLY the corrected PlotDSL code, no explanations.")
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
        input: PlotDSLContext,
        code: String,
        attempts: Int,
        validationResult: PlotDSLValidationResult
    ): Map<String, String> {
        return buildMap {
            put("description", input.description)
            put("linesOfCode", code.lines().size.toString())
            put("attempts", attempts.toString())
            put("isValid", validationResult.isValid.toString())
            if (validationResult.warnings.isNotEmpty()) {
                put("warnings", validationResult.warnings.joinToString("; "))
            }
        }
    }

    private fun buildPrompt(input: PlotDSLContext): String {
        return """
$promptTemplate

## User Request:
${input.description}
""".trim()
    }

    override fun formatOutput(output: ToolResult.AgentResult): String {
        return if (output.success) {
            output.content
        } else {
            "Error generating PlotDSL: ${output.content}"
        }
    }

    override fun getParameterClass(): String = "PlotDSLContext"

    override fun shouldTrigger(context: Map<String, Any>): Boolean {
        val content = context["content"] as? String ?: return false
        val keywords = listOf("chart", "plot", "graph", "visualization", "histogram", "scatter", "bar chart", "line chart", "boxplot", "statistics")
        return keywords.any { content.lowercase().contains(it) }
    }

    override suspend fun handleQuestion(
        question: String,
        context: Map<String, Any>
    ): ToolResult.AgentResult {
        return execute(
            PlotDSLContext(description = question),
            onProgress = {}
        )
    }

    override fun getStateSummary(): Map<String, Any> = mapOf(
        "name" to name,
        "description" to description,
        "priority" to priority,
        "supportedFeatures" to listOf("bar", "line", "scatter", "histogram", "boxplot", "area", "density", "themes")
    )

    companion object {
        private fun createDefinition() = AgentDefinition(
            name = "plotdsl-agent",
            displayName = "PlotDSL Agent",
            description = PlotDSLAgentSchema.description,
            promptConfig = PromptConfig(
                systemPrompt = "You are a data visualization expert. Generate statistical charts using PlotDSL syntax."
            ),
            modelConfig = ModelConfig.default(),
            runConfig = RunConfig(maxTurns = 3, maxTimeMinutes = 2)
        )

        const val DEFAULT_PROMPT = """You are a PlotDSL expert. Generate statistical visualization code using PlotDSL syntax.

## PlotDSL Syntax (YAML format)

PlotDSL uses YAML format for defining statistical plots, similar to ggplot2 in R.

### Basic Structure
```yaml
plot:
  title: "Chart Title"
  data:
    x: [value1, value2, ...]
    y: [value1, value2, ...]
  geom: point|line|bar|histogram|boxplot|area|density
  aes:
    x: column_name
    y: column_name
    color: column_name
    fill: column_name
  theme: default|minimal|classic|dark|light
```

### Supported Geoms
- `point` or `scatter`: Scatter plot
- `line`: Line chart
- `bar` or `column`: Bar chart
- `histogram`: Histogram
- `boxplot`: Box plot
- `area`: Area chart
- `density`: Density plot

### Aesthetic Mappings (aes)
- `x`: X-axis variable
- `y`: Y-axis variable
- `color`: Point/line color by variable
- `fill`: Fill color by variable
- `size`: Point size by variable
- `shape`: Point shape by variable
- `group`: Grouping variable

### Themes
- `default`: Grey theme
- `minimal`: Minimal theme (clean)
- `classic`: Classic ggplot2 theme
- `dark`: Dark theme
- `light`: Light theme
- `void`: No axes or grid

### Example - Bar Chart
```yaml
plot:
  title: "Sales by Region"
  data:
    region: [North, South, East, West]
    sales: [120, 98, 150, 87]
  geom: bar
  aes:
    x: region
    y: sales
    fill: region
  theme: minimal
```

### Example - Line Chart
```yaml
plot:
  title: "Monthly Revenue"
  data:
    month: [Jan, Feb, Mar, Apr, May, Jun]
    revenue: [10000, 12000, 9500, 14000, 13500, 16000]
  geom: line
  aes:
    x: month
    y: revenue
  theme: minimal
```

### Example - Scatter Plot
```yaml
plot:
  title: "Price vs Sales"
  data:
    price: [10, 20, 30, 40, 50]
    sales: [100, 80, 60, 40, 20]
  geom: point
  aes:
    x: price
    y: sales
    color: price
  theme: minimal
```

## Output Rules
1. Output ONLY PlotDSL YAML code, no explanations
2. Use proper YAML indentation (2 spaces)
3. Keep it minimal - no redundant fields
4. Wrap output in ```plotdsl code fence
5. Ensure data columns have matching lengths
6. Include a descriptive title"""
    }
}

/**
 * PlotDSL validation result
 */
data class PlotDSLValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * PlotDSL generation context
 */
@Serializable
data class PlotDSLContext(
    val description: String
)

/**
 * Schema for PlotDSL Agent tool
 */
object PlotDSLAgentSchema : DeclarativeToolSchema(
    description = """Generates data visualizations from natural language descriptions.

This tool uses a specialized sub-agent to generate statistical visualization code in PlotDSL syntax.
The generated code will be returned in a ```plotdsl code block that can be rendered as an interactive chart.

IMPORTANT: After calling this tool, you MUST include the returned ```plotdsl code block in your response
to the user so they can see the generated chart. Do not summarize or describe the code - show it directly.

Use this tool when the user asks for:
- Statistical charts and plots
- Data visualizations (bar, line, scatter, etc.)
- Histograms and distributions
- Box plots and statistical analysis
- Time series visualizations""",
    properties = mapOf(
        "description" to string(
            description = "Natural language description of the chart to generate. Be specific about data, chart type, theme, and visualization requirements.",
            required = true
        )
    )
) {
    override fun getExampleUsage(toolName: String): String {
        return """/$toolName description="Create a bar chart showing quarterly sales: Q1=100, Q2=150, Q3=120, Q4=180 with minimal theme""""
    }
}

