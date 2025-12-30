package cc.unitmesh.agent.e2etest.model

import kotlinx.serialization.Serializable

/**
 * Represents a single step in an E2E test execution.
 * 
 * A step contains the action to perform, the element fingerprint for self-healing,
 * and execution metadata.
 * 
 * @see <a href="https://github.com/phodal/auto-dev/issues/532">Issue #532</a>
 */
@Serializable
data class TestStep(
    /**
     * Unique identifier for this step
     */
    val id: String,

    /**
     * Human-readable description of what this step does
     */
    val description: String,

    /**
     * The action to perform
     */
    val action: TestAction,

    /**
     * Element fingerprint for self-healing locator
     * Contains multiple attributes to identify the element if primary selector fails
     */
    val elementFingerprint: ElementFingerprint? = null,

    /**
     * Expected outcome after this step (for verification)
     */
    val expectedOutcome: String? = null,

    /**
     * Screenshot to capture after this step (optional)
     */
    val captureScreenshot: Boolean = false,

    /**
     * Timeout for this specific step (overrides default)
     */
    val timeoutMs: Long? = null,

    /**
     * Whether to continue test execution if this step fails
     */
    val continueOnFailure: Boolean = false,

    /**
     * Retry count for flaky steps
     */
    val retryCount: Int = 0
)

/**
 * Element fingerprint for self-healing locator.
 * 
 * When the primary selector fails, the self-healing algorithm uses these
 * attributes to find the most similar element in the current DOM.
 * 
 * Inspired by Mabl's weighted attribute scoring algorithm.
 */
@Serializable
data class ElementFingerprint(
    /**
     * Primary CSS selector
     */
    val selector: String,

    /**
     * HTML tag name (e.g., "button", "input", "div")
     */
    val tagName: String,

    /**
     * Element ID attribute (if present)
     */
    val id: String? = null,

    /**
     * CSS class names
     */
    val classNames: List<String> = emptyList(),

    /**
     * Text content of the element
     */
    val textContent: String? = null,

    /**
     * Inner text (visible text only)
     */
    val innerText: String? = null,

    /**
     * ARIA role
     */
    val role: String? = null,

    /**
     * ARIA label
     */
    val ariaLabel: String? = null,

    /**
     * Name attribute
     */
    val name: String? = null,

    /**
     * Placeholder text (for inputs)
     */
    val placeholder: String? = null,

    /**
     * data-testid attribute (high confidence)
     */
    val testId: String? = null,

    /**
     * XPath as fallback
     */
    val xpath: String? = null,

    /**
     * Relative position in parent
     */
    val childIndex: Int? = null,

    /**
     * Parent element info for context
     */
    val parentInfo: ParentInfo? = null,

    /**
     * Bounding box coordinates (for visual matching)
     */
    val boundingBox: BoundingBox? = null,

    /**
     * Additional custom attributes
     */
    val customAttributes: Map<String, String> = emptyMap()
)

@Serializable
data class ParentInfo(
    val tagName: String,
    val id: String? = null,
    val classNames: List<String> = emptyList()
)

@Serializable
data class BoundingBox(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double
)

/**
 * Result of executing a single test step
 */
@Serializable
data class StepResult(
    /**
     * The step that was executed
     */
    val stepId: String,

    /**
     * Whether the step succeeded
     */
    val success: Boolean,

    /**
     * Execution duration in milliseconds
     */
    val durationMs: Long,

    /**
     * Error message if failed
     */
    val error: String? = null,

    /**
     * Whether self-healing was triggered
     */
    val selfHealed: Boolean = false,

    /**
     * New selector if self-healing succeeded
     */
    val healedSelector: String? = null,

    /**
     * Screenshot path if captured
     */
    val screenshotPath: String? = null,

    /**
     * Actual outcome observed
     */
    val actualOutcome: String? = null,

    /**
     * Number of retries attempted
     */
    val retriesAttempted: Int = 0
)
