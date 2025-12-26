package cc.unitmesh.server.cli

import cc.unitmesh.agent.ArtifactAgent
import cc.unitmesh.agent.artifact.ArtifactBundle
import cc.unitmesh.agent.artifact.ArtifactBundlePacker
import cc.unitmesh.agent.artifact.ConversationMessage
import cc.unitmesh.agent.artifact.PackResult
import cc.unitmesh.agent.render.ArtifactRenderer
import cc.unitmesh.agent.render.ConsoleLogEntry
import cc.unitmesh.agent.tool.ToolResult
import cc.unitmesh.devins.ui.compose.agent.artifact.ArtifactScenarios
import cc.unitmesh.llm.LLMService
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
 * ./gradlew :mpp-ui:runArtifactCli -PartifactScenario="todolist" -PartifactOutput="my-todo.unit"
 * ```
 *
 * Test Scenarios (from ArtifactScenarios):
 * - dashboard: Interactive dashboard with charts
 * - todolist: Simple todo list app
 * - calculator: Calculator widget
 * - pomodoro: Pomodoro timer
 * - weather: Weather card widget
 * - game: Tic-Tac-Toe game
 * - kanban: Kanban board
 * - markdown: Markdown editor
 */
object ArtifactCli {

    @JvmStatic
    fun main(args: Array<String>) {
        println("=".repeat(80))
        println("AutoDev Artifact Agent CLI (JVM)")
        println("=".repeat(80))

        // Parse arguments
        val prompt = System.getProperty("artifactPrompt")
        val scenario = System.getProperty("artifactScenario")
        val outputPath = System.getProperty("artifactOutput") ?: "artifact-output.unit"
        val language = System.getProperty("artifactLanguage") ?: "EN"
        val exportHtmlOnly = System.getProperty("artifactHtmlOnly")?.toBoolean() ?: false

        // Get scenarios from shared ArtifactScenarios
        val scenarioMap = ArtifactScenarios.scenarios.associate { it.id to it.prompt }

        val finalPrompt = when {
            prompt != null -> prompt
            scenario != null -> {
                val foundScenario = ArtifactScenarios.getById(scenario)
                if (foundScenario != null) {
                    foundScenario.prompt
                } else {
                    System.err.println("Unknown scenario: $scenario")
                    System.err.println("Available scenarios: ${ArtifactScenarios.scenarios.map { it.id }.joinToString(", ")}")
                    return
                }
            }
            else -> {
                println("Available test scenarios:")
                ArtifactScenarios.scenarios.forEach { s ->
                    println("  - ${s.id}: ${s.name} - ${s.description}")
                }
                println()
                println("Usage:")
                println("  -PartifactPrompt=\"your prompt\"")
                println("  -PartifactScenario=<scenario>")
                println("  -PartifactOutput=<output.unit>     # Output file (.unit bundle or .html)")
                println("  -PartifactLanguage=<EN|ZH>")
                println("  -PartifactHtmlOnly=true            # Export raw HTML instead of .unit bundle")
                println()

                // Default to dashboard for quick testing
                println("Running default scenario: dashboard")
                ArtifactScenarios.getById("dashboard")!!.prompt
            }
        }

        println("📝 Prompt: ${finalPrompt.lines().first()}...")
        println("📄 Output: $outputPath")
        println("🌍 Language: $language")
        println("📦 Format: ${if (exportHtmlOnly) "HTML only" else ".unit bundle"}")
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

                val llmService = LLMService(
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

                    // Select best artifact for export
                    val bestArtifact = ArtifactBundle.selectBestArtifact(result.artifacts)

                    result.artifacts.forEachIndexed { index, artifact ->
                        println()
                        println("─".repeat(40))
                        println("Artifact ${index + 1}: ${artifact.title}")
                        println("  ID: ${artifact.identifier}")
                        println("  Type: ${artifact.type}")
                        println("  Size: ${artifact.content.length} chars")
                        if (artifact == bestArtifact) {
                            println("  ★ Selected for export")
                        }

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
                        }
                    }

                    // Export the best artifact
                    if (bestArtifact != null) {
                        println()
                        println("─".repeat(40))
                        println("📦 Exporting artifact...")

                        if (exportHtmlOnly || outputPath.endsWith(".html")) {
                            // Export raw HTML
                            val htmlPath = if (outputPath.endsWith(".unit")) {
                                outputPath.replace(".unit", ".html")
                            } else {
                                outputPath
                            }
                            File(htmlPath).writeText(bestArtifact.content)
                            println("  📁 Saved HTML to: $htmlPath")
                        } else {
                            // Export as .unit bundle
                            val bundle = ArtifactBundle.fromArtifact(
                                artifact = bestArtifact,
                                conversationHistory = listOf(
                                    ConversationMessage(role = "user", content = finalPrompt),
                                    ConversationMessage(role = "assistant", content = result.rawResponse)
                                ),
                                modelInfo = cc.unitmesh.agent.artifact.ModelInfo(
                                    name = activeConfig.model,
                                    provider = activeConfig.provider
                                )
                            )

                            val packer = ArtifactBundlePacker()
                            val unitPath = if (outputPath.endsWith(".unit")) {
                                outputPath
                            } else {
                                "${outputPath}.unit"
                            }

                            when (val packResult = packer.pack(bundle, unitPath)) {
                                is PackResult.Success -> {
                                    val file = File(packResult.outputPath)
                                    println("  ✅ Created .unit bundle: ${packResult.outputPath}")
                                    println("  📦 Bundle size: ${file.length()} bytes")
                                    println()
                                    println("  📋 Bundle contents:")
                                    println("     - ARTIFACT.md (metadata)")
                                    println("     - package.json (execution config)")
                                    println("     - ${bundle.getMainFileName()} (main content)")
                                    println("     - .artifact/context.json (conversation history)")
                                    println()
                                    println("  💡 To use the artifact:")
                                    println("     1. Open in AutoDev desktop app")
                                    println("     2. Or extract: unzip ${packResult.outputPath} -d ./extracted")
                                    println("     3. Then open ${bundle.getMainFileName()} in browser")
                                }
                                is PackResult.Error -> {
                                    println("  ❌ Failed to create .unit bundle: ${packResult.message}")
                                    packResult.cause?.printStackTrace()

                                    // Fallback to HTML export
                                    val htmlPath = outputPath.replace(".unit", ".html")
                                    File(htmlPath).writeText(bestArtifact.content)
                                    println("  📁 Fallback: Saved HTML to: $htmlPath")
                                }
                            }
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
