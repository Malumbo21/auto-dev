package cc.unitmesh.devins.test

import cc.unitmesh.agent.parser.NanoDSLValidator
import cc.unitmesh.agent.subagent.NanoDSLAgent
import cc.unitmesh.agent.subagent.NanoDSLContext
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devins.filesystem.EmptyFileSystem
import cc.unitmesh.devins.parser.CodeFence
import cc.unitmesh.llm.KoogLLMService
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.random.Random

/**
 * Local scenario-based NanoDSL testing harness.
 *
 * What it does:
 * 1) Load LLM config via ConfigManager (active config)
 * 2) Generate N scenario descriptions (LLM-driven if enabled, otherwise local random templates)
 * 3) For each scenario: NanoDSLAgent generates NanoDSL, then validate variables/state consistency
 * 4) Save NanoDSL cases under docs/test-scripts/nanodsl-cases/
 *
 * Run:
 *   ./gradlew :mpp-core:runNanoDslScenarioHarness --args="--count=5 --useLlmScenarios=false"
 */
object NanoDslScenarioHarness {

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val config = HarnessConfig.fromArgs(args)

        val wrapper = ConfigManager.load()
        val activeModelConfig = wrapper.getActiveModelConfig()

        if (activeModelConfig == null || !wrapper.isValid()) {
            error(
                "No valid active LLM config found at ${ConfigManager.getConfigPath()}. " +
                    "Please configure ~/.autodev/config.yaml (active config) before running."
            )
        }

        val llm = KoogLLMService.create(activeModelConfig)
        val scenarios = if (config.useLlmScenarios) {
            generateScenariosWithLlm(llm, config)
        } else {
            generateScenariosLocally(config)
        }

        val outputDir = config.resolveOutputDir()
        outputDir.createDirectories()

        val agent = NanoDSLAgent(llmService = llm, maxRetries = config.maxRetries)
        val validator = NanoDSLValidator()

        println("NanoDSL scenario harness")
        println("- Scenarios: ${scenarios.size}")
        println("- Output: $outputDir")

        val runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
        val runDir = outputDir.resolve(runId).also { it.createDirectories() }

        var successCount = 0
        val failures = mutableListOf<String>()

        scenarios.forEachIndexed { index, scenario ->
            val caseId = (index + 1).toString().padStart(2, '0')
            val fileBase = "case-$caseId"

            println("\n[$caseId/${scenarios.size}] Scenario: $scenario")

            val result = agent.execute(
                NanoDSLContext(description = scenario),
                onProgress = { /* keep quiet by default */ }
            )

            val generatedCode = extractNanoDslFromAgentContent(result.content)

            val validation = validator.validate(generatedCode)
            val parseOk = validation.isValid
            val variableStateIssues = validateTemplateVariablesAgainstState(generatedCode)

            val finalOk = result.success && parseOk && variableStateIssues.isEmpty()

            val reportText = buildString {
                appendLine("scenario: $scenario")
                appendLine("agentSuccess: ${result.success}")
                appendLine("validatorIsValid: ${validation.isValid}")
                if (validation.warnings.isNotEmpty()) {
                    appendLine("warnings:")
                    validation.warnings.forEach { appendLine("- $it") }
                }
                if (validation.errors.isNotEmpty()) {
                    appendLine("errors:")
                    validation.errors.forEach { appendLine("- line ${it.line}: ${it.message}${it.suggestion?.let { s -> " ($s)" } ?: ""}") }
                }
                if (variableStateIssues.isNotEmpty()) {
                    appendLine("templateStateIssues:")
                    variableStateIssues.forEach { appendLine("- $it") }
                }
            }

            val caseDir = runDir.resolve(fileBase).also { it.createDirectories() }
            caseDir.resolve("scenario.txt").writeText(scenario, StandardCharsets.UTF_8)
            caseDir.resolve("generated.nanodsl").writeText(generatedCode, StandardCharsets.UTF_8)
            caseDir.resolve("report.txt").writeText(reportText, StandardCharsets.UTF_8)

            if (finalOk) {
                successCount++
                println("✅ OK (saved: ${runDir.relativize(caseDir)})")
            } else {
                val reason = buildString {
                    if (!result.success) append("agent failed; ")
                    if (!parseOk) append("validator invalid; ")
                    if (variableStateIssues.isNotEmpty()) append("template/state mismatch; ")
                }.trim()

                failures += "[$caseId] $reason scenario='$scenario'"
                println("❌ FAIL: $reason (saved: ${runDir.relativize(caseDir)})")
            }
        }

        println("\nSummary")
        println("- Passed: $successCount/${scenarios.size}")
        if (failures.isNotEmpty()) {
            println("- Failures:")
            failures.forEach { println("  $it") }
            error("Some scenarios failed. See reports under $runDir")
        }

