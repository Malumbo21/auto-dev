package cc.unitmesh.server.cli

import cc.unitmesh.agent.e2etest.planner.PlannerConfig
import cc.unitmesh.agent.e2etest.planner.TestActionPlanner
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
                val planner = TestActionPlanner(llmService, PlannerConfig(useDslFormat = true))

                println("🧠 Generating E2E test scenario...")
                println()

                // Create a mock page state for scenario generation
                val mockPageState = createMockPageState(targetUrl)

                val scenario = planner.generateScenario(
                    description = testGoal,
                    startUrl = targetUrl,
                    pageState = mockPageState
                )

                val totalTime = System.currentTimeMillis() - startTime

                if (scenario != null) {
                    println("✅ Scenario generated successfully!")
                    println()
                    println("=".repeat(80))
                    println("📋 Generated E2E DSL:")
                    println("=".repeat(80))
                    println()

                    val dsl = generateDsl(scenario)
                    println(dsl)

                    // Save to file if specified
                    if (outputFile != null) {
                        java.io.File(outputFile).writeText(dsl)
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

    /**
     * Create a mock page state for scenario generation.
     * In a real implementation, this would use Playwright to extract actual page state.
     */
    private fun createMockPageState(url: String): cc.unitmesh.agent.e2etest.model.PageState {
        val defaultBoundingBox = cc.unitmesh.agent.e2etest.model.BoundingBox(0.0, 0.0, 100.0, 30.0)

        fun createFingerprint(selector: String, tagName: String, name: String, role: String) =
            cc.unitmesh.agent.e2etest.model.ElementFingerprint(
                selector = selector,
                tagName = tagName,
                textContent = name,
                role = role
            )

        return cc.unitmesh.agent.e2etest.model.PageState(
            url = url,
            title = "Page at $url",
            viewport = cc.unitmesh.agent.e2etest.model.Viewport(1280, 720),
            actionableElements = listOf(
                // Common elements that might be on a page
                cc.unitmesh.agent.e2etest.model.ActionableElement(
                    tagId = 1,
                    tagName = "input",
                    role = "textbox",
                    name = "username",
                    selector = "input[name='username']",
                    isVisible = true,
                    isEnabled = true,
                    boundingBox = defaultBoundingBox,
                    fingerprint = createFingerprint("input[name='username']", "input", "username", "textbox")
                ),
                cc.unitmesh.agent.e2etest.model.ActionableElement(
                    tagId = 2,
                    tagName = "input",
                    role = "textbox",
                    name = "password",
                    selector = "input[name='password']",
                    isVisible = true,
                    isEnabled = true,
                    boundingBox = defaultBoundingBox,
                    fingerprint = createFingerprint("input[name='password']", "input", "password", "textbox")
                ),
                cc.unitmesh.agent.e2etest.model.ActionableElement(
                    tagId = 3,
                    tagName = "button",
                    role = "button",
                    name = "Login",
                    selector = "button[type='submit']",
                    isVisible = true,
                    isEnabled = true,
                    boundingBox = defaultBoundingBox,
                    fingerprint = createFingerprint("button[type='submit']", "button", "Login", "button")
                ),
                cc.unitmesh.agent.e2etest.model.ActionableElement(
                    tagId = 4,
                    tagName = "input",
                    role = "searchbox",
                    name = "search",
                    selector = "input[type='search']",
                    isVisible = true,
                    isEnabled = true,
                    boundingBox = defaultBoundingBox,
                    fingerprint = createFingerprint("input[type='search']", "input", "search", "searchbox")
                ),
                cc.unitmesh.agent.e2etest.model.ActionableElement(
                    tagId = 5,
                    tagName = "button",
                    role = "button",
                    name = "Search",
                    selector = "button.search-btn",
                    isVisible = true,
                    isEnabled = true,
                    boundingBox = defaultBoundingBox,
                    fingerprint = createFingerprint("button.search-btn", "button", "Search", "button")
                )
            ),
            capturedAt = System.currentTimeMillis()
        )
    }

    /**
     * Generate DSL from a TestScenario
     */
    private fun generateDsl(scenario: cc.unitmesh.agent.e2etest.model.TestScenario): String {
        return buildString {
            appendLine("scenario \"${scenario.name}\" {")
            appendLine("    description \"${scenario.description}\"")
            appendLine("    url \"${scenario.startUrl}\"")
            appendLine()

            scenario.steps.forEach { step ->
                appendLine("    step \"${step.description}\" {")
                appendLine("        ${formatAction(step.action)}")
                step.expectedOutcome?.let {
                    appendLine("        expect \"$it\"")
                }
                appendLine("    }")
                appendLine()
            }

            appendLine("}")
        }
    }

    private fun formatAction(action: cc.unitmesh.agent.e2etest.model.TestAction): String {
        return when (action) {
            is cc.unitmesh.agent.e2etest.model.TestAction.Click -> "click #${action.targetId}"
            is cc.unitmesh.agent.e2etest.model.TestAction.Type -> "type #${action.targetId} \"${action.text}\""
            is cc.unitmesh.agent.e2etest.model.TestAction.Hover -> "hover #${action.targetId}"
            is cc.unitmesh.agent.e2etest.model.TestAction.Scroll -> "scroll ${action.direction.name.lowercase()}"
            is cc.unitmesh.agent.e2etest.model.TestAction.Wait -> formatWaitAction(action)
            is cc.unitmesh.agent.e2etest.model.TestAction.PressKey -> "pressKey \"${action.key}\""
            is cc.unitmesh.agent.e2etest.model.TestAction.Navigate -> "navigate \"${action.url}\""
            is cc.unitmesh.agent.e2etest.model.TestAction.GoBack -> "goBack"
            is cc.unitmesh.agent.e2etest.model.TestAction.GoForward -> "goForward"
            is cc.unitmesh.agent.e2etest.model.TestAction.Refresh -> "refresh"
            is cc.unitmesh.agent.e2etest.model.TestAction.Assert -> "assert #${action.targetId} ${formatAssertion(action.assertion)}"
            is cc.unitmesh.agent.e2etest.model.TestAction.Select -> "select #${action.targetId}"
            is cc.unitmesh.agent.e2etest.model.TestAction.UploadFile -> "uploadFile #${action.targetId} \"${action.filePath}\""
            is cc.unitmesh.agent.e2etest.model.TestAction.Screenshot -> "screenshot \"${action.name}\""
        }
    }

    private fun formatWaitAction(action: cc.unitmesh.agent.e2etest.model.TestAction.Wait): String {
        return when (val condition = action.condition) {
            is cc.unitmesh.agent.e2etest.model.WaitCondition.Duration -> "wait duration ${condition.ms}"
            is cc.unitmesh.agent.e2etest.model.WaitCondition.ElementVisible -> "wait visible #${condition.targetId}"
            is cc.unitmesh.agent.e2etest.model.WaitCondition.ElementHidden -> "wait hidden #${condition.targetId}"
            is cc.unitmesh.agent.e2etest.model.WaitCondition.ElementEnabled -> "wait enabled #${condition.targetId}"
            is cc.unitmesh.agent.e2etest.model.WaitCondition.TextPresent -> "wait textPresent \"${condition.text}\""
            is cc.unitmesh.agent.e2etest.model.WaitCondition.UrlContains -> "wait urlContains \"${condition.substring}\""
            is cc.unitmesh.agent.e2etest.model.WaitCondition.PageLoaded -> "wait pageLoaded"
            is cc.unitmesh.agent.e2etest.model.WaitCondition.NetworkIdle -> "wait networkIdle"
        }
    }

    private fun formatAssertion(assertion: cc.unitmesh.agent.e2etest.model.AssertionType): String {
        return when (assertion) {
            is cc.unitmesh.agent.e2etest.model.AssertionType.Visible -> "visible"
            is cc.unitmesh.agent.e2etest.model.AssertionType.Hidden -> "hidden"
            is cc.unitmesh.agent.e2etest.model.AssertionType.Enabled -> "enabled"
            is cc.unitmesh.agent.e2etest.model.AssertionType.Disabled -> "disabled"
            is cc.unitmesh.agent.e2etest.model.AssertionType.Checked -> "checked"
            is cc.unitmesh.agent.e2etest.model.AssertionType.Unchecked -> "unchecked"
            is cc.unitmesh.agent.e2etest.model.AssertionType.TextEquals -> "textEquals \"${assertion.text}\""
            is cc.unitmesh.agent.e2etest.model.AssertionType.TextContains -> "textContains \"${assertion.text}\""
            is cc.unitmesh.agent.e2etest.model.AssertionType.AttributeEquals -> "attributeEquals \"${assertion.attribute}\" \"${assertion.value}\""
            is cc.unitmesh.agent.e2etest.model.AssertionType.HasClass -> "hasClass \"${assertion.className}\""
        }
    }
}

