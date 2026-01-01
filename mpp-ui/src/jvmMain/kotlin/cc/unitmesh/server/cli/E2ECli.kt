package cc.unitmesh.server.cli

import cc.unitmesh.e2e.dsl.E2EDslGenerator
import cc.unitmesh.e2e.dsl.E2EDslIntegration
import cc.unitmesh.llm.LLMProviderType
import cc.unitmesh.llm.LLMService
import cc.unitmesh.llm.ModelConfig
import com.charleskorn.kaml.Yaml
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * E2E Test CLI - AI-driven E2E test scenario generator
 *
 * This CLI generates E2E test scenarios in DSL format from natural language descriptions.
 * The generated DSL can be executed with Playwright or other browser automation tools.
 *
 * Usage:
 * ```bash
 * ./gradlew :mpp-ui:runE2ECli -Pe2eUrl="https://example.com" -Pe2eGoal="Login with valid credentials"
 * ```
 *
 * @see <a href="https://github.com/phodal/auto-dev/issues/532">Issue #532</a>
 */
object E2ECli {

    @JvmStatic
    fun main(args: Array<String>) {
        println("=".repeat(80))
        println("AutoDev E2E Test Agent CLI")
        println("=".repeat(80))

        // Parse arguments
        val targetUrl = System.getProperty("e2eUrl") ?: args.getOrNull(0) ?: run {
            System.err.println("Usage: -Pe2eUrl=<url> -Pe2eGoal=<goal> [-Pe2eOutput=<file>]")
            System.err.println("Example: -Pe2eUrl=\"https://example.com\" -Pe2eGoal=\"Login with valid credentials\"")
            return
        }

        val testGoal = System.getProperty("e2eGoal") ?: args.getOrNull(1) ?: run {
            System.err.println("Usage: -Pe2eUrl=<url> -Pe2eGoal=<goal> [-Pe2eOutput=<file>]")
            return
        }

        val outputFile = System.getProperty("e2eOutput") ?: args.getOrNull(2)

        println("🌐 Target URL: $targetUrl")
        println("🎯 Test Goal: $testGoal")
        if (outputFile != null) {
            println("📄 Output File: $outputFile")
        }
        println()

        runBlocking {
            try {
                val startTime = System.currentTimeMillis()

                // Load LLM configuration
                val modelConfig = loadLLMConfig() ?: run {
                    System.err.println("❌ No LLM configuration found")
                    System.err.println("   Please configure in ~/.autodev/config.yaml or set environment variables")
                    return@runBlocking
                }

                println("🤖 Using LLM: ${modelConfig.provider} / ${modelConfig.modelName}")
                println()

                val llmService = LLMService.create(modelConfig)
                val dslIntegration = E2EDslIntegration(llmService)
                val dslGenerator = E2EDslGenerator()

                println("🧠 Generating E2E test scenario from description...")
                println()

                // Generate scenario directly from description - no page state needed
                val scenario = dslIntegration.generateScenarioFromDescription(
                    description = testGoal,
                    startUrl = targetUrl
                )

                val totalTime = System.currentTimeMillis() - startTime

                if (scenario != null) {
                    println("✅ Scenario generated successfully!")
                    println()
                    println("=".repeat(80))
                    println("📋 Generated E2E DSL:")
                    println("=".repeat(80))
                    println()

                    val dsl = dslGenerator.generate(scenario)
                    println(dsl)

                    // Save to file if specified
                    if (outputFile != null) {
                        File(outputFile).writeText(dsl)
                        println()
                        println("💾 Saved to: $outputFile")
                    }

                    println()
                    println("=".repeat(80))
                    println("📊 Summary:")
                    println("   Scenario: ${scenario.name}")
                    println("   Steps: ${scenario.steps.size}")
                    println("   Time: ${totalTime}ms")
                } else {
                    println("❌ Failed to generate scenario")
                }

            } catch (e: Exception) {
                System.err.println("❌ Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun loadLLMConfig(): ModelConfig? {
        // Try config file first
        val configFile = File(System.getProperty("user.home"), ".autodev/config.yaml")
        if (configFile.exists()) {
            try {
                val yamlContent = configFile.readText()
                val yaml = Yaml(configuration = com.charleskorn.kaml.YamlConfiguration(strictMode = false))
                val config = yaml.decodeFromString(AutoDevConfig.serializer(), yamlContent)

                val activeName = config.active
                val activeConfig = config.configs.find { it.name == activeName }

                if (activeConfig != null) {
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

                    return ModelConfig(
                        provider = providerType,
                        modelName = activeConfig.model,
                        apiKey = activeConfig.apiKey,
                        temperature = activeConfig.temperature ?: 0.3,
                        maxTokens = activeConfig.maxTokens ?: 4096,
                        baseUrl = activeConfig.baseUrl ?: ""
                    )
                }
            } catch (e: Exception) {
                println("⚠️ Failed to load config file: ${e.message}")
            }
        }

        // Fallback to environment variables
        val apiKey = System.getenv("OPENAI_API_KEY")
            ?: System.getenv("ANTHROPIC_API_KEY")
            ?: System.getenv("DEEPSEEK_API_KEY")

        if (apiKey.isNullOrBlank()) return null

        val provider = when {
            System.getenv("DEEPSEEK_API_KEY") != null -> LLMProviderType.DEEPSEEK
            System.getenv("ANTHROPIC_API_KEY") != null -> LLMProviderType.ANTHROPIC
            else -> LLMProviderType.OPENAI
        }

        val modelName = when (provider) {
            LLMProviderType.DEEPSEEK -> "deepseek-chat"
            LLMProviderType.ANTHROPIC -> "claude-3-5-sonnet-20241022"
            else -> "gpt-4o-mini"
        }

        return ModelConfig(
            provider = provider,
            modelName = modelName,
            apiKey = apiKey,
            temperature = 0.3,
            maxTokens = 4096
        )
    }
}

