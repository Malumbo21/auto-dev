package cc.unitmesh.agent.e2etest.model

import kotlinx.serialization.Serializable

/**
 * Represents the current state of a web page for agent perception.
 * 
 * Combines DOM structure, accessibility tree, and visual information
 * to provide comprehensive page understanding.
 * 
 * @see <a href="https://github.com/phodal/auto-dev/issues/532">Issue #532</a>
 */
@Serializable
data class PageState(
    /**
     * Current page URL
     */
    val url: String,

    /**
     * Page title
     */
    val title: String,

    /**
     * Viewport dimensions
     */
    val viewport: Viewport,

    /**
     * Actionable elements with Set-of-Mark tags
     */
    val actionableElements: List<ActionableElement>,

    /**
     * Simplified accessibility tree (high signal-to-noise ratio)
     */
    val accessibilityTree: AccessibilityNode? = null,

    /**
     * Cleaned DOM summary (for context)
     */
    val domSummary: String? = null,

    /**
     * Screenshot with SoM annotations (base64)
     */
    val annotatedScreenshot: String? = null,

    /**
     * Page load state
     */
    val loadState: PageLoadState = PageLoadState.COMPLETE,

    /**
     * Active element (focused)
     */
    val activeElementId: Int? = null,

    /**
     * Scroll position
     */
    val scrollPosition: ScrollPosition = ScrollPosition(0, 0),

    /**
     * Detected framework (React, Vue, Angular, etc.)
     */
    val detectedFramework: String? = null,

    /**
     * Timestamp when state was captured
     */
    val capturedAt: Long
)

@Serializable
data class Viewport(
    val width: Int,
    val height: Int
)

@Serializable
data class ScrollPosition(
    val x: Int,
    val y: Int
)

@Serializable
enum class PageLoadState {
    LOADING,
    INTERACTIVE,
    COMPLETE
}

/**
 * An actionable element on the page with Set-of-Mark tag.
 * 
 * Set-of-Mark (SoM) assigns visible numeric tags to interactive elements,
 * allowing LLMs to reference elements by number instead of coordinates.
 */
@Serializable
data class ActionableElement(
    /**
     * Set-of-Mark tag number (displayed on screenshot)
     */
    val tagId: Int,

    /**
     * CSS selector for this element
     */
    val selector: String,

    /**
     * HTML tag name
     */
    val tagName: String,

    /**
     * ARIA role or inferred role
     */
    val role: String,

    /**
     * Accessible name (from aria-label, text content, etc.)
     */
    val name: String,

    /**
     * Whether element is currently visible in viewport
     */
    val isVisible: Boolean,

    /**
     * Whether element is enabled/interactive
     */
    val isEnabled: Boolean,

    /**
     * Bounding box in viewport coordinates
     */
    val boundingBox: BoundingBox,

    /**
     * Element fingerprint for self-healing
     */
    val fingerprint: ElementFingerprint,

    /**
     * Input type (for input elements)
     */
    val inputType: String? = null,

    /**
     * Current value (for inputs, selects)
     */
    val value: String? = null,

    /**
     * Placeholder text
     */
    val placeholder: String? = null,

    /**
     * Whether element is in a shadow DOM
     */
    val inShadowDom: Boolean = false,

    /**
     * Parent frame index (0 for main frame)
     */
    val frameIndex: Int = 0
)

/**
 * Accessibility tree node - simplified representation.
 * 
 * Accessibility tree has much higher signal-to-noise ratio than raw DOM,
 * reducing token usage by 60-80% while preserving semantic information.
 */
@Serializable
data class AccessibilityNode(
    /**
     * ARIA role
     */
    val role: String,

    /**
     * Accessible name
     */
    val name: String,

    /**
     * Accessible description
     */
    val description: String? = null,

    /**
     * Accessible value
     */
    val value: String? = null,

    /**
     * Child nodes
     */
    val children: List<AccessibilityNode> = emptyList(),

    /**
     * Whether this node is focusable
     */
    val focusable: Boolean = false,

    /**
     * Whether this node is focused
     */
    val focused: Boolean = false,

    /**
     * State flags (checked, expanded, selected, etc.)
     */
    val states: Set<AccessibilityState> = emptySet(),

    /**
     * Corresponding SoM tag ID (if actionable)
     */
    val tagId: Int? = null
)

@Serializable
enum class AccessibilityState {
    CHECKED,
    UNCHECKED,
    EXPANDED,
    COLLAPSED,
    SELECTED,
    DISABLED,
    READONLY,
    REQUIRED,
    INVALID,
    BUSY
}

/**
 * Screenshot with Set-of-Mark annotations
 */
@Serializable
data class SoMScreenshot(
    /**
     * Base64 encoded image with SoM tags drawn
     */
    val imageBase64: String,

    /**
     * Image format (jpeg, png)
     */
    val format: String,

    /**
     * Image width
     */
    val width: Int,

    /**
     * Image height
     */
    val height: Int,

    /**
     * Mapping from tag ID to element info
     */
    val tagMapping: Map<Int, ActionableElement>
)
