package cc.unitmesh.agent.webagent.model

import kotlinx.serialization.Serializable

/**
 * Result of an E2E test execution.
 * 
 * @see <a href="https://github.com/phodal/auto-dev/issues/532">Issue #532</a>
 */
@Serializable
data class E2ETestResult(
    /**
     * Test scenario identifier
     */
    val scenarioId: String,

    /**
     * Test scenario name
     */
    val scenarioName: String,

    /**
     * Overall test status
     */
    val status: TestStatus,

    /**
     * Results for each step
     */
    val stepResults: List<StepResult>,

    /**
     * Total execution time in milliseconds
     */
    val totalDurationMs: Long,

    /**
     * Number of steps that passed
     */
    val passedSteps: Int,

    /**
     * Number of steps that failed
     */
    val failedSteps: Int,

    /**
     * Number of steps that were skipped
     */
    val skippedSteps: Int,

    /**
     * Number of steps that required self-healing
     */
    val selfHealedSteps: Int,

    /**
     * Error summary if test failed
     */
    val errorSummary: String? = null,

    /**
     * Final page URL
     */
    val finalUrl: String? = null,

    /**
     * Final page title
     */
    val finalTitle: String? = null,

    /**
     * Screenshots captured during test
     */
    val screenshots: List<ScreenshotInfo> = emptyList(),

    /**
     * Browser/environment info
     */
    val environment: TestEnvironment? = null,

    /**
     * Timestamp when test started
     */
    val startedAt: Long,

    /**
     * Timestamp when test completed
     */
    val completedAt: Long
) {
    val successRate: Double
        get() = if (stepResults.isEmpty()) 0.0 else passedSteps.toDouble() / stepResults.size
}

@Serializable
enum class TestStatus {
    PASSED,
    FAILED,
    SKIPPED,
    ERROR,
    TIMEOUT
}

@Serializable
data class ScreenshotInfo(
    val name: String,
    val path: String,
    val stepId: String?,
    val timestamp: Long,
    val width: Int,
    val height: Int
)

@Serializable
data class TestEnvironment(
    val browserName: String,
    val browserVersion: String,
    val platform: String,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val userAgent: String? = null
)

/**
 * Test scenario definition - a complete test case
 */
@Serializable
data class TestScenario(
    /**
     * Unique identifier
     */
    val id: String,

    /**
     * Human-readable name
     */
    val name: String,

    /**
     * Description of what this test verifies
     */
    val description: String,

    /**
     * Starting URL for the test
     */
    val startUrl: String,

    /**
     * Ordered list of test steps
     */
    val steps: List<TestStep>,

    /**
     * Tags for categorization
     */
    val tags: List<String> = emptyList(),

    /**
     * Priority level
     */
    val priority: TestPriority = TestPriority.MEDIUM,

    /**
     * Maximum execution time for entire scenario
     */
    val timeoutMs: Long = 60000,

    /**
     * Setup actions to run before test
     */
    val setup: List<TestAction> = emptyList(),

    /**
     * Teardown actions to run after test
     */
    val teardown: List<TestAction> = emptyList(),

    /**
     * Test data/variables
     */
    val variables: Map<String, String> = emptyMap()
)

@Serializable
enum class TestPriority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}

/**
 * Test suite - a collection of test scenarios
 */
@Serializable
data class TestSuite(
    val id: String,
    val name: String,
    val description: String,
    val scenarios: List<TestScenario>,
    val parallelExecution: Boolean = false,
    val stopOnFirstFailure: Boolean = false
)

/**
 * Result of running a test suite
 */
@Serializable
data class TestSuiteResult(
    val suiteId: String,
    val suiteName: String,
    val status: TestStatus,
    val scenarioResults: List<E2ETestResult>,
    val totalDurationMs: Long,
    val passedScenarios: Int,
    val failedScenarios: Int,
    val skippedScenarios: Int,
    val startedAt: Long,
    val completedAt: Long
)
