package cc.unitmesh.agent.e2etest.planner

import cc.unitmesh.agent.e2etest.E2ETestContext
import cc.unitmesh.agent.e2etest.currentTimeMillis
import cc.unitmesh.agent.e2etest.model.*
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

    /**
     * Plan the next action based on current context
     */
    suspend fun planNextAction(context: E2ETestContext): PlannedAction? {
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
     * Generate a test scenario from natural language description using LLM
     */
    suspend fun generateScenario(
        description: String,
        startUrl: String,
        pageState: PageState
    ): TestScenario? {
        val prompt = buildScenarioGenerationPrompt(description, startUrl, pageState)

        return try {
            val response = llmService.sendPrompt(prompt)
            parseScenarioResponse(response, description, startUrl)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Plan action using LLM
     */
    private suspend fun planWithLLM(context: E2ETestContext): PlannedAction? {
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
            val targetId = jsonObj["target_id"]?.jsonPrimitive?.intOrNull
            val value = jsonObj["value"]?.jsonPrimitive?.content
            val reasoning = jsonObj["reasoning"]?.jsonPrimitive?.content ?: "LLM planned action"
            val confidence = jsonObj["confidence"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.8

            val action = when (actionType.lowercase()) {
                "click" -> targetId?.let { TestAction.Click(it) }
                "type" -> targetId?.let { id -> value?.let { TestAction.Type(id, it) } }
                "scroll_down" -> TestAction.Scroll(ScrollDirection.DOWN)
                "scroll_up" -> TestAction.Scroll(ScrollDirection.UP)
                "wait" -> TestAction.Wait(WaitCondition.Duration(value?.toLongOrNull() ?: 1000))
                "navigate" -> value?.let { TestAction.Navigate(it) }
                "go_back" -> TestAction.GoBack
                "press_key" -> value?.let { TestAction.PressKey(it) }
                "assert_visible" -> targetId?.let { TestAction.Assert(it, AssertionType.Visible) }
                "assert_text" -> targetId?.let { id ->
                    value?.let { TestAction.Assert(id, AssertionType.TextContains(it)) }
                }
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
            "click" -> targetId?.let { TestAction.Click(it) }
            "type" -> targetId?.let { id -> value?.let { TestAction.Type(id, it) } }
            "scroll_down" -> TestAction.Scroll(ScrollDirection.DOWN)
            "scroll_up" -> TestAction.Scroll(ScrollDirection.UP)
            "wait" -> TestAction.Wait(WaitCondition.Duration(value?.toLongOrNull() ?: 1000))
            "navigate" -> value?.let { TestAction.Navigate(it) }
            "go_back" -> TestAction.GoBack
            "press_key" -> value?.let { TestAction.PressKey(it) }
            "assert_visible" -> targetId?.let { TestAction.Assert(it, AssertionType.Visible) }
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
    private fun buildPlanningPrompt(context: E2ETestContext): String {
        return buildString {
            appendLine("You are an E2E testing agent. Analyze the current page state and determine the next action.")
            appendLine()
            appendLine("## Test Scenario")
            appendLine("Name: ${context.scenario.name}")
            appendLine("Description: ${context.scenario.description}")
            appendLine()
            appendLine("## Current Page")
            appendLine("URL: ${context.pageState.url}")
            appendLine("Title: ${context.pageState.title}")
            appendLine()
            appendLine("## Actionable Elements (Set-of-Mark)")
            context.pageState.actionableElements.take(config.maxElementsInPrompt).forEach { element ->
                appendLine("[${element.tagId}] ${element.role} \"${element.name}\" - ${element.tagName}")
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
            appendLine("- target_id: Set-of-Mark tag number (for element actions)")
            appendLine("- value: text to type or key to press (if applicable)")
            appendLine("- reasoning: brief explanation of why this action")
            appendLine()
            appendLine("Example: {\"action_type\":\"click\",\"target_id\":5,\"reasoning\":\"Click login button\"}")
        }
    }

    /**
     * Build prompt for scenario generation
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
    val enableCoT: Boolean = true
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
