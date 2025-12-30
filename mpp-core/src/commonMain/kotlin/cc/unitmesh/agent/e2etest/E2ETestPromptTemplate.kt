package cc.unitmesh.agent.e2etest

import cc.unitmesh.agent.e2etest.model.*

/**
 * Prompt templates for E2E Testing Agent.
 * 
 * @see <a href="https://github.com/phodal/auto-dev/issues/532">Issue #532</a>
 */
object E2ETestPromptTemplate {

    /**
     * System prompt for the E2E testing agent
     */
    fun systemPrompt(language: String = "EN"): String {
        return if (language == "ZH") SYSTEM_PROMPT_ZH else SYSTEM_PROMPT_EN
    }

    /**
     * Build prompt for action planning
     */
    fun actionPlanningPrompt(
        pageState: PageState,
        userIntent: String,
        recentActions: List<ActionRecord>,
        language: String = "EN"
    ): String {
        return buildString {
            appendLine(if (language == "ZH") "## Current Page State" else "## Current Page State")
            appendLine("URL: ${pageState.url}")
            appendLine("Title: ${pageState.title}")
            appendLine()
            
            appendLine(if (language == "ZH") "## Actionable Elements (Set-of-Mark)" else "## Actionable Elements (Set-of-Mark)")
            pageState.actionableElements.take(50).forEach { element ->
                appendLine("[${element.tagId}] ${element.role} \"${element.name}\" (${element.tagName})")
            }
            appendLine()
            
            appendLine(if (language == "ZH") "## User Intent" else "## User Intent")
            appendLine(userIntent)
            appendLine()
            
            if (recentActions.isNotEmpty()) {
                appendLine(if (language == "ZH") "## Recent Actions" else "## Recent Actions")
                recentActions.takeLast(5).forEach { action ->
                    val status = if (action.success) "OK" else "FAILED"
                    appendLine("- [$status] ${action.description}")
                }
                appendLine()
            }
            
            appendLine(ACTION_OUTPUT_FORMAT)
        }
    }

    /**
     * Build prompt for scenario generation
     */
    fun scenarioGenerationPrompt(
        description: String,
        startUrl: String,
        pageState: PageState,
        language: String = "EN"
    ): String {
        return buildString {
            appendLine(if (language == "ZH") "## Test Description" else "## Test Description")
            appendLine(description)
            appendLine()
            
            appendLine(if (language == "ZH") "## Starting URL" else "## Starting URL")
            appendLine(startUrl)
            appendLine()
            
            appendLine(if (language == "ZH") "## Current Page" else "## Current Page")
            appendLine("Title: ${pageState.title}")
            appendLine()
            
            appendLine(if (language == "ZH") "## Available Elements" else "## Available Elements")
            pageState.actionableElements.take(30).forEach { element ->
                appendLine("[${element.tagId}] ${element.role} \"${element.name}\"")
            }
            appendLine()
            
            appendLine(SCENARIO_OUTPUT_FORMAT)
        }
    }

    /**
     * Build prompt for self-healing with LLM
     */
    fun selfHealingPrompt(
        failedSelector: String,
        fingerprint: ElementFingerprint,
        pageContext: String,
        language: String = "EN"
    ): String {
        return buildString {
            appendLine(if (language == "ZH") HEALING_INTRO_ZH else HEALING_INTRO_EN)
            appendLine()
            
            appendLine(if (language == "ZH") "## Failed Selector" else "## Failed Selector")
            appendLine(failedSelector)
            appendLine()
            
            appendLine(if (language == "ZH") "## Original Element Properties" else "## Original Element Properties")
            appendLine("- Tag: ${fingerprint.tagName}")
            fingerprint.id?.let { appendLine("- ID: $it") }
            fingerprint.textContent?.let { appendLine("- Text: $it") }
            fingerprint.ariaLabel?.let { appendLine("- ARIA Label: $it") }
            fingerprint.role?.let { appendLine("- Role: $it") }
            fingerprint.testId?.let { appendLine("- data-testid: $it") }
            if (fingerprint.classNames.isNotEmpty()) {
                appendLine("- Classes: ${fingerprint.classNames.joinToString(" ")}")
            }
            appendLine()
            
            appendLine(if (language == "ZH") "## Current Page Context" else "## Current Page Context")
            appendLine(pageContext)
            appendLine()
            
            appendLine(HEALING_OUTPUT_FORMAT)
        }
    }