        println("All scenarios passed. Reports saved under $runDir")
    }

    private data class HarnessConfig(
        val count: Int,
        val seed: Int,
        val outputDir: String,
        val maxRetries: Int,
        val useLlmScenarios: Boolean,
        val llmScenarioPrompt: String
    ) {
        fun resolveOutputDir(): Path {
            val p = Path.of(outputDir)
            return if (p.isAbsolute) p else Path.of(System.getProperty("user.dir")).resolve(p).normalize()
        }

        companion object {
            fun fromArgs(args: Array<String>): HarnessConfig {
                fun getArg(name: String): String? {
                    val prefix = "--$name="
                    return args.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
                }

                return HarnessConfig(
                    count = getArg("count")?.toIntOrNull() ?: 5,
                    seed = getArg("seed")?.toIntOrNull() ?: 42,
                    outputDir = getArg("outputDir") ?: "docs/test-scripts/nanodsl-cases",
                    maxRetries = getArg("maxRetries")?.toIntOrNull() ?: 2,
                    useLlmScenarios = getArg("useLlmScenarios")?.toBooleanStrictOrNull() ?: false,
                    llmScenarioPrompt = getArg("llmScenarioPrompt")
                        ?: "Generate {count} business scenarios (1 sentence each) from a user's perspective. " +
                        "Focus on WHAT users need to accomplish, NOT how the UI looks. " +
                        "Examples: 'User books a hotel for multiple nights and sees total cost', 'Traveler plans trip within budget', 'Shopper reviews items in cart before checkout'. " +
                        "Each scenario should naturally involve dynamic data (counts, prices, lists, totals, etc.) that will become state variables. " +
                        "Return ONLY a plain list, one scenario per line, without numbering."
                )
            }
        }
    }

    private suspend fun generateScenariosWithLlm(llm: KoogLLMService, config: HarnessConfig): List<String> {
        val prompt = config.llmScenarioPrompt.replace("{count}", config.count.toString())

        val chunks = llm.streamPrompt(
            userPrompt = prompt,
            fileSystem = EmptyFileSystem(),
            historyMessages = emptyList(),
            compileDevIns = false
        ).toList()

        val text = chunks.joinToString(separator = "").trim()
        val lines = text
            .lines()
            .map { it.trim() }
            .map { it.removePrefix("- ") }
            .map { it.replace(Regex("^\\d+[\\.)\\s-]+"), "") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(config.count)

        if (lines.isEmpty()) {
            error("LLM returned no scenarios. Raw response: '${text.take(800)}'")
        }

        return lines
    }

    private fun generateScenariosLocally(config: HarnessConfig): List<String> {
        val random = Random(config.seed)
        val templates = listOf(
            "Traveler plans a trip within a specific budget and needs to see remaining funds",
            "User books hotel for multiple nights and views total cost before confirming",
            "Task manager user reviews pending tasks and tracks completion progress",
            "Fitness enthusiast tracks daily steps towards a 10000-step goal",
            "Online shopper reviews items in cart and proceeds to checkout",
            "Student monitors assignment deadlines and submission status",
            "Restaurant customer customizes meal order and sees price breakdown",
            "Event organizer manages attendee list and tracks registration count",
            "Financial user monitors monthly expenses against budget limit",
            "Project manager reviews team tasks and tracks milestone progress",
            "Homebuyer compares properties within price range and neighborhood preferences",
            "Language learner tracks vocabulary words and daily study streak",
            "Gym member views workout history and calorie burn statistics",
            "Recipe user adjusts serving sizes and sees ingredient quantities update",
            "Subscription user manages active services and monthly spending total"
        )

        return (0 until config.count).map {
            templates[random.nextInt(templates.size)]
        }
    }

    private fun extractNanoDslFromAgentContent(content: String): String {
        val codeFence = CodeFence.parse(content)
        return if (codeFence.text.isNotBlank()) {
            codeFence.text.trim()
        } else {
            content.trim()
        }
    }

    private fun validateTemplateVariablesAgainstState(nanoDsl: String): List<String> {
        val referenced = parseReferencedStateKeysFromTemplates(nanoDsl)
        val stateKeys = parseStateKeys(nanoDsl)
        val issues = mutableListOf<String>()

        if (referenced.isNotEmpty() && stateKeys.isEmpty()) {
            issues += "Template references state variables ${referenced.sorted().joinToString(prefix = "[", postfix = "]")} but no 'state:' block was found"
            return issues
        }

        referenced.forEach { key ->
            if (!stateKeys.contains(key)) {
                issues += "Template references state.$key but state does not declare '$key' (declared: ${stateKeys.sorted()})"
            }
        }

        return issues
    }

    private fun parseStateKeys(nanoDsl: String): Set<String> {
        val lines = nanoDsl.lines()
        val keys = mutableSetOf<String>()

        var inStateBlock = false
        var stateIndent: Int? = null

        for (line in lines) {
            if (!inStateBlock) {
                val trimmed = line.trimEnd()
                if (trimmed.trim() == "state:") {
                    inStateBlock = true
                    stateIndent = line.indexOfFirst { !it.isWhitespace() }
                    continue
                }
            } else {
                if (line.trim().isEmpty()) continue

                val indent = line.indexOfFirst { !it.isWhitespace() }
                val baseIndent = stateIndent ?: 0
                if (indent <= baseIndent) {
                    inStateBlock = false
                    stateIndent = null
                    continue
                }

                // Example: budget: int = 2000
                val m = Regex("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*:").find(line)
                if (m != null) keys += m.groupValues[1]
            }
        }

        return keys
    }

    private fun parseReferencedStateKeysFromTemplates(nanoDsl: String): Set<String> {
        val referenced = mutableSetOf<String>()

        // {state.foo} or ${state.foo}
        Regex("\\{\\$?state\\.([A-Za-z_][A-Za-z0-9_]*)\\}")
            .findAll(nanoDsl)
            .forEach { referenced += it.groupValues[1] }

        // {len(state.items)}
        Regex("\\{\\s*len\\(state\\.([A-Za-z_][A-Za-z0-9_]*)\\)\\s*\\}")
            .findAll(nanoDsl)
            .forEach { referenced += it.groupValues[1] }

        return referenced
    }
}
