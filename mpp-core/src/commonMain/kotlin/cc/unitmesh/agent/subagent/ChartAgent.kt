package cc.unitmesh.agent.subagent

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
 * Chart Agent - Generates chart code from data and natural language descriptions.
 *
 * This agent uses LLM to analyze data and generate appropriate chart configurations
 * that can be rendered using ComposeCharts library.
 *
 * Supported chart types:
 * - Pie Chart: For showing proportions and percentages
 * - Line Chart: For showing trends over time
 * - Column Chart: For comparing values across categories
 * - Row Chart: Horizontal bar chart for category comparison
 *
 * Cross-platform support:
 * - JVM: Full support with prompt templates
 * - JS/WASM: Available via JsChartAgent wrapper
 */
class ChartAgent(
    private val llmService: KoogLLMService,
    private val promptTemplate: String = DEFAULT_PROMPT,
    private val maxRetries: Int = 2
) : SubAgent<ChartContext, ToolResult.AgentResult>(
    definition = createDefinition()
) {
    private val logger = getLogger("ChartAgent")

    override val priority: Int = 60 // Medium-high priority for visualization tasks

    override fun validateInput(input: Map<String, Any>): ChartContext {
        val description = input["description"] as? String
            ?: throw IllegalArgumentException("Missing required parameter: description")

        return ChartContext(description = description)
    }

    override suspend fun execute(
        input: ChartContext,
        onProgress: (String) -> Unit
    ): ToolResult.AgentResult {
        onProgress("📊 Chart Agent: Generating chart from description")
        onProgress("Description: ${input.description.take(100)}...")

        var lastGeneratedCode = ""

        for (attempt in 1..maxRetries) {
            try {
                val prompt = buildPrompt(input)

                onProgress(if (attempt == 1) {
                    "Calling LLM for chart generation..."
                } else {
                    "Retry attempt $attempt/$maxRetries..."
                })

                val responseBuilder = StringBuilder()
                llmService.streamPrompt(
                    userPrompt = prompt,
                    compileDevIns = false
                ).toList().forEach { chunk ->
                    responseBuilder.append(chunk)
                }

                val llmResponse = responseBuilder.toString()

                // Extract chart code from markdown code fence
                val codeFence = CodeFence.parse(llmResponse)
                val generatedCode = if (codeFence.text.isNotBlank()) {
                    codeFence.text.trim()
                } else {
                    llmResponse.trim()
                }

                lastGeneratedCode = generatedCode

                // Basic validation - check if it looks like valid chart config
                if (isValidChartConfig(generatedCode)) {
                    onProgress("✅ Generated chart configuration" +
                        (if (attempt > 1) " (after $attempt attempts)" else ""))

                    // Return formatted output with chart code fence
                    val formattedContent = buildString {
                        appendLine("I've generated the following chart based on your data:")
                        appendLine()
                        appendLine("```chart")
                        appendLine(generatedCode)
                        appendLine("```")
                        appendLine()
                        appendLine("This chart will be rendered as an interactive visualization.")
                    }

                    return ToolResult.AgentResult(
                        success = true,
                        content = formattedContent,
                        metadata = buildMetadata(input, generatedCode, attempt)
                    )
                } else {
                    if (attempt < maxRetries) {
                        onProgress("⚠️ Invalid chart config, will retry...")
                        logger.warn { "Chart validation failed (attempt $attempt)" }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Chart generation failed on attempt $attempt" }
                if (attempt == maxRetries) {
                    onProgress("❌ Generation failed: ${e.message}")
                    return ToolResult.AgentResult(
                        success = false,
                        content = "Failed to generate chart: ${e.message}",
                        metadata = mapOf(
                            "error" to (e.message ?: "Unknown error"),
                            "attempts" to attempt.toString()
                        )
                    )
                }
                onProgress("⚠️ Generation error, will retry: ${e.message}")
            }
        }

        // Return the last generated code even if validation failed
        return ToolResult.AgentResult(
            success = false,
            content = lastGeneratedCode.ifEmpty { "Failed to generate valid chart configuration" },
            metadata = mapOf(
                "description" to input.description.take(200),
                "attempts" to maxRetries.toString(),
                "isValid" to "false"
            )
        )
    }

    private fun isValidChartConfig(code: String): Boolean {
        val trimmed = code.trim().lowercase()
        // Check for valid chart type indicators
        return trimmed.contains("type:") ||
               trimmed.contains("\"type\"") ||
               trimmed.startsWith("pie") ||
               trimmed.startsWith("line") ||
               trimmed.startsWith("column") ||
               trimmed.startsWith("bar") ||
               trimmed.startsWith("row")
    }

    private fun buildMetadata(
        input: ChartContext,
        code: String,
        attempts: Int
    ): Map<String, String> {
        return buildMap {
            put("description", input.description.take(200))
            put("linesOfCode", code.lines().size.toString())
            put("attempts", attempts.toString())
            put("isValid", "true")
        }
    }

    private fun buildPrompt(input: ChartContext): String {
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
            "Error generating chart: ${output.content}"
        }
    }

    override fun getParameterClass(): String = "ChartContext"

    override fun shouldTrigger(context: Map<String, Any>): Boolean {
        val content = context["content"] as? String ?: return false
        val keywords = listOf("chart", "graph", "plot", "visualize", "visualization", "pie", "bar", "line")
        return keywords.any { content.lowercase().contains(it) }
    }

    override suspend fun handleQuestion(
        question: String,
        context: Map<String, Any>
    ): ToolResult.AgentResult {
        return execute(
            ChartContext(description = question),
            onProgress = {}
        )
    }

    override fun getStateSummary(): Map<String, Any> = mapOf(
        "name" to name,
        "description" to description,
        "priority" to priority,
        "supportedChartTypes" to listOf("pie", "line", "column", "row")
    )

    companion object {
        private fun createDefinition() = AgentDefinition(
            name = "chart-agent",
            displayName = "Chart Agent",
            description = ChartAgentSchema.description,
            promptConfig = PromptConfig(
                systemPrompt = "You are a data visualization expert. Generate chart configurations."
            ),
            modelConfig = ModelConfig.default(),
            runConfig = RunConfig(maxTurns = 2, maxTimeMinutes = 1)
        )

        const val DEFAULT_PROMPT = """You are a data visualization expert. Generate chart code for the ComposeCharts library.

## Chart Configuration Format

Use YAML format with the following structure:

### Pie Chart
```yaml
type: pie
title: "Chart Title"
data:
  items:
    - label: "Category A"
      value: 30
      color: "#1E88E5"
    - label: "Category B"
      value: 50
      color: "#43A047"
```

### Line Chart
```yaml
type: line
title: "Trend Over Time"
data:
  lines:
    - label: "Series 1"
      values: [10, 20, 30, 40, 50]
      color: "#1E88E5"
    - label: "Series 2"
      values: [15, 25, 20, 35, 45]
      color: "#43A047"
```

### Column Chart (Vertical Bars)
```yaml
type: column
title: "Comparison"
data:
  bars:
    - label: "Q1"
      values:
        - value: 100
          color: "#1E88E5"
    - label: "Q2"
      values:
        - value: 150
          color: "#43A047"
```

### Row Chart (Horizontal Bars)
```yaml
type: row
title: "Rankings"
data:
  bars:
    - label: "Item A"
      values:
        - value: 85
    - label: "Item B"
      values:
        - value: 72
```

## Color Palette
Use these colors: #1E88E5 (blue), #43A047 (green), #FB8C00 (orange), #E53935 (red), #8E24AA (purple), #00ACC1 (cyan)

## Output Rules
1. Output ONLY the chart configuration in YAML format
2. Choose the most appropriate chart type for the data
3. Use meaningful labels and titles
4. Wrap output in ```chart code fence"""
    }
}

/**
 * Chart generation context
 */
@Serializable
data class ChartContext(
    val description: String
)

/**
 * Schema for Chart Agent tool
 */
object ChartAgentSchema : DeclarativeToolSchema(
    description = """Generate chart visualization from natural language description.

This tool uses a specialized sub-agent to analyze data and generate appropriate chart configurations.
The generated code will be returned in a ```chart code block that can be rendered as an interactive chart.

IMPORTANT: After calling this tool, you MUST include the returned ```chart code block in your response
to the user so they can see the generated visualization. Do not summarize or describe the chart - show it directly.

Supported chart types:
- pie: For showing proportions and percentages
- line: For showing trends over time or continuous data
- column: For comparing values across categories (vertical bars)
- row: For comparing values across categories (horizontal bars)

Use this tool when the user:
- Provides data and asks for a chart or visualization
- Wants to see trends, comparisons, or distributions
- Asks to plot or graph data""",
    properties = mapOf(
        "description" to string(
            description = "Natural language description of the chart to generate. Include the data, chart type, title, and any visualization requirements.",
            required = true
        )
    )
) {
    override fun getExampleUsage(toolName: String): String {
        return """/$toolName description="Create a column chart showing quarterly sales: Q1=100, Q2=150, Q3=200, Q4=180 with title 'Quarterly Sales'""""
    }
}