    /**
     * Build prompt for assertion verification
     */
    fun assertionPrompt(
        assertion: AssertionType,
        targetElement: ActionableElement,
        pageState: PageState,
        language: String = "EN"
    ): String {
        return buildString {
            appendLine(if (language == "ZH") "## Assertion to Verify" else "## Assertion to Verify")
            appendLine("Type: ${assertion::class.simpleName}")
            appendLine()
            
            appendLine(if (language == "ZH") "## Target Element" else "## Target Element")
            appendLine("Tag ID: ${targetElement.tagId}")
            appendLine("Role: ${targetElement.role}")
            appendLine("Name: ${targetElement.name}")
            appendLine("Visible: ${targetElement.isVisible}")
            appendLine("Enabled: ${targetElement.isEnabled}")
            appendLine()
            
            appendLine(if (language == "ZH") "## Page Context" else "## Page Context")
            appendLine("URL: ${pageState.url}")
            appendLine("Title: ${pageState.title}")
            appendLine()
            
            appendLine(ASSERTION_OUTPUT_FORMAT)
        }
    }

    private const val SYSTEM_PROMPT_EN = """You are an AI-powered E2E testing agent specialized in web UI automation.

Your capabilities:
1. Analyze web page structure using DOM and accessibility tree
2. Execute browser actions: click, type, scroll, wait, navigate, assert
3. Self-heal broken element selectors using visual and semantic understanding
4. Generate test scenarios from natural language descriptions

Guidelines:
- Always reference elements by their Set-of-Mark tag ID [number]
- Prefer semantic selectors (role, aria-label, text) over structural ones
- Consider element visibility and interactivity before actions
- Report clear error messages when actions fail

Output Format:
- Always output valid JSON for structured responses
- Include reasoning for action decisions
- Provide confidence scores when applicable"""

    private const val SYSTEM_PROMPT_ZH = """You are an AI-powered E2E testing agent specialized in web UI automation.

Your capabilities:
1. Analyze web page structure using DOM and accessibility tree
2. Execute browser actions: click, type, scroll, wait, navigate, assert
3. Self-heal broken element selectors using visual and semantic understanding
4. Generate test scenarios from natural language descriptions

Guidelines:
- Always reference elements by their Set-of-Mark tag ID [number]
- Prefer semantic selectors (role, aria-label, text) over structural ones
- Consider element visibility and interactivity before actions
- Report clear error messages when actions fail

Output Format:
- Always output valid JSON for structured responses
- Include reasoning for action decisions
- Provide confidence scores when applicable"""

    private const val ACTION_OUTPUT_FORMAT = """## Output Format
Output a JSON object:
{
  "action_type": "click" | "type" | "scroll" | "wait" | "press_key" | "navigate" | "assert",
  "target_id": <Set-of-Mark tag number>,
  "value": "<text to type or key to press>",
  "reasoning": "<brief explanation>"
}"""

    private const val SCENARIO_OUTPUT_FORMAT = """## Output Format
Output a JSON object:
{
  "name": "<test scenario name>",
  "description": "<what this test verifies>",
  "steps": [
    {
      "description": "<what this step does>",
      "action": {"action_type": "...", "target_id": ..., "value": "..."},
      "expected_outcome": "<what should happen>"
    }
  ]
}"""

    private const val HEALING_INTRO_EN = "You are helping to fix a broken element selector in a web automation test."
    private const val HEALING_INTRO_ZH = "You are helping to fix a broken element selector in a web automation test."

    private const val HEALING_OUTPUT_FORMAT = """## Task
Find the element that best matches the original element properties.
Output ONLY a valid CSS selector, nothing else."""

    private const val ASSERTION_OUTPUT_FORMAT = """## Task
Verify if the assertion passes based on the element state.
Output JSON: {"passed": true/false, "actual_value": "...", "reason": "..."}"""
}
