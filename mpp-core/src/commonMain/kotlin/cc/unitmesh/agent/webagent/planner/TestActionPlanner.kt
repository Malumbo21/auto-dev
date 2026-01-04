package cc.unitmesh.agent.webagent.planner

import cc.unitmesh.agent.webagent.WebAgentContext
import cc.unitmesh.agent.webagent.currentTimeMillis
import cc.unitmesh.agent.webagent.model.*
import cc.unitmesh.agent.webagent.prompt.WebAgentPrompts
import cc.unitmesh.llm.LLMService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Plans test actions based on natural language instructions and page state.
 *
 * Uses LLM to understand user intent and generate appropriate test actions.
 * Maintains memory to prevent loops and provide context.
 *
 * Supports both JSON and DSL formats for scenario generation.
 *
 * @see <a href="https://github.com/phodal/auto-dev/issues/532">Issue #532</a>
 */
class TestActionPlanner(
    private val llmService: LLMService,
    private val config: PlannerConfig = PlannerConfig()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val dslParser = SimpleDslParser()

    /**
     * Plan the next action based on current context
     */
    suspend fun planNextAction(context: WebAgentContext): PlannedAction? {
        // Check if we're in a loop
        if (context.memory.isInLoop()) {
            return PlannedAction(
                action = TestAction.Wait(WaitCondition.Duration(1000)),
                reasoning = "Detected potential loop, waiting before retry",
                confidence = 0.5
            )
        }

        // If we have predefined steps, use them
        val currentStep = context.currentStep
        if (currentStep != null) {
            return PlannedAction(
                action = currentStep.action,
                reasoning = "Executing predefined step: ${currentStep.description}",
                confidence = 1.0,
                stepId = currentStep.id
            )
        }

        // Otherwise, use LLM to plan
        return planWithLLM(context)
    }

    /**
     * Generate a test scenario from natural language description using LLM.
     *
     * Uses DSL format by default for better readability and reliability.
     */
    suspend fun generateScenario(
        description: String,
        startUrl: String,
        pageState: PageState
    ): TestScenario? {
        val prompt = if (config.useDslFormat) {
            buildDslScenarioGenerationPrompt(description, startUrl, pageState)
        } else {
            buildScenarioGenerationPrompt(description, startUrl, pageState)
        }

        return try {
            val response = llmService.sendPrompt(prompt)
            if (config.useDslFormat) {
                parseDslScenarioResponse(response, description, startUrl)
            } else {
                parseScenarioResponse(response, description, startUrl)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Plan action using LLM
     */
    private suspend fun planWithLLM(context: WebAgentContext): PlannedAction? {
        val prompt = buildPlanningPrompt(context)

        return try {
            val response = llmService.sendPrompt(prompt)
            parseActionResponse(response)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse LLM response into a PlannedAction
     */
    private fun parseActionResponse(response: String): PlannedAction? {
        val jsonStr = extractJson(response) ?: return null

        return try {
            val jsonObj = json.decodeFromString<JsonObject>(jsonStr)
            val actionType = jsonObj["action_type"]?.jsonPrimitive?.content ?: return null
            val targetId = jsonObj["target_id"]?.jsonPrimitive?.intOrNull ?: 0
            val selector = jsonObj["selector"]?.jsonPrimitive?.content
            val value = jsonObj["value"]?.jsonPrimitive?.content
            val reasoning = jsonObj["reasoning"]?.jsonPrimitive?.content ?: "LLM planned action"
            val confidence = jsonObj["confidence"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.8

            val action = when (actionType.lowercase()) {
                "click" -> TestAction.Click(targetId = targetId, selector = selector)
                "type" -> value?.let { TestAction.Type(targetId = targetId, selector = selector, text = it) }
                "scroll_down" -> TestAction.Scroll(ScrollDirection.DOWN, selector = selector)
                "scroll_up" -> TestAction.Scroll(ScrollDirection.UP, selector = selector)
                "wait" -> TestAction.Wait(WaitCondition.Duration(value?.toLongOrNull() ?: 1000))
                "navigate" -> value?.let { TestAction.Navigate(it) }
                "go_back" -> TestAction.GoBack
                "press_key" -> value?.let { TestAction.PressKey(it) }
                "assert_visible" -> TestAction.Assert(targetId = targetId, selector = selector, assertion = AssertionType.Visible)
                "assert_text" -> value?.let {
                    TestAction.Assert(targetId = targetId, selector = selector, assertion = AssertionType.TextContains(it))
                }
                "hover" -> TestAction.Hover(targetId = targetId, selector = selector)
                "done" -> null // Signal completion
                else -> null
            }

            action?.let {
                PlannedAction(
                    action = it,
                    reasoning = reasoning,
                    confidence = confidence
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse LLM response into a TestScenario
     */
    private fun parseScenarioResponse(response: String, fallbackName: String, startUrl: String): TestScenario? {
        val jsonStr = extractJson(response) ?: return null

        return try {
            val parsed = json.decodeFromString<ScenarioResponse>(jsonStr)
            TestScenario(
                id = "scenario_${currentTimeMillis()}",
                name = parsed.name ?: fallbackName,
                description = parsed.description ?: fallbackName,
                startUrl = startUrl,
                steps = parsed.steps.mapIndexed { index, step ->
                    TestStep(
                        id = "step_$index",
                        description = step.description,
                        action = parseStepAction(step) ?: TestAction.Wait(WaitCondition.Duration(100)),
                        expectedOutcome = step.expected_outcome
                    )
                }
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseStepAction(step: StepResponse): TestAction? {
        val actionType = step.action_type ?: return null
        val targetId = step.target_id
        val value = step.value

        return when (actionType.lowercase()) {
            "click" -> targetId?.let { TestAction.Click(targetId = it) }
            "type" -> targetId?.let { id -> value?.let { TestAction.Type(targetId = id, text = it) } }
            "scroll_down" -> TestAction.Scroll(ScrollDirection.DOWN)
            "scroll_up" -> TestAction.Scroll(ScrollDirection.UP)
            "wait" -> TestAction.Wait(WaitCondition.Duration(value?.toLongOrNull() ?: 1000))
            "navigate" -> value?.let { TestAction.Navigate(it) }
            "go_back" -> TestAction.GoBack
            "press_key" -> value?.let { TestAction.PressKey(it) }
            "assert_visible" -> targetId?.let { TestAction.Assert(targetId = it, assertion = AssertionType.Visible) }
            else -> null
        }
    }

    /**
     * Extract JSON from LLM response (handles markdown code blocks)
     */
    private fun extractJson(response: String): String? {
        val trimmed = response.trim()

        // Try to find JSON in code blocks
        val codeBlockPattern = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
        val codeBlockMatch = codeBlockPattern.find(trimmed)
        if (codeBlockMatch != null) {
            return codeBlockMatch.groupValues[1].trim()
        }

        // Try to find raw JSON object
        val jsonStart = trimmed.indexOf('{')
        val jsonEnd = trimmed.lastIndexOf('}')
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return trimmed.substring(jsonStart, jsonEnd + 1)
        }

        return null
    }

    /**
     * Build prompt for action planning
     */
    private fun buildPlanningPrompt(context: WebAgentContext): String {
        return buildString {
            appendLine(WebAgentPrompts.actionPlanningIntro)
            appendLine()
            appendLine("## Test Scenario")
            appendLine("Name: ${context.scenario.name}")
            appendLine("Description: ${context.scenario.description}")
            appendLine()
            appendLine("## Current Page")
            appendLine("URL: ${context.pageState.url}")
            appendLine("Title: ${context.pageState.title}")
            appendLine()
            appendLine("## Actionable Elements")
            context.pageState.actionableElements.take(config.maxElementsInPrompt).forEach { element ->
                appendLine("[#${element.tagId}] ${element.role} \"${element.name}\" - selector: ${element.selector}")
            }
            appendLine()
            appendLine("## Recent Actions")
            context.memory.recentActions.takeLast(5).forEach { action ->
                val status = if (action.success) "OK" else "FAILED"
                appendLine("- [$status] ${action.description}")
            }
            appendLine()
            appendLine("## Progress")
            appendLine("Step ${context.currentStepIndex + 1} of ${context.scenario.steps.size}")
            appendLine("Passed: ${context.previousResults.count { it.success }}")
            appendLine("Failed: ${context.previousResults.count { !it.success }}")
            appendLine()
            appendLine("## Output Format")
            appendLine("Output a JSON object with:")
            appendLine("- action_type: click | type | scroll | wait | press_key | navigate | assert")
            appendLine("- selector: CSS selector (preferred) OR target_id: Set-of-Mark tag number")
            appendLine("- value: text to type or key to press (if applicable)")
            appendLine("- reasoning: brief explanation of why this action")
            appendLine()
            appendLine("Example: {\"action_type\":\"click\",\"selector\":\"#login-btn\",\"reasoning\":\"Click login button\"}")
            appendLine("Or: {\"action_type\":\"click\",\"target_id\":5,\"reasoning\":\"Click login button\"}")
        }
    }

    /**
     * Build prompt for scenario generation (JSON format)
     */
    private fun buildScenarioGenerationPrompt(
        description: String,
        startUrl: String,
        pageState: PageState
    ): String {
        return buildString {
            appendLine("Generate an E2E test scenario based on the following description.")
            appendLine()
            appendLine("## User Description")
            appendLine(description)
            appendLine()
            appendLine("## Starting URL")
            appendLine(startUrl)
            appendLine()
            appendLine("## Current Page State")
            appendLine("Title: ${pageState.title}")
            appendLine()
            appendLine("## Available Elements")
            pageState.actionableElements.take(30).forEach { element ->
                appendLine("[${element.tagId}] ${element.role} \"${element.name}\"")
            }
            appendLine()
            appendLine("## Output Format")
            appendLine("Output a JSON object with:")
            appendLine("- name: test scenario name")
            appendLine("- description: what this test verifies")
            appendLine("- steps: array of test steps, each with:")
            appendLine("  - description: what this step does")
            appendLine("  - action: {action_type, target_id, value}")
            appendLine("  - expected_outcome: what should happen after this step")
        }
    }

    /**
     * Build prompt for scenario generation using DSL format
     */
    private fun buildDslScenarioGenerationPrompt(
        description: String,
        startUrl: String,
        pageState: PageState
    ): String {
        return buildString {
            appendLine(WebAgentPrompts.scenarioGenerationIntro)
            appendLine()
            appendLine(WebAgentPrompts.dslSyntaxReference)
            appendLine()
            appendLine("## Test Goal")
            appendLine(description)
            appendLine()
            appendLine("## Target URL")
            appendLine(startUrl)
            appendLine()
            appendLine("## Current Page State")
            appendLine("Title: ${pageState.title}")
            appendLine("URL: ${pageState.url}")
            appendLine()
            appendLine("## Available Elements")
            pageState.actionableElements.take(config.maxElementsInPrompt).forEach { element ->
                appendLine("[#${element.tagId}] ${element.role} \"${element.name}\" - selector: ${element.selector}")
            }
            appendLine()
            appendLine("Generate a test scenario in DSL format that achieves the test goal.")
            appendLine(WebAgentPrompts.preferCssSelectorsHint)
            appendLine(WebAgentPrompts.outputOnlyDslHint)
        }
    }

    /**
     * Parse DSL scenario response from LLM
     */
    private fun parseDslScenarioResponse(response: String, fallbackName: String, startUrl: String): TestScenario? {
        val dsl = extractDsl(response)
        if (dsl.isEmpty()) return null

        return dslParser.parse(dsl, fallbackName, startUrl)
    }

    /**
     * Extract DSL from LLM response (handles markdown code blocks)
     */
    private fun extractDsl(response: String): String {
        val trimmed = response.trim()

        // Try to find DSL in code blocks
        val codeBlockPattern = Regex("```(?:e2e|dsl)?\\s*([\\s\\S]*?)```")
        val codeBlockMatch = codeBlockPattern.find(trimmed)
        if (codeBlockMatch != null) {
            return codeBlockMatch.groupValues[1].trim()
        }

        // If no code block, check if it starts with 'scenario'
        if (trimmed.startsWith("scenario")) {
            return trimmed
        }

        // Try to find scenario block
        val scenarioStart = trimmed.indexOf("scenario")
        if (scenarioStart >= 0) {
            return trimmed.substring(scenarioStart)
        }

        return ""
    }

}

/**
 * A planned action with reasoning
 */
data class PlannedAction(
    /**
     * The action to execute
     */
    val action: TestAction,

    /**
     * Reasoning for why this action was chosen
     */
    val reasoning: String,

    /**
     * Confidence score (0.0 to 1.0)
     */
    val confidence: Double,

    /**
     * Associated step ID (if from predefined scenario)
     */
    val stepId: String? = null,

    /**
     * Alternative actions considered
     */
    val alternatives: List<TestAction> = emptyList()
)

/**
 * Configuration for the planner
 */
data class PlannerConfig(
    /**
     * Maximum elements to include in prompt
     */
    val maxElementsInPrompt: Int = 50,

    /**
     * Maximum recent actions to include
     */
    val maxRecentActions: Int = 5,

    /**
     * Temperature for LLM generation
     */
    val temperature: Double = 0.3,

    /**
     * Enable chain-of-thought reasoning
     */
    val enableCoT: Boolean = true,

    /**
     * Use DSL format for scenario generation instead of JSON.
     * DSL format is more readable and reliable for LLM generation.
     */
    val useDslFormat: Boolean = true
)

/**
 * Response structure for scenario generation
 */
@Serializable
internal data class ScenarioResponse(
    val name: String? = null,
    val description: String? = null,
    val steps: List<StepResponse> = emptyList()
)

/**
 * Response structure for a single step
 */
@Serializable
internal data class StepResponse(
    val description: String = "",
    val action_type: String? = null,
    val target_id: Int? = null,
    val value: String? = null,
    val expected_outcome: String? = null
)

/**
 * Simple DSL parser for E2E test scenarios.
 *
 * Parses the DSL format into TestScenario objects.
 * This is a lightweight parser that handles the core DSL syntax.
 */
internal class SimpleDslParser {

    /**
     * Parse DSL text into a TestScenario
     */
    fun parse(dsl: String, fallbackName: String, startUrl: String): TestScenario? {
        val lines = dsl.lines()
        var scenarioName = fallbackName
        var scenarioDescription = ""
        var scenarioUrl = startUrl
        val steps = mutableListOf<TestStep>()

        var currentStepDescription = ""
        var currentStepAction: TestAction? = null
        var currentStepExpect = ""
        var inStep = false
        var stepIndex = 0

        for (line in lines) {
            val trimmed = line.trim()

            // Skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) {
                continue
            }

            when {
                // Parse scenario name
                trimmed.startsWith("scenario ") -> {
                    scenarioName = extractQuotedString(trimmed.removePrefix("scenario ")) ?: fallbackName
                }

                // Parse description
                trimmed.startsWith("description ") && !inStep -> {
                    scenarioDescription = extractQuotedString(trimmed.removePrefix("description ")) ?: ""
                }

                // Parse url
                trimmed.startsWith("url ") && !inStep -> {
                    scenarioUrl = extractQuotedString(trimmed.removePrefix("url ")) ?: startUrl
                }

                // Parse step start
                trimmed.startsWith("step ") -> {
                    // Save previous step if exists
                    if (inStep && currentStepAction != null) {
                        steps.add(TestStep(
                            id = "step_$stepIndex",
                            description = currentStepDescription,
                            action = currentStepAction!!,
                            expectedOutcome = currentStepExpect.ifEmpty { null }
                        ))
                        stepIndex++
                    }

                    inStep = true
                    currentStepDescription = extractQuotedString(trimmed.removePrefix("step ")) ?: "Step ${stepIndex + 1}"
                    currentStepAction = null
                    currentStepExpect = ""
                }

                // Parse expect in step
                trimmed.startsWith("expect ") && inStep -> {
                    currentStepExpect = extractQuotedString(trimmed.removePrefix("expect ")) ?: ""
                }

                // Parse actions in step
                inStep -> {
                    val action = parseAction(trimmed)
                    if (action != null) {
                        currentStepAction = action
                    }
                }
            }
        }

        // Save last step
        if (inStep && currentStepAction != null) {
            steps.add(TestStep(
                id = "step_$stepIndex",
                description = currentStepDescription,
                action = currentStepAction!!,
                expectedOutcome = currentStepExpect.ifEmpty { null }
            ))
        }

        if (steps.isEmpty()) return null

        return TestScenario(
            id = "scenario_${cc.unitmesh.agent.webagent.currentTimeMillis()}",
            name = scenarioName,
            description = scenarioDescription,
            startUrl = scenarioUrl,
            steps = steps
        )
    }

    /**
     * Parse a single action line
     */
    private fun parseAction(line: String): TestAction? {
        val parts = tokenizeLine(line)
        if (parts.isEmpty()) return null

        val actionType = parts[0].lowercase()

        return when (actionType) {
            "click" -> {
                val targetId = extractTargetId(parts.getOrNull(1))
                targetId?.let { TestAction.Click(it) }
            }

            "type" -> {
                val targetId = extractTargetId(parts.getOrNull(1))
                val text = parts.drop(2).firstOrNull { it.startsWith("\"") }?.let { extractQuotedString(it) }
                if (targetId != null && text != null) {
                    TestAction.Type(targetId = targetId, text = text)
                } else null
            }

            "hover" -> {
                val targetId = extractTargetId(parts.getOrNull(1))
                targetId?.let { TestAction.Hover(targetId = it) }
            }

            "scroll" -> {
                val direction = when (parts.getOrNull(1)?.lowercase()) {
                    "up" -> ScrollDirection.UP
                    "down" -> ScrollDirection.DOWN
                    "left" -> ScrollDirection.LEFT
                    "right" -> ScrollDirection.RIGHT
                    else -> ScrollDirection.DOWN
                }
                TestAction.Scroll(direction)
            }

            "wait" -> {
                val condition = parts.getOrNull(1)?.lowercase()
                val value = parts.getOrNull(2)
                when (condition) {
                    "duration" -> TestAction.Wait(WaitCondition.Duration(value?.toLongOrNull() ?: 1000))
                    "visible" -> {
                        val targetId = extractTargetId(value)
                        targetId?.let { TestAction.Wait(WaitCondition.ElementVisible(it)) }
                    }
                    "hidden" -> {
                        val targetId = extractTargetId(value)
                        targetId?.let { TestAction.Wait(WaitCondition.ElementHidden(it)) }
                    }
                    "pageloaded" -> TestAction.Wait(WaitCondition.PageLoaded())
                    "networkidle" -> TestAction.Wait(WaitCondition.NetworkIdle())
                    else -> {
                        // Default: treat as duration
                        val duration = condition?.toLongOrNull() ?: 1000
                        TestAction.Wait(WaitCondition.Duration(duration))
                    }
                }
            }

            "presskey" -> {
                val key = extractQuotedString(parts.getOrNull(1) ?: "") ?: parts.getOrNull(1)
                key?.let { TestAction.PressKey(it) }
            }

            "navigate" -> {
                val url = extractQuotedString(parts.getOrNull(1) ?: "")
                url?.let { TestAction.Navigate(it) }
            }

            "goback" -> TestAction.GoBack

            "goforward" -> TestAction.GoForward

            "refresh" -> TestAction.Refresh

            "assert" -> {
                val targetId = extractTargetId(parts.getOrNull(1))
                val assertType = parts.getOrNull(2)?.lowercase()
                val value = parts.getOrNull(3)?.let { extractQuotedString(it) }

                if (targetId != null) {
                    val assertion = when (assertType) {
                        "visible" -> AssertionType.Visible
                        "hidden" -> AssertionType.Hidden
                        "enabled" -> AssertionType.Enabled
                        "disabled" -> AssertionType.Disabled
                        "textequals" -> value?.let { AssertionType.TextEquals(it) }
                        "textcontains" -> value?.let { AssertionType.TextContains(it) }
                        else -> AssertionType.Visible
                    }
                    assertion?.let { TestAction.Assert(targetId = targetId, assertion = it) }
                } else null
            }

            "screenshot" -> {
                val name = extractQuotedString(parts.getOrNull(1) ?: "") ?: "screenshot"
                val fullPage = parts.any { it.lowercase() == "fullpage" }
                TestAction.Screenshot(name, fullPage)
            }

            else -> null
        }
    }

    /**
     * Tokenize a line into parts, respecting quoted strings
     */
    private fun tokenizeLine(line: String): List<String> {
        val parts = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> {
                    inQuotes = !inQuotes
                    current.append(char)
                }
                char.isWhitespace() && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        parts.add(current.toString())
                        current = StringBuilder()
                    }
                }
                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) {
            parts.add(current.toString())
        }

        return parts
    }

    /**
     * Extract a quoted string value
     */
    private fun extractQuotedString(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
            return trimmed.substring(1, trimmed.length - 1)
        }
        // Also try to find quoted string within the input
        val startQuote = trimmed.indexOf('"')
        val endQuote = trimmed.lastIndexOf('"')
        if (startQuote >= 0 && endQuote > startQuote) {
            return trimmed.substring(startQuote + 1, endQuote)
        }
        return null
    }

    /**
     * Extract target ID from #N format
     */
    private fun extractTargetId(input: String?): Int? {
        if (input == null) return null
        val trimmed = input.trim()
        if (trimmed.startsWith("#")) {
            return trimmed.substring(1).toIntOrNull()
        }
        return trimmed.toIntOrNull()
    }
}
