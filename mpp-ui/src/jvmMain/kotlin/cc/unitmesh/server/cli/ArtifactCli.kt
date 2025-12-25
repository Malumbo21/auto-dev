package cc.unitmesh.server.cli

import cc.unitmesh.agent.ArtifactAgent
import cc.unitmesh.agent.render.ArtifactRenderer
import cc.unitmesh.agent.render.ConsoleLogEntry
import cc.unitmesh.agent.tool.ToolResult
import cc.unitmesh.llm.KoogLLMService
import cc.unitmesh.llm.LLMProviderType
import cc.unitmesh.llm.ModelConfig
import cc.unitmesh.llm.compression.TokenInfo
import com.charleskorn.kaml.Yaml
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * JVM CLI for testing ArtifactAgent HTML/JS generation
 *
 * Usage:
 * ```bash
 * ./gradlew :mpp-ui:runArtifactCli -PartifactPrompt="Create a todo list app"
 * ./gradlew :mpp-ui:runArtifactCli -PartifactScenario="dashboard"
 * ```
 *
 * Test Scenarios:
 * - dashboard: Interactive dashboard with charts
 * - todolist: Simple todo list app
 * - calculator: Calculator widget
 * - timer: Countdown timer
 * - game: Simple game (snake or tic-tac-toe)
 */
object ArtifactCli {

    // Predefined test scenarios
    private val scenarios = mapOf(
        "dashboard" to """
            Create an interactive dashboard with:
            1. A header showing "Analytics Dashboard" 
            2. 3 stat cards showing: Users (1,234), Revenue ($45,678), Orders (567)
            3. A simple bar chart using CSS (no libraries) showing monthly data
            4. Dark theme with modern styling
            5. Console.log the current time when the page loads
        """.trimIndent(),

        "todolist" to """
            Create a todo list app with:
            1. Input field to add new todos
            2. List of todos with checkbox to mark complete
            3. Button to delete todos
            4. Local storage persistence
            5. Show count of remaining todos
            6. Console.log when items are added/completed/deleted
        """.trimIndent(),

        "calculator" to """
            Create a calculator widget with:
            1. Display showing current input and result
            2. Number buttons 0-9
            3. Operation buttons: +, -, *, /, =, C
            4. Responsive grid layout
            5. Handle decimal numbers
            6. Console.log each calculation
        """.trimIndent(),

        "timer" to """
            Create a countdown timer app with:
            1. Input for minutes and seconds
            2. Start, Pause, Reset buttons
            3. Large time display (MM:SS format)
            4. Visual progress ring or bar
            5. Sound notification when complete (use Web Audio API beep)
            6. Console.log timer events
        """.trimIndent(),

        "game" to """
            Create a Tic-Tac-Toe game with:
            1. 3x3 grid board
            2. Two players: X and O
            3. Turn indicator
            4. Win detection with highlighting
            5. Reset button
            6. Score tracking
            7. Console.log game moves and results
        """.trimIndent(),

        "weather" to """
            Create a weather card widget that shows:
            1. City name input field
            2. Current temperature display (use mock data)
            3. Weather icon (sun/cloud/rain using CSS/emoji)
            4. 5-day forecast preview
            5. Toggle between Celsius and Fahrenheit
            6. Modern glassmorphism design
            7. Console.log temperature conversions
        """.trimIndent(),

        "pomodoro" to """
            Create a Pomodoro timer with:
            1. 25-minute work sessions, 5-minute breaks
            2. Circular progress indicator
            3. Session counter
            4. Start/Pause/Skip buttons
            5. Different colors for work vs break
            6. Browser notification when session ends
            7. Console.log session transitions
        """.trimIndent()
    )

