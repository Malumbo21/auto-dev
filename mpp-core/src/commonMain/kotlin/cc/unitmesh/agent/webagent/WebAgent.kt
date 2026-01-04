package cc.unitmesh.agent.webagent

import cc.unitmesh.agent.core.SubAgent
import cc.unitmesh.agent.webagent.executor.*
import cc.unitmesh.agent.webagent.model.*
import cc.unitmesh.agent.webagent.perception.PageStateExtractor
import cc.unitmesh.agent.webagent.perception.PageStateExtractorConfig
import cc.unitmesh.agent.webagent.perception.createPageStateExtractor
import cc.unitmesh.agent.webagent.planner.TestActionPlanner
import cc.unitmesh.agent.webagent.prompt.WebAgentPrompts
import cc.unitmesh.agent.model.AgentDefinition
import cc.unitmesh.agent.model.PromptConfig
import cc.unitmesh.agent.tool.ToolResult
import cc.unitmesh.llm.LLMService
import cc.unitmesh.llm.ModelConfig
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

/**
 * Web Agent - AI-driven web automation with visual understanding.
 *
 * Features:
 * - Natural language scenario generation for testing and RPA
 * - Multi-modal perception (DOM + Accessibility Tree + Vision)
 * - Self-healing locators with two-level strategy
 * - Deterministic execution with AI planning
 *
 * @see <a href="https://github.com/phodal/auto-dev/issues/532">Issue #532</a>
 */
