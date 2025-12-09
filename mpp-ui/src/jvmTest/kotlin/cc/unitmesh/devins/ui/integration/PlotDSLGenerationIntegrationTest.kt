package cc.unitmesh.devins.ui.integration

import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devins.ui.compose.sketch.letsplot.PlotConfig
import cc.unitmesh.devins.ui.compose.sketch.letsplot.PlotParser
import cc.unitmesh.llm.KoogLLMService
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for PlotDSL generation.
 *
 * These tests verify that LLMs can generate valid PlotDSL code that can be parsed
 * and rendered by Lets-Plot Compose.
 *
 * Run with: ./gradlew :mpp-ui:jvmTest --tests "*PlotDSL*"
 *
 * Prerequisites:
 * - Valid API key in ~/.autodev/config.yaml
 * - Or environment variables: OPENAI_API_KEY, ANTHROPIC_API_KEY, DEEPSEEK_API_KEY
 */
class PlotDSLGenerationIntegrationTest {

    companion object {
        // PlotDSL system prompt for LLM
        private val SYSTEM_PROMPT = """
You are a PlotDSL expert. Generate statistical visualization code using PlotDSL syntax.

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

## Output Rules
1. Output ONLY PlotDSL YAML code, no explanations
2. Use proper YAML indentation (2 spaces)
3. Keep it minimal - no redundant fields
4. Wrap output in ```plotdsl code fence
5. Ensure data columns have matching lengths
""".trim()
    }

    /**
     * Check if LLM is configured and return service
     */
    private suspend fun getLLMServiceOrSkip(): KoogLLMService? {
        if (!ConfigManager.exists()) {
            println("Skipping integration test: Config file not found.")
            return null
        }

        val configWrapper = ConfigManager.load()
        val activeConfig = configWrapper.getActiveConfig()
        if (activeConfig == null) {
            println("Skipping integration test: No active config found.")
            return null
        }

        val modelConfig = activeConfig.toModelConfig()
        if (modelConfig.apiKey.isBlank() && modelConfig.provider != cc.unitmesh.llm.LLMProviderType.OLLAMA) {
            println("Skipping integration test: No API key found.")
            return null
        }

        return KoogLLMService(modelConfig)
    }

    /**
     * Generate PlotDSL from user prompt
     */
    private suspend fun generatePlotDSL(llmService: KoogLLMService, userPrompt: String): String {
        val fullPrompt = "$SYSTEM_PROMPT\n\n## User Request:\n$userPrompt"

        val responseFlow = llmService.streamPrompt(
            userPrompt = fullPrompt,
            compileDevIns = false
        )

        val response = responseFlow.toList().joinToString("")
        return extractDslFromResponse(response)
    }

    /**
     * Extract PlotDSL code from markdown response
     */
    private fun extractDslFromResponse(response: String): String {
        val fencePattern = Regex("```(?:plotdsl|yaml)?\\s*\\n([\\s\\S]*?)\\n```")
        val match = fencePattern.find(response)
        return match?.groupValues?.get(1)?.trim() ?: response.trim()
    }

    /**
     * Verify DSL parses correctly
     */
    private fun verifyDslParses(dsl: String, testName: String): PlotConfig? {
        val config = PlotParser.parse(dsl)
        println("=== $testName ===")
        println("Generated DSL:\n$dsl")
        println("Parse result: ${if (config != null) "SUCCESS" else "FAILED"}")
        if (config != null) {
            println("Title: ${config.title}")
            println("Geom: ${config.geom}")
            println("Data columns: ${config.data.columns.keys}")
        }
        println()
        return config
    }

    // ==================== BASIC CHART TESTS ====================

    @Test
    fun `01 - Simple bar chart generation`() = runBlocking {
        val llmService = getLLMServiceOrSkip() ?: return@runBlocking

        val userPrompt = "Create a simple bar chart showing quarterly sales: Q1=100, Q2=150, Q3=120, Q4=180"
        val generatedDsl = generatePlotDSL(llmService, userPrompt)
        val config = verifyDslParses(generatedDsl, "Simple Bar Chart")

        assertNotNull(config, "Generated PlotDSL should be parseable")
        assertNotNull(config.data.columns["x"] ?: config.data.columns.values.firstOrNull(),
            "Data should have at least one column")
    }