    @JvmStatic
    fun main(args: Array<String>) {
        println("=".repeat(80))
        println("AutoDev Artifact Agent CLI (JVM)")
        println("=".repeat(80))

        // Parse arguments
        val prompt = System.getProperty("artifactPrompt")
        val scenario = System.getProperty("artifactScenario")
        val outputPath = System.getProperty("artifactOutput") ?: "artifact-output.html"
        val language = System.getProperty("artifactLanguage") ?: "EN"

        val finalPrompt = when {
            prompt != null -> prompt
            scenario != null -> {
                scenarios[scenario] ?: run {
                    System.err.println("Unknown scenario: $scenario")
                    System.err.println("Available scenarios: ${scenarios.keys.joinToString(", ")}")
                    return
                }
            }
            else -> {
                println("Available test scenarios:")
                scenarios.forEach { (name, desc) ->
                    println("  - $name: ${desc.lines().first()}")
                }
                println()
                println("Usage:")
                println("  -PartifactPrompt=\"your prompt\"")
                println("  -PartifactScenario=<scenario>")
                println("  -PartifactOutput=<output.html>")
                println("  -PartifactLanguage=<EN|ZH>")
                println()

                // Default to dashboard for quick testing
                println("Running default scenario: dashboard")
                scenarios["dashboard"]!!
            }
        }

        println("📝 Prompt: ${finalPrompt.lines().first()}...")
        println("📄 Output: $outputPath")
        println("🌍 Language: $language")
        println()

        runBlocking {
            try {
                val startTime = System.currentTimeMillis()

                // Load configuration from ~/.autodev/config.yaml
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
                    return@runBlocking
                }

                println("📝 Using config: ${activeConfig.name} (${activeConfig.provider}/${activeConfig.model})")

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

                val renderer = ArtifactCliRenderer()
                val agent = ArtifactAgent(
                    llmService = llmService,
                    renderer = renderer,
                    language = language
                )

                println()
                println("🚀 Generating artifact...")
                println()

                val result = agent.generate(finalPrompt) { progress ->
                    // Progress is handled by renderer
                }

                val totalTime = System.currentTimeMillis() - startTime

                println()
                println("=".repeat(80))
                println("📊 Result:")
                println("=".repeat(80))

                if (result.success && result.artifacts.isNotEmpty()) {
                    println("✅ Generated ${result.artifacts.size} artifact(s)")

                    result.artifacts.forEachIndexed { index, artifact ->
                        println()
                        println("─".repeat(40))
                        println("Artifact ${index + 1}: ${artifact.title}")
                        println("  ID: ${artifact.identifier}")
                        println("  Type: ${artifact.type}")
                        println("  Size: ${artifact.content.length} chars")

                        // Validate HTML artifacts
                        if (artifact.type == ArtifactAgent.Artifact.ArtifactType.HTML) {
                            val validation = agent.validateHtmlArtifact(artifact.content)
                            if (validation.isValid) {
                                println("  ✓ HTML validation passed")
                            } else {
                                println("  ⚠ HTML validation warnings:")
                                validation.errors.forEach { error ->
                                    println("    - $error")
                                }
                            }

                            // Save to file
                            val fileName = if (result.artifacts.size > 1) {
                                outputPath.replace(".html", "-${index + 1}.html")
                            } else {
                                outputPath
                            }

                            File(fileName).writeText(artifact.content)
                            println("  📁 Saved to: $fileName")
                        }
                    }
                } else {
                    println("❌ Failed to generate artifact")
                    if (result.error != null) {
                        println("   Error: ${result.error}")
                    }
                }

                println()
                println("⏱️  Total time: ${totalTime}ms")

                // Show console logs if any
                val logs = renderer.getConsoleLogs("")
                if (logs.isNotEmpty()) {
                    println()
                    println("📋 Console logs captured:")
                    logs.forEach { log ->
                        println("  [${log.level}] ${log.message}")
                    }
                }

            } catch (e: Exception) {
                System.err.println("❌ Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}

/**
 * Artifact CLI Renderer - Console output with artifact parsing
 */
class ArtifactCliRenderer : ArtifactRenderer {
    private val consoleLogs = mutableListOf<ConsoleLogEntry>()
    private val artifacts = mutableMapOf<String, String>()

    override fun renderIterationHeader(current: Int, max: Int) {
        // Not used in artifact mode
    }

    override fun renderLLMResponseStart() {
        print("💭 ")
    }

    override fun renderLLMResponseChunk(chunk: String) {
        // Filter out artifact XML tags for cleaner output
        val filtered = chunk
            .replace(Regex("<autodev-artifact[^>]*>"), "[ARTIFACT START]")
            .replace("</autodev-artifact>", "[ARTIFACT END]")
        print(filtered)
        System.out.flush()
    }

    override fun renderLLMResponseEnd() {
        println("\n")
    }

    override fun renderToolCall(toolName: String, paramsStr: String) {
        println("● $toolName: $paramsStr")
    }

    override fun renderToolResult(
        toolName: String,
        success: Boolean,
        output: String?,
        fullOutput: String?,
        metadata: Map<String, String>
    ) {
        val status = if (success) "✓" else "✗"
        println("  $status ${output?.take(100) ?: ""}")
    }

    override fun renderTaskComplete(executionTimeMs: Long, toolsUsedCount: Int) {
        println("✓ Generation complete (${executionTimeMs}ms)")
    }

    override fun renderFinalResult(success: Boolean, message: String, iterations: Int) {
        val symbol = if (success) "✅" else "❌"
        println("$symbol $message")
    }

    override fun renderError(message: String) {
        System.err.println("❌ Error: $message")
    }

    override fun renderRepeatWarning(toolName: String, count: Int) {
        println("⚠️  Warning: $toolName repeated $count times")
    }

    override fun renderRecoveryAdvice(recoveryAdvice: String) {
        println("💡 $recoveryAdvice")
    }

    override fun updateTokenInfo(tokenInfo: TokenInfo) {
        println("📊 Tokens: ${tokenInfo.inputTokens} in / ${tokenInfo.outputTokens} out")
    }

    override fun renderUserConfirmationRequest(toolName: String, params: Map<String, Any>) {
        println("❓ Confirm: $toolName with $params")
    }

    // ArtifactRenderer specific methods

    override fun renderArtifact(identifier: String, type: String, title: String, content: String) {
        artifacts[identifier] = content
        println()
        println("🎨 Artifact Generated: $title")
        println("   ID: $identifier")
        println("   Type: $type")
        println("   Size: ${content.length} characters")
    }

    override fun updateArtifact(identifier: String, content: String) {
        artifacts[identifier] = content
        println("🔄 Artifact Updated: $identifier")
    }

    override fun logConsoleMessage(identifier: String, level: String, message: String, timestamp: Long) {
        consoleLogs.add(ConsoleLogEntry(identifier, level, message, timestamp))
        println("  📋 [$level] $message")
    }

    override fun clearConsoleLogs(identifier: String?) {
        if (identifier != null) {
            consoleLogs.removeIf { it.identifier == identifier }
        } else {
            consoleLogs.clear()
        }
    }

    override fun getConsoleLogs(identifier: String): List<ConsoleLogEntry> {
        return if (identifier.isEmpty()) {
            consoleLogs.toList()
        } else {
            consoleLogs.filter { it.identifier == identifier }
        }
    }

    override suspend fun exportArtifact(identifier: String, format: String): String? {
        val content = artifacts[identifier] ?: return null
        val fileName = "$identifier.$format"
        File(fileName).writeText(content)
        return fileName
    }

    override fun setArtifactPreviewVisible(visible: Boolean) {
        // No-op for CLI
    }

    override fun isArtifactPreviewVisible(): Boolean = false

    override suspend fun awaitSessionResult(sessionId: String, timeoutMs: Long): ToolResult {
        return ToolResult.Error("Not supported in CLI renderer")
    }
}

