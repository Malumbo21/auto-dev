package cc.unitmesh.e2e.dsl

import cc.unitmesh.agent.webagent.model.*
import kotlinx.serialization.Serializable

/**
 * E2E Test DSL - A human-readable domain-specific language for E2E testing.
 *
 * DSL Syntax Example:
 * ```
 * scenario "User Login Test" {
 *     description "Verify user can login with valid credentials"
 *     url "https://example.com/login"
 *     tags ["login", "auth"]
 *     priority high
 *
 *     step "Enter username" {
 *         type #1 "testuser"
 *     }
 *
 *     step "Enter password" {
 *         type #2 "password123" clearFirst pressEnter
 *     }
 *
 *     step "Click login button" {
 *         click #3
 *         expect "Dashboard should appear"
 *     }
 *
 *     step "Verify login success" {
 *         assert #4 visible
 *         assert #5 textContains "Welcome"
 *     }
 * }
 * ```
 *
 * Action Keywords:
 * - click #id [left|right|middle] [double]
 * - type #id "text" [clearFirst] [pressEnter]
 * - hover #id
 * - scroll up|down|left|right [amount] [#id]
 * - wait duration|visible|hidden|enabled|textPresent|urlContains|pageLoaded|networkIdle [value] [timeout]
 * - pressKey "key" [ctrl] [alt] [shift] [meta]
 * - navigate "url"
 * - goBack
 * - goForward
 * - refresh
 * - assert #id visible|hidden|enabled|disabled|checked|unchecked|textEquals|textContains|attributeEquals|hasClass [value]
 * - select #id [value "v"] [label "l"] [index n]
 * - uploadFile #id "path"
 * - screenshot "name" [fullPage]
 *
 * @see <a href="https://github.com/phodal/auto-dev/issues/532">Issue #532</a>
 */
object E2EDsl {
    const val VERSION = "1.0.0"

    /**
     * DSL keywords
     */
    object Keywords {
        const val SCENARIO = "scenario"
        const val DESCRIPTION = "description"
        const val URL = "url"
        const val TAGS = "tags"
        const val PRIORITY = "priority"
        const val STEP = "step"
        const val EXPECT = "expect"
        const val TIMEOUT = "timeout"
        const val RETRY = "retry"
        const val CONTINUE_ON_FAILURE = "continueOnFailure"

        // Actions
        const val CLICK = "click"
        const val TYPE = "type"
        const val HOVER = "hover"
        const val SCROLL = "scroll"
        const val WAIT = "wait"
        const val PRESS_KEY = "pressKey"
        const val NAVIGATE = "navigate"
        const val GO_BACK = "goBack"
        const val GO_FORWARD = "goForward"
        const val REFRESH = "refresh"
        const val ASSERT = "assert"
        const val SELECT = "select"
        const val UPLOAD_FILE = "uploadFile"
        const val SCREENSHOT = "screenshot"

        // Modifiers
        const val CLEAR_FIRST = "clearFirst"
        const val PRESS_ENTER = "pressEnter"
        const val FULL_PAGE = "fullPage"
        const val DOUBLE = "double"

        // Mouse buttons
        const val LEFT = "left"
        const val RIGHT = "right"
        const val MIDDLE = "middle"

        // Scroll directions
        const val UP = "up"
        const val DOWN = "down"

        // Key modifiers
        const val CTRL = "ctrl"
        const val ALT = "alt"
        const val SHIFT = "shift"
        const val META = "meta"

        // Wait conditions
        const val DURATION = "duration"
        const val VISIBLE = "visible"
        const val HIDDEN = "hidden"
        const val ENABLED = "enabled"
        const val DISABLED = "disabled"
        const val TEXT_PRESENT = "textPresent"
        const val URL_CONTAINS = "urlContains"
        const val PAGE_LOADED = "pageLoaded"
        const val NETWORK_IDLE = "networkIdle"

        // Assertion types
        const val CHECKED = "checked"
        const val UNCHECKED = "unchecked"
        const val TEXT_EQUALS = "textEquals"
        const val TEXT_CONTAINS = "textContains"
        const val ATTRIBUTE_EQUALS = "attributeEquals"
        const val HAS_CLASS = "hasClass"

        // Priority levels
        const val HIGH = "high"
        const val MEDIUM = "medium"
        const val LOW = "low"
        const val CRITICAL = "critical"

        // Select options
        const val VALUE = "value"
        const val LABEL = "label"
        const val INDEX = "index"
    }
}

/**
 * Result of parsing E2E DSL
 */
@Serializable
data class DslParseResult(
    val success: Boolean,
    val scenario: TestScenario? = null,
    val errors: List<DslError> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * DSL parsing error
 */
@Serializable
data class DslError(
    val line: Int,
    val column: Int,
    val message: String,
    val context: String? = null
)

