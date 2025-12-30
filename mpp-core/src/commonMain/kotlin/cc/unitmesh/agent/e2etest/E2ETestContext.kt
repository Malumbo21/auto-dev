package cc.unitmesh.agent.e2etest

import cc.unitmesh.agent.e2etest.model.*
import cc.unitmesh.devins.compiler.variable.VariableTable
import cc.unitmesh.devins.compiler.variable.VariableType
import kotlinx.serialization.Serializable

/**
 * Context for E2E Testing Agent execution.
 * 
 * Contains all information needed for the agent to understand the current state
 * and make decisions about next actions.
 * 
 * @see <a href="https://github.com/phodal/auto-dev/issues/532">Issue #532</a>
 */
@Serializable
data class E2ETestContext(
    /**
     * The test scenario being executed
     */
    val scenario: TestScenario,

    /**
     * Current page state
     */
    val pageState: PageState,

    /**
     * Index of current step being executed
     */
    val currentStepIndex: Int,

    /**
     * Results of previously executed steps
     */
    val previousResults: List<StepResult>,

    /**
     * Short-term memory of recent actions and observations
     */
    val memory: TestMemory,

    /**
     * Test variables (can be updated during execution)
     */
    val variables: MutableMap<String, String>,

    /**
     * Configuration options
     */
    val config: E2ETestConfig
) : cc.unitmesh.agent.AgentContext {
    
    override fun toVariableTable(): VariableTable {
        val table = VariableTable()
        table.addVariable("scenarioId", VariableType.STRING, scenario.id)
        table.addVariable("scenarioName", VariableType.STRING, scenario.name)
        table.addVariable("currentUrl", VariableType.STRING, pageState.url)
        table.addVariable("pageTitle", VariableType.STRING, pageState.title)
        table.addVariable("currentStep", VariableType.NUMBER, currentStepIndex)
        table.addVariable("totalSteps", VariableType.NUMBER, scenario.steps.size)
        table.addVariable("passedSteps", VariableType.NUMBER, previousResults.count { it.success })
        table.addVariable("failedSteps", VariableType.NUMBER, previousResults.count { !it.success })
        table.addVariable("actionableElements", VariableType.NUMBER, pageState.actionableElements.size)
        variables.forEach { (key, value) ->
            table.addVariable(key, VariableType.STRING, value)
        }
        return table
    }

    /**
     * Get the current step to execute
     */
    val currentStep: TestStep?
        get() = scenario.steps.getOrNull(currentStepIndex)

    /**
     * Check if all steps have been executed
     */
    val isComplete: Boolean
        get() = currentStepIndex >= scenario.steps.size

    /**
     * Get remaining steps count
     */
    val remainingSteps: Int
        get() = (scenario.steps.size - currentStepIndex).coerceAtLeast(0)
}

/**
 * Short-term memory for the testing agent.
 * 
 * Maintains recent history to prevent loops and provide context for decisions.
 */
@Serializable
data class TestMemory(
    /**
     * Recent actions taken (last N)
     */
    val recentActions: List<ActionRecord>,

    /**
     * Recent page states (last N)
     */
    val recentPageStates: List<PageStateSummary>,

    /**
     * Errors encountered
     */
    val errors: List<ErrorRecord>,

    /**
     * Self-healing history
     */
    val healingHistory: List<HealingRecord>,

    /**
     * Maximum items to keep in memory
     */
    val maxSize: Int = 10
) {
    /**
     * Check if we're in a potential loop (same action repeated)
     */
    fun isInLoop(): Boolean {
        if (recentActions.size < 3) return false
        val last3 = recentActions.takeLast(3)
        return last3.all { it.actionType == last3.first().actionType && it.targetId == last3.first().targetId }
    }

    /**
     * Add an action to memory
     */
    fun withAction(action: ActionRecord): TestMemory {
        val updated = (recentActions + action).takeLast(maxSize)
        return copy(recentActions = updated)
    }

    /**
     * Add a page state summary to memory
     */
    fun withPageState(state: PageStateSummary): TestMemory {
        val updated = (recentPageStates + state).takeLast(maxSize)
        return copy(recentPageStates = updated)
    }

    /**
     * Add an error to memory
     */
    fun withError(error: ErrorRecord): TestMemory {
        val updated = (errors + error).takeLast(maxSize)
        return copy(errors = updated)
    }

    /**
     * Add a healing record to memory
     */
    fun withHealing(healing: HealingRecord): TestMemory {
        val updated = (healingHistory + healing).takeLast(maxSize)
        return copy(healingHistory = updated)
    }

    companion object {
        fun empty(maxSize: Int = 10) = TestMemory(
            recentActions = emptyList(),
            recentPageStates = emptyList(),
            errors = emptyList(),
            healingHistory = emptyList(),
            maxSize = maxSize
        )
    }
}

@Serializable
data class ActionRecord(
    val actionType: String,
    val targetId: Int?,
    val timestamp: Long,
    val success: Boolean,
    val description: String
)

@Serializable
data class PageStateSummary(
    val url: String,
    val title: String,
    val actionableCount: Int,
    val timestamp: Long
)

@Serializable
data class ErrorRecord(
    val stepId: String,
    val errorType: String,
    val message: String,
    val timestamp: Long
)

@Serializable
data class HealingRecord(
    val stepId: String,
    val originalSelector: String,
    val healedSelector: String,
    val healingLevel: HealingLevel,
    val timestamp: Long
)

@Serializable
enum class HealingLevel {
    /**
     * Algorithm-based healing (milliseconds, low cost)
     */
    L1_ALGORITHM,

    /**
     * LLM semantic healing (seconds, high cost)
     */
    L2_LLM_SEMANTIC
}

/**
 * Configuration for E2E test execution
 */
@Serializable
data class E2ETestConfig(
    /**
     * Default timeout for actions in milliseconds
     */
    val defaultTimeoutMs: Long = 5000,

    /**
     * Default retry count for flaky steps
     */
    val defaultRetryCount: Int = 2,

    /**
     * Enable self-healing locators
     */
    val enableSelfHealing: Boolean = true,

    /**
     * Self-healing confidence threshold (0.0 to 1.0)
     */
    val healingThreshold: Double = 0.8,

    /**
     * Enable L2 LLM semantic healing (more expensive)
     */
    val enableLLMHealing: Boolean = true,

    /**
     * Capture screenshots on failure
     */
    val screenshotOnFailure: Boolean = true,

    /**
     * Capture screenshots on each step
     */
    val screenshotOnEachStep: Boolean = false,

    /**
     * Viewport width
     */
    val viewportWidth: Int = 1280,

    /**
     * Viewport height
     */
    val viewportHeight: Int = 720,

    /**
     * Headless mode (no visible browser)
     */
    val headless: Boolean = false,

    /**
     * Slow motion delay between actions (for debugging)
     */
    val slowMotionMs: Long = 0,

    /**
     * Maximum memory items to keep
     */
    val maxMemorySize: Int = 10
)