    @Test
    fun `02 - Line chart with multiple series`() = runBlocking {
        val llmService = getLLMServiceOrSkip() ?: return@runBlocking

        val userPrompt = """
            Create a line chart showing monthly revenue for 2023:
            - Jan: 10000, Feb: 12000, Mar: 9500, Apr: 14000, May: 13500, Jun: 16000
        """.trimIndent()

        val generatedDsl = generatePlotDSL(llmService, userPrompt)
        val config = verifyDslParses(generatedDsl, "Line Chart")

        assertNotNull(config, "Generated PlotDSL should be parseable")
    }

    @Test
    fun `03 - Scatter plot with colors`() = runBlocking {
        val llmService = getLLMServiceOrSkip() ?: return@runBlocking

        val userPrompt = """
            Create a scatter plot showing the relationship between price and sales:
            Prices: 10, 20, 30, 40, 50
            Sales: 100, 80, 60, 40, 20
            Use color to indicate price category (low/high)
        """.trimIndent()

        val generatedDsl = generatePlotDSL(llmService, userPrompt)
        val config = verifyDslParses(generatedDsl, "Scatter Plot with Colors")

        assertNotNull(config, "Generated PlotDSL should be parseable")
    }

    @Test
    fun `04 - Histogram generation`() = runBlocking {
        val llmService = getLLMServiceOrSkip() ?: return@runBlocking

        val userPrompt = """
            Create a histogram showing the distribution of exam scores:
            Scores: 65, 70, 72, 75, 78, 80, 82, 85, 88, 90, 92, 95
        """.trimIndent()

        val generatedDsl = generatePlotDSL(llmService, userPrompt)
        val config = verifyDslParses(generatedDsl, "Histogram")

        assertNotNull(config, "Generated PlotDSL should be parseable")
    }

    // ==================== THEMED CHARTS ====================

    @Test
    fun `05 - Dark theme bar chart`() = runBlocking {
        val llmService = getLLMServiceOrSkip() ?: return@runBlocking

        val userPrompt = """
            Create a bar chart with dark theme showing website traffic by source:
            - Direct: 5000
            - Search: 3500
            - Social: 2000
            - Referral: 1500
        """.trimIndent()

        val generatedDsl = generatePlotDSL(llmService, userPrompt)
        val config = verifyDslParses(generatedDsl, "Dark Theme Bar Chart")

        assertNotNull(config, "Generated PlotDSL should be parseable")
    }

    @Test
    fun `06 - Minimal theme line chart`() = runBlocking {
        val llmService = getLLMServiceOrSkip() ?: return@runBlocking

        val userPrompt = """
            Create a minimal-themed line chart showing CPU usage over time:
            Time: 1, 2, 3, 4, 5, 6, 7, 8
            Usage: 20, 45, 30, 70, 55, 80, 40, 25
        """.trimIndent()

        val generatedDsl = generatePlotDSL(llmService, userPrompt)
        val config = verifyDslParses(generatedDsl, "Minimal Theme Line Chart")

        assertNotNull(config, "Generated PlotDSL should be parseable")
    }

    // ==================== COMPLEX VISUALIZATIONS ====================

    @Test
    fun `07 - Grouped bar chart`() = runBlocking {
        val llmService = getLLMServiceOrSkip() ?: return@runBlocking

        val userPrompt = """
            Create a grouped bar chart comparing sales by product category across regions:
            Categories: Electronics, Clothing, Food
            North region: 100, 80, 60
            South region: 90, 85, 70
        """.trimIndent()

        val generatedDsl = generatePlotDSL(llmService, userPrompt)
        val config = verifyDslParses(generatedDsl, "Grouped Bar Chart")

        assertNotNull(config, "Generated PlotDSL should be parseable")
    }

    @Test
    fun `08 - Box plot for statistical analysis`() = runBlocking {
        val llmService = getLLMServiceOrSkip() ?: return@runBlocking

        val userPrompt = """
            Create a box plot showing the distribution of response times across different servers:
            Server A: 10, 12, 15, 11, 14, 13
            Server B: 20, 22, 18, 25, 21, 19
            Server C: 15, 16, 14, 17, 15, 16
        """.trimIndent()

        val generatedDsl = generatePlotDSL(llmService, userPrompt)
        val config = verifyDslParses(generatedDsl, "Box Plot")

        assertNotNull(config, "Generated PlotDSL should be parseable")
    }

