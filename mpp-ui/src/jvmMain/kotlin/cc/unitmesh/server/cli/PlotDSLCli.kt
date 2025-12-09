package cc.unitmesh.server.cli

import cc.unitmesh.agent.render.CodingAgentRenderer
import cc.unitmesh.agent.subagent.PlotDSLAgent
import cc.unitmesh.agent.subagent.PlotDSLContext
import cc.unitmesh.devins.ui.compose.sketch.letsplot.PlotParser
import cc.unitmesh.llm.KoogLLMService
import cc.unitmesh.llm.LLMProviderType
import cc.unitmesh.llm.ModelConfig
import cc.unitmesh.llm.compression.TokenInfo
import com.charleskorn.kaml.Yaml
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * JVM CLI for testing PlotDSLAgent - generates statistical charts from natural language
 *
 * Usage:
 * ```bash
 * # Generate a chart from description
 * ./gradlew :mpp-ui:runPlotDSLCli -PplotDescription="Create a bar chart showing quarterly sales"
 *
 * # Specify chart type
 * ./gradlew :mpp-ui:runPlotDSLCli -PplotDescription="Show monthly revenue" -PplotChartType="line"
 *
 * # Use dark theme
 * ./gradlew :mpp-ui:runPlotDSLCli -PplotDescription="Scatter plot of price vs sales" -PplotTheme="dark"
 * ```
 */
object PlotDSLCli {

    @JvmStatic
    fun main(args: Array<String>) {
        println("=".repeat(80))
        println("AutoDev PlotDSL CLI (JVM - Lets-Plot Compose)")
        println("=".repeat(80))

        // Parse arguments
        val description = System.getProperty("description") ?: args.getOrNull(0) ?: run {
            System.err.println("Usage: -PplotDescription=<description> [-PplotChartType=<type>] [-PplotTheme=<theme>]")
            System.err.println()
            System.err.println("Examples:")
            System.err.println("  -PplotDescription=\"Create a bar chart showing quarterly sales: Q1=100, Q2=150, Q3=120, Q4=180\"")
            System.err.println("  -PplotDescription=\"Line chart of monthly revenue\" -PplotChartType=line")
            System.err.println("  -PplotDescription=\"Scatter plot comparing price and sales\" -PplotTheme=dark")
            return
        }

        val chartType = System.getProperty("chartType") ?: args.getOrNull(1)
        val theme = System.getProperty("theme") ?: args.getOrNull(2) ?: "minimal"

        println("📊 Description: $description")
        if (chartType != null) {
            println("📈 Chart Type: $chartType")
        }
        println("🎨 Theme: $theme")
        println()

        runBlocking {
            try {
                // Load configuration from ~/.autodev/config.yaml
                println("🔧 Loading configuration...")
                val configFile = File(System.getProperty("user.home"), ".autodev/config.yaml")
                if (!configFile.exists()) {
                    System.err.println("❌ Configuration file not found: ${configFile.absolutePath}")
                    System.err.println("   Please create ~/.autodev/config.yaml with your LLM configuration")
                    return@runBlocking
                }

                val yamlContent = configFile.readText()
                val yaml = Yaml(configuration = com.charleskorn.kaml.YamlConfiguration(strictMode = false))
                val config = yaml.decodeFromString(AutoDevConfig.serializer(), yamlContent)

                val activeName = config.active
                val activeConfig = config.configs.find { it.name == activeName }

                if (activeConfig == null) {
                    System.err.println("❌ Active configuration '$activeName' not found in config.yaml")
                    System.err.println("   Available configs: ${config.configs.map { it.name }.joinToString(", ")}")
                    return@runBlocking
                }

                println("📝 Using config: ${activeConfig.name} (${activeConfig.provider}/${activeConfig.model})")
                println()

                // Convert provider string to LLMProviderType
                val providerType = when (activeConfig.provider.lowercase()) {
                    "openai" -> LLMProviderType.OPENAI
                    "anthropic" -> LLMProviderType.ANTHROPIC
                    "google" -> LLMProviderType.GOOGLE
                    "deepseek" -> LLMProviderType.DEEPSEEK
                    "ollama" -> LLMProviderType.OLLAMA
                    "openrouter" -> LLMProviderType.OPENROUTER
                    "glm" -> LLMProviderType.GLM
                    "qwen" -> LLMProviderType.QWEN
                    "kimi" -> LLMProviderType.KIMI
                    else -> LLMProviderType.CUSTOM_OPENAI_BASE
                }

                val llmService = KoogLLMService(
                    ModelConfig(
                        provider = providerType,
                        modelName = activeConfig.model,
                        apiKey = activeConfig.apiKey,
                        temperature = activeConfig.temperature ?: 0.7,
                        maxTokens = activeConfig.maxTokens ?: 4096,
                        baseUrl = activeConfig.baseUrl ?: ""
                    )
                )

                // Create PlotDSLAgent
                println("🧠 Creating PlotDSLAgent...")
                val agent = PlotDSLAgent(llmService)
                println("✅ Agent created")
                println()

                // Create context
                val context = PlotDSLContext(
                    description = description,
                    chartType = chartType,
                    theme = theme,
                    includeTitle = true
                )

                // Execute generation
                println("🔍 Generating chart...")
                println()

                val startTime = System.currentTimeMillis()
                val result = agent.execute(context) { progress ->
                    println("  $progress")
                }
                val executionTime = System.currentTimeMillis() - startTime

                println()
                println("=".repeat(80))
                println("📊 Result:")
                println("=".repeat(80))
                println(result.content)
                println()

                // Try to parse and validate the generated code
                val codeMatch = Regex("```plotdsl\\s*\\n([\\s\\S]*?)\\n```").find(result.content)
                val generatedCode = codeMatch?.groupValues?.get(1)?.trim()

                if (generatedCode != null) {
                    println("=".repeat(80))
                    println("🔍 Validation:")
                    println("=".repeat(80))
                    
                    val plotConfig = PlotParser.parse(generatedCode)
                    if (plotConfig != null) {
                        println("✅ PlotDSL code is valid and parseable")
                        println("   Title: ${plotConfig.title ?: "(no title)"}")
                        println("   Geom: ${plotConfig.geom}")
                        println("   Theme: ${plotConfig.theme}")
                        println("   Data columns: ${plotConfig.data.columns.keys.joinToString(", ")}")
                        
                        // Show data preview
                        println()
                        println("📊 Data Preview:")
                        plotConfig.data.columns.forEach { (name, values) ->
                            val preview = values.take(5).joinToString(", ") { 
                                when (it) {
                                    is cc.unitmesh.devins.ui.compose.sketch.letsplot.PlotValue.Number -> it.value.toString()
                                    is cc.unitmesh.devins.ui.compose.sketch.letsplot.PlotValue.Text -> it.value
                                }
                            }
                            val suffix = if (values.size > 5) ", ..." else ""
                            println("   $name: [$preview$suffix] (${values.size} values)")
                        }
                    } else {
                        println("⚠️ PlotDSL code could not be parsed - may have syntax issues")
                    }
                }

                println()
                if (result.success) {
                    println("✅ Generation completed successfully")
                    println("⏱️  Execution time: ${executionTime}ms")
                    
                    // Show metadata
                    println()
                    println("📋 Metadata:")
                    result.metadata.forEach { (key, value) ->
                        if (key != "description") { // Skip long description
                            println("   $key: $value")
                        }
                    }
                } else {
                    println("❌ Generation failed")
                    println("   Error: ${result.metadata["error"] ?: "Unknown error"}")
                }

            } catch (e: Exception) {
                System.err.println("❌ Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}

