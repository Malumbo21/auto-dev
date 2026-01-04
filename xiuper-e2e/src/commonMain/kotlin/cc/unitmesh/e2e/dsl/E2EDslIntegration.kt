package cc.unitmesh.e2e.dsl

import cc.unitmesh.agent.webagent.model.TestScenario
import cc.unitmesh.llm.LLMService

/**
 * Integration layer for E2E DSL with E2ETestAgent.
 *
 * This class provides methods to generate test scenarios using the DSL format
 * and integrate with the existing E2ETestAgent infrastructure.
 */
class E2EDslIntegration(
    private val llmService: LLMService
) {
    private val parser = E2EDslParser()
    private val generator = E2EDslGenerator()
    private val llmGenerator = E2EDslLLMGenerator(llmService)

    /**
     * Generate a test scenario from natural language description using DSL format.
     *
     * This method:
     * 1. Sends a prompt to LLM to generate DSL
     * 2. Parses the DSL into a TestScenario
     * 3. Returns the scenario for execution
     */
    suspend fun generateScenarioFromDescription(
        description: String,
        startUrl: String,
        context: String = ""
    ): TestScenario? {
        val result = llmGenerator.generateFromDescription(
            description = description,
            targetUrl = startUrl,
            context = context
        )

        return if (result.success) {
            result.scenario
        } else {
            null
        }
    }

    /**
     * Parse a DSL string into a TestScenario
     */
    fun parseScenario(dsl: String): TestScenario? {
        val result = parser.parse(dsl)
        return if (result.success) {
            result.scenario
        } else {
            null
        }
    }

    /**
     * Generate DSL from a TestScenario
     */
    fun generateDsl(scenario: TestScenario): String {
        return generator.generate(scenario)
    }

    /**
     * Generate multiple test scenarios for a feature
     */
    suspend fun generateTestSuite(
        featureDescription: String,
        targetUrl: String,
        numberOfTests: Int = 5
    ): List<TestScenario> {
        val results = llmGenerator.generateTestSuite(
            featureDescription = featureDescription,
            targetUrl = targetUrl,
            numberOfTests = numberOfTests
        )

        return results.filter { it.success }.mapNotNull { it.scenario }
    }

    /**
     * Build a prompt for LLM to generate DSL based on actionable elements
     */
    fun buildDslGenerationPrompt(
        testGoal: String,
        pageTitle: String,
        pageUrl: String,
        elements: List<ActionableElementInfo>
    ): String {
        return buildString {
            appendLine(DSL_SYNTAX_REFERENCE)
            appendLine()
            appendLine("## Test Goal")
            appendLine(testGoal)
            appendLine()
            appendLine("## Target URL")
            appendLine(pageUrl)
            appendLine()
            appendLine("## Current Page State")
            appendLine("Title: $pageTitle")
            appendLine("URL: $pageUrl")
            appendLine()
            appendLine("## Available Elements (Set-of-Mark)")
            elements.take(30).forEach { element ->
                appendLine("[${element.tagId}] ${element.role} \"${element.name}\" - ${element.tagName}")
            }
            appendLine()
            appendLine("Generate a test scenario in DSL format that achieves the test goal.")
            appendLine("Use the element tag IDs (e.g., #1, #2) to reference elements.")
        }
    }

    companion object {
        /**
         * Create an integration instance with the given LLM service
         */
        fun create(llmService: LLMService): E2EDslIntegration {
            return E2EDslIntegration(llmService)
        }

        /**
         * DSL syntax reference for prompts
         */
        const val DSL_SYNTAX_REFERENCE = """## E2E DSL Syntax Reference

```
scenario "Scenario Name" {
    description "What this test verifies"
    url "https://example.com/page"
    tags ["tag1", "tag2"]
    priority high|medium|low|critical

    step "Step description" {
        <action>
        expect "Expected outcome"
        timeout 5000
        retry 2
    }
}
```

## Available Actions

- click #id [left|right|middle] [double]
- type #id "text" [clearFirst] [pressEnter]
- hover #id
- scroll up|down|left|right [amount] [#id]
- wait duration|visible|hidden|enabled|textPresent|urlContains|pageLoaded|networkIdle [value] [timeout]
- pressKey "key" [ctrl] [alt] [shift] [meta]
- navigate "url"
- goBack
- goForward
- refresh
- assert #id visible|hidden|enabled|disabled|checked|unchecked|textEquals|textContains|attributeEquals|hasClass [value]
- select #id [value "v"] [label "l"] [index n]
- uploadFile #id "path"
- screenshot "name" [fullPage]"""
    }
}

/**
 * Simple data class for actionable element info
 */
data class ActionableElementInfo(
    val tagId: Int,
    val role: String,
    val name: String,
    val tagName: String
)