class WebAgent(
    private val llmService: LLMService,
    private val config: WebAgentConfig = WebAgentConfig()
) : SubAgent<WebAgentInput, ToolResult.AgentResult>(
    AgentDefinition(
        name = "WebAgent",
        displayName = "Web Automation Agent",
        description = "AI-driven web automation with visual understanding and self-healing locators for testing and RPA",
        promptConfig = PromptConfig(
            systemPrompt = WebAgentPrompts.systemPrompt,
            queryTemplate = null,
            initialMessages = emptyList()
        ),
        modelConfig = ModelConfig.default(),
        runConfig = cc.unitmesh.agent.model.RunConfig(
            maxTurns = 50,
            maxTimeMinutes = 10,
            terminateOnError = false
        )
    )
) {
    private var pageStateExtractor: PageStateExtractor? = null
    private var browserExecutor: DriverBasedBrowserActionExecutor? = null
    private var planner: TestActionPlanner? = null
    private var selfHealingLocator: SelfHealingLocator? = null

    /**
     * Check if the agent is available on this platform
     */
    override val isAvailable: Boolean
        get() = pageStateExtractor?.isAvailable == true && browserExecutor?.isAvailable == true

    /**
     * Initialize the agent with browser components using platform factory functions.
     *
     * Note: This method only initializes the PageStateExtractor and planner.
     * The browserExecutor will be null - use [initializeWithDriver]
     * to provide a BrowserDriver for full functionality.
     */
    fun initialize() {
        val extractorConfig = PageStateExtractorConfig(
            viewportWidth = config.viewportWidth,
            viewportHeight = config.viewportHeight
        )
        pageStateExtractor = createPageStateExtractor(extractorConfig)

        // Note: browserExecutor is not set here because createBrowserActionExecutor
        // returns BrowserActionExecutor? which is platform-specific.
        // Use initializeWithDriver() to provide a BrowserDriver.
        browserExecutor = null

        selfHealingLocator = SelfHealingLocator(
            llmService = if (config.enableLLMHealing) llmService else null,
            config = SelfHealingConfig(threshold = config.healingThreshold)
        )

        planner = TestActionPlanner(llmService)
    }

    /**
     * Initialize the agent with a BrowserDriver.
     *
     * This creates a DriverBasedBrowserActionExecutor from the driver.
     * You still need to provide a PageStateExtractor.
     *
     * @param driver The BrowserDriver to use for browser operations
     * @param extractor The PageStateExtractor to use for page state extraction
     */
    fun initializeWithDriver(
        driver: BrowserDriver,
        extractor: PageStateExtractor
    ) {
        pageStateExtractor = extractor
        browserExecutor = DriverBasedBrowserActionExecutor(
            driver = driver,
            config = BrowserExecutorConfig(
                headless = config.headless,
                viewportWidth = config.viewportWidth,
                viewportHeight = config.viewportHeight,
                defaultTimeoutMs = config.defaultTimeoutMs,
                slowMotionMs = config.slowMotionMs
            )
        )

        selfHealingLocator = SelfHealingLocator(
            llmService = if (config.enableLLMHealing) llmService else null,
            config = SelfHealingConfig(threshold = config.healingThreshold)
        )

        planner = TestActionPlanner(llmService)
    }

    override fun validateInput(input: Map<String, Any>): WebAgentInput {
        val scenario = input["scenario"] as? TestScenario
        val naturalLanguage = input["naturalLanguage"] as? String
        val startUrl = input["startUrl"] as? String

        if (scenario == null && naturalLanguage == null) {
            throw IllegalArgumentException("Either 'scenario' or 'naturalLanguage' is required")
        }

        return WebAgentInput(
            scenario = scenario,
            naturalLanguage = naturalLanguage,
            startUrl = startUrl ?: "about:blank"
        )
    }

    override suspend fun execute(
        input: WebAgentInput,
        onProgress: (String) -> Unit
    ): ToolResult.AgentResult {
        if (!isAvailable) {
            return ToolResult.AgentResult(
                success = false,
                content = "E2E Testing Agent is not available on this platform"
            )
        }

        try {
            // Navigate to start URL
            onProgress("Navigating to ${input.startUrl}...")
            browserExecutor?.navigateTo(input.startUrl)
            delay(1000) // Wait for page load

            // Get or generate scenario
            val scenario = input.scenario ?: run {
                onProgress("Generating test scenario from description...")
                val pageState = pageStateExtractor?.extractPageState()
                    ?: return ToolResult.AgentResult(
                        success = false,
                        content = "Failed to extract page state"
                    )
                
                planner?.generateScenario(
                    input.naturalLanguage ?: "",
                    input.startUrl,
                    pageState
                ) ?: return ToolResult.AgentResult(
                    success = false,
                    content = "Failed to generate scenario"
                )
            }

            // Execute scenario
            onProgress("Executing scenario: ${scenario.name}")
            val result = executeScenario(scenario, onProgress)

            return ToolResult.AgentResult(
                success = result.status == TestStatus.PASSED,
                content = formatTestResult(result),
                metadata = mapOf(
                    "scenarioId" to result.scenarioId,
                    "scenarioName" to result.scenarioName,
                    "passedSteps" to result.passedSteps.toString(),
                    "failedSteps" to result.failedSteps.toString(),
                    "selfHealedSteps" to result.selfHealedSteps.toString(),
                    "durationMs" to result.totalDurationMs.toString()
                )
            )
        } catch (e: Exception) {
            return ToolResult.AgentResult(
                success = false,
                content = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Format test result for output
     */
    private fun formatTestResult(result: E2ETestResult): String {
        return buildString {
            appendLine("Test ${if (result.status == TestStatus.PASSED) "PASSED" else "FAILED"}: ${result.scenarioName}")
            appendLine("Steps: ${result.passedSteps}/${result.stepResults.size} passed")
            appendLine("Duration: ${result.totalDurationMs}ms")
            if (result.selfHealedSteps > 0) {
                appendLine("Self-healed: ${result.selfHealedSteps} steps")
            }
            if (result.errorSummary != null) {
                appendLine("Error: ${result.errorSummary}")
            }
        }
    }

    /**
     * Execute a test scenario
     */
    private suspend fun executeScenario(
        scenario: TestScenario,
        onProgress: (String) -> Unit
    ): E2ETestResult {
        val startTime = currentTimeMillis()
        val stepResults = mutableListOf<StepResult>()
        var memory = TestMemory.empty(config.maxMemorySize)

        // Execute setup actions
        scenario.setup.forEach { action ->
            browserExecutor?.execute(action, ActionExecutionContext(emptyMap()))
        }

        // Execute each step
        for ((index, step) in scenario.steps.withIndex()) {
            onProgress("Step ${index + 1}/${scenario.steps.size}: ${step.description}")

            val stepResult = executeStep(step, memory)
            stepResults.add(stepResult)

            // Update memory
            memory = memory.withAction(
                ActionRecord(
                    actionType = step.action::class.simpleName ?: "Unknown",
                    targetId = getTargetId(step.action),
                    timestamp = currentTimeMillis(),
                    success = stepResult.success,
                    description = step.description
                )
            )

            if (!stepResult.success && !step.continueOnFailure) {
                onProgress("Step failed: ${stepResult.error}")
                break
            }

            // Slow motion delay
            if (config.slowMotionMs > 0) {
                delay(config.slowMotionMs)
            }
        }

        // Execute teardown actions
        scenario.teardown.forEach { action ->
            browserExecutor?.execute(action, ActionExecutionContext(emptyMap()))
        }

        val endTime = currentTimeMillis()
        val passed = stepResults.count { it.success }
        val failed = stepResults.count { !it.success }
        val selfHealed = stepResults.count { it.selfHealed }

        return E2ETestResult(
            scenarioId = scenario.id,
            scenarioName = scenario.name,
            status = if (failed == 0) TestStatus.PASSED else TestStatus.FAILED,
            stepResults = stepResults,
            totalDurationMs = endTime - startTime,
            passedSteps = passed,
            failedSteps = failed,
            skippedSteps = scenario.steps.size - stepResults.size,
            selfHealedSteps = selfHealed,
            errorSummary = if (failed > 0) "Failed $failed of ${stepResults.size} steps" else null,
            finalUrl = pageStateExtractor?.getCurrentUrl(),
            finalTitle = pageStateExtractor?.getPageTitle(),
            startedAt = startTime,
            completedAt = endTime
        )
    }

    /**
     * Execute a single test step with self-healing
     */
    private suspend fun executeStep(step: TestStep, memory: TestMemory): StepResult {
        val startTime = currentTimeMillis()
        var retries = 0
        var lastError: String? = null
        var selfHealed = false
        var healedSelector: String? = null

        while (retries <= step.retryCount) {
            try {
                // Get current page state
                val pageState = pageStateExtractor?.extractPageState()
                    ?: return StepResult(
                        stepId = step.id,
                        success = false,
                        durationMs = currentTimeMillis() - startTime,
                        error = "Failed to extract page state"
                    )

                // Build execution context
                val tagMapping = pageState.actionableElements.associateBy { it.tagId }
                val context = ActionExecutionContext(
                    tagMapping = tagMapping,
                    selfHealingLocator = selfHealingLocator,
                    timeoutMs = step.timeoutMs ?: config.defaultTimeoutMs
                )

                // Execute action
                val result = browserExecutor?.execute(step.action, context)
                    ?: return StepResult(
                        stepId = step.id,
                        success = false,
                        durationMs = currentTimeMillis() - startTime,
                        error = "Browser executor not available"
                    )

                if (result.success) {
                    return StepResult(
                        stepId = step.id,
                        success = true,
                        durationMs = currentTimeMillis() - startTime,
                        selfHealed = result.selfHealed || selfHealed,
                        healedSelector = result.healedSelector ?: healedSelector,
                        retriesAttempted = retries
                    )
                }

                lastError = result.error

                // Try self-healing if enabled
                if (config.enableSelfHealing && step.elementFingerprint != null) {
                    val healingResult = selfHealingLocator?.healWithAlgorithm(
                        step.elementFingerprint.selector,
                        step.elementFingerprint,
                        pageState.actionableElements
                    )

                    if (healingResult != null) {
                        selfHealed = true
                        healedSelector = healingResult.healedSelector
                        // Retry with healed selector would happen on next iteration
                    }
                }

            } catch (e: Exception) {
                lastError = e.message
            }

            retries++
            if (retries <= step.retryCount) {
                delay(500) // Brief delay before retry
            }
        }

        return StepResult(
            stepId = step.id,
            success = false,
            durationMs = currentTimeMillis() - startTime,
            error = lastError ?: "Unknown error",
            selfHealed = selfHealed,
            healedSelector = healedSelector,
            retriesAttempted = retries
        )
    }

    /**
     * Get target ID from action (if applicable)
     */
    private fun getTargetId(action: TestAction): Int? {
        return when (action) {
            is TestAction.Click -> action.targetId
            is TestAction.Type -> action.targetId
            is TestAction.Hover -> action.targetId
            is TestAction.Scroll -> action.targetId
            is TestAction.Assert -> action.targetId
            is TestAction.Select -> action.targetId
            is TestAction.UploadFile -> action.targetId
            else -> null
        }
    }

    override fun formatOutput(output: ToolResult.AgentResult): String {
        return output.content
    }

    /**
     * Cleanup resources
     */
    fun close() {
        pageStateExtractor?.close()
        browserExecutor?.close()
    }

}

/**
 * Input for Web Agent
 */
@Serializable
data class WebAgentInput(
    /**
     * Predefined test scenario (optional)
     */
    val scenario: TestScenario? = null,

    /**
     * Natural language description to generate scenario (optional)
     */
    val naturalLanguage: String? = null,

    /**
     * Starting URL for the automation
     */
    val startUrl: String
)

/**
 * Platform-specific time function
 */
fun currentTimeMillis(): Long {
    return kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}