    @Test
    fun `09 - Area chart for trends`() = runBlocking {
        val llmService = getLLMServiceOrSkip() ?: return@runBlocking

        val userPrompt = """
            Create an area chart showing user growth over months:
            Months: Jan, Feb, Mar, Apr, May, Jun
            Users: 1000, 1500, 2200, 3000, 3800, 5000
        """.trimIndent()

        val generatedDsl = generatePlotDSL(llmService, userPrompt)
        val config = verifyDslParses(generatedDsl, "Area Chart")

        assertNotNull(config, "Generated PlotDSL should be parseable")
    }

    @Test
    fun `10 - Density plot`() = runBlocking {
        val llmService = getLLMServiceOrSkip() ?: return@runBlocking

        val userPrompt = """
            Create a density plot showing the distribution of customer ages:
            Ages: 18, 22, 25, 28, 30, 32, 35, 38, 40, 45, 50, 55, 60
        """.trimIndent()

        val generatedDsl = generatePlotDSL(llmService, userPrompt)
        val config = verifyDslParses(generatedDsl, "Density Plot")

        assertNotNull(config, "Generated PlotDSL should be parseable")
    }

    // ==================== REAL-WORLD SCENARIOS ====================

    @Test
    fun `11 - Sales dashboard chart`() = runBlocking {
        val llmService = getLLMServiceOrSkip() ?: return@runBlocking

        val userPrompt = """
            Create a sales dashboard chart showing:
            - Product names: iPhone, MacBook, iPad, AirPods, Apple Watch
            - Units sold: 5000, 2000, 3000, 8000, 4000
            - Use a horizontal bar chart with minimal theme
            - Title: "Product Sales Q4 2023"
        """.trimIndent()

        val generatedDsl = generatePlotDSL(llmService, userPrompt)
        val config = verifyDslParses(generatedDsl, "Sales Dashboard Chart")

        assertNotNull(config, "Generated PlotDSL should be parseable")
        assertTrue(config.title != null, "Chart should have a title")
    }

    @Test
    fun `12 - Performance metrics chart`() = runBlocking {
        val llmService = getLLMServiceOrSkip() ?: return@runBlocking

        val userPrompt = """
            Create a line chart showing API response times over 24 hours:
            - Hours: 0, 4, 8, 12, 16, 20, 24
            - Response time (ms): 50, 45, 120, 200, 180, 80, 55
            - Use minimal theme and appropriate axis labels
        """.trimIndent()

        val generatedDsl = generatePlotDSL(llmService, userPrompt)
        val config = verifyDslParses(generatedDsl, "Performance Metrics Chart")

        assertNotNull(config, "Generated PlotDSL should be parseable")
    }

    @Test
    fun `13 - Financial data visualization`() = runBlocking {
        val llmService = getLLMServiceOrSkip() ?: return@runBlocking

        val userPrompt = """
            Create a chart showing stock price movement:
            - Days: Mon, Tue, Wed, Thu, Fri
            - Open: 100, 102, 98, 105, 103
            - Close: 102, 98, 105, 103, 108
            Show both open and close as separate lines
        """.trimIndent()

        val generatedDsl = generatePlotDSL(llmService, userPrompt)
        val config = verifyDslParses(generatedDsl, "Financial Data Visualization")

        assertNotNull(config, "Generated PlotDSL should be parseable")
    }

    @Test
    fun `14 - Survey results chart`() = runBlocking {
        val llmService = getLLMServiceOrSkip() ?: return@runBlocking

        val userPrompt = """
            Create a bar chart showing customer satisfaction survey results:
            - Ratings: Very Satisfied, Satisfied, Neutral, Dissatisfied, Very Dissatisfied
            - Responses: 45, 30, 15, 7, 3
            - Use a color scale from green (satisfied) to red (dissatisfied)
        """.trimIndent()

        val generatedDsl = generatePlotDSL(llmService, userPrompt)
        val config = verifyDslParses(generatedDsl, "Survey Results Chart")

        assertNotNull(config, "Generated PlotDSL should be parseable")
    }

    @Test
    fun `15 - Multi-variable analysis`() = runBlocking {
        val llmService = getLLMServiceOrSkip() ?: return@runBlocking

        val userPrompt = """
            Create a scatter plot analyzing the relationship between:
            - Marketing spend (thousands): 10, 20, 30, 40, 50, 60, 70, 80
            - Sales (thousands): 50, 80, 100, 120, 150, 180, 200, 250
            Show the correlation trend with title "Marketing ROI Analysis"
        """.trimIndent()

        val generatedDsl = generatePlotDSL(llmService, userPrompt)
        val config = verifyDslParses(generatedDsl, "Multi-variable Analysis")

        assertNotNull(config, "Generated PlotDSL should be parseable")
    }
}

