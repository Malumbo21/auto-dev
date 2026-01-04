package cc.unitmesh.e2e.dsl

import cc.unitmesh.llm.LLMService

/**
 * LLM-powered E2E DSL generator.
 *
 * Uses LLM to generate E2E test DSL from natural language descriptions.
 */
class E2EDslLLMGenerator(
    private val llmService: LLMService
) {
    private val parser = E2EDslParser()

    /**
     * Generate E2E DSL from natural language description
     */
    suspend fun generateFromDescription(
        description: String,
        targetUrl: String,
        context: String = ""
    ): DslGenerationResult {
        val prompt = buildPrompt(description, targetUrl, context)

        return try {
            val response = llmService.sendPrompt(prompt)
            val dsl = extractDsl(response)

            if (dsl.isNotEmpty()) {
                val parseResult = parser.parse(dsl)
                DslGenerationResult(
                    success = parseResult.success,
                    dsl = dsl,
                    scenario = parseResult.scenario,
                    errors = parseResult.errors.map { it.message },
                    rawResponse = response
                )
            } else {
                DslGenerationResult(
                    success = false,
                    dsl = "",
                    errors = listOf("Failed to extract DSL from LLM response"),
                    rawResponse = response
                )
            }
        } catch (e: Exception) {
            DslGenerationResult(
                success = false,
                dsl = "",
                errors = listOf("LLM error: ${e.message}")
            )
        }
    }

    /**
     * Generate multiple test scenarios for a given feature
     */
    suspend fun generateTestSuite(
        featureDescription: String,
        targetUrl: String,
        numberOfTests: Int = 5
    ): List<DslGenerationResult> {
        val prompt = buildTestSuitePrompt(featureDescription, targetUrl, numberOfTests)

        return try {
            val response = llmService.sendPrompt(prompt)
            val dslBlocks = extractMultipleDsl(response)

            dslBlocks.map { dsl ->
                val parseResult = parser.parse(dsl)
                DslGenerationResult(
                    success = parseResult.success,
                    dsl = dsl,
                    scenario = parseResult.scenario,
                    errors = parseResult.errors.map { it.message },
                    rawResponse = response
                )
            }
        } catch (e: Exception) {
            listOf(DslGenerationResult(
                success = false,
                dsl = "",
                errors = listOf("LLM error: ${e.message}")
            ))
        }
    }

    private fun buildPrompt(description: String, targetUrl: String, context: String): String {
        return """You are an E2E test DSL generator. Generate a test scenario in the E2E DSL format.

## E2E DSL Syntax Reference

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

Use CSS selectors to identify elements. Common selector patterns:
- #elementId - by ID
- .className - by class
- [name="fieldName"] - by name attribute
- [data-testid="testId"] - by test ID (preferred)
- button[type="submit"] - by tag and attribute

Actions:
- click "selector" [left|right|middle] [double]
- type "selector" "text" [clearFirst] [pressEnter]
- hover "selector"
- scroll up|down|left|right [amount] ["selector"]
- wait duration|visible|hidden|enabled|textPresent|urlContains|pageLoaded|networkIdle [value] [timeout]
- pressKey "key" [ctrl] [alt] [shift] [meta]
- navigate "url"
- goBack
- goForward
- refresh
- assert "selector" visible|hidden|enabled|disabled|checked|unchecked|textEquals|textContains|attributeEquals|hasClass [value]
- select "selector" [value "v"] [label "l"] [index n]
- uploadFile "selector" "path"
- screenshot "name" [fullPage]

## Task

Generate an E2E test scenario for:
- Description: $description
- Target URL: $targetUrl
${if (context.isNotEmpty()) "- Additional Context: $context" else ""}

Output ONLY the DSL code, no explanations."""
    }

    private fun buildTestSuitePrompt(featureDescription: String, targetUrl: String, numberOfTests: Int): String {
        return """You are an E2E test DSL generator. Generate $numberOfTests different test scenarios for the given feature.

## E2E DSL Syntax Reference

```
scenario "Scenario Name" {
    description "What this test verifies"
    url "https://example.com/page"
    tags ["tag1", "tag2"]
    priority high|medium|low|critical

    step "Step description" {
        <action>
        expect "Expected outcome"
    }
}
```

## Available Actions

Use CSS selectors to identify elements. Common selector patterns:
- #elementId - by ID
- .className - by class
- [name="fieldName"] - by name attribute
- [data-testid="testId"] - by test ID (preferred)

Actions:
- click "selector" [left|right|middle] [double]
- type "selector" "text" [clearFirst] [pressEnter]
- hover "selector"
- scroll up|down|left|right [amount]
- wait duration|visible|hidden|textPresent|urlContains|pageLoaded|networkIdle [value]
- pressKey "key" [ctrl] [alt] [shift] [meta]
- navigate "url"
- goBack / goForward / refresh
- assert "selector" visible|hidden|enabled|disabled|textEquals|textContains [value]
- select "selector" [value "v"] [label "l"] [index n]
- uploadFile "selector" "path"
- screenshot "name" [fullPage]

## Task

Generate $numberOfTests different E2E test scenarios for:
- Feature: $featureDescription
- Target URL: $targetUrl

Include various test cases:
1. Happy path / success scenario
2. Error handling / validation
3. Edge cases
4. User flow variations

Output ONLY the DSL code for all scenarios, separated by blank lines. No explanations."""
    }

    private fun extractDsl(response: String): String {
        val trimmed = response.trim()

        // Try to find DSL in code blocks
        val codeBlockPattern = Regex("```(?:dsl|e2e)?\\s*([\\s\\S]*?)```")
        val codeBlockMatch = codeBlockPattern.find(trimmed)
        if (codeBlockMatch != null) {
            return codeBlockMatch.groupValues[1].trim()
        }

        // Try to find scenario block directly
        val scenarioPattern = Regex("(scenario\\s+\"[^\"]+\"\\s*\\{[\\s\\S]*?\\}\\s*\\})")
        val scenarioMatch = scenarioPattern.find(trimmed)
        if (scenarioMatch != null) {
            return scenarioMatch.groupValues[1].trim()
        }

        // If starts with scenario, return as-is
        if (trimmed.startsWith("scenario")) {
            return trimmed
        }

        return ""
    }

    private fun extractMultipleDsl(response: String): List<String> {
        val trimmed = response.trim()
        val results = mutableListOf<String>()

        // Find all scenario blocks
        val scenarioPattern = Regex("scenario\\s+\"[^\"]+\"\\s*\\{[\\s\\S]*?\\}\\s*\\}")
        val matches = scenarioPattern.findAll(trimmed)

        matches.forEach { match ->
            results.add(match.value.trim())
        }

        // If no matches found, try to extract from code blocks
        if (results.isEmpty()) {
            val codeBlockPattern = Regex("```(?:dsl|e2e)?\\s*([\\s\\S]*?)```")
            val codeBlockMatches = codeBlockPattern.findAll(trimmed)

            codeBlockMatches.forEach { match ->
                val content = match.groupValues[1].trim()
                val innerMatches = scenarioPattern.findAll(content)
                innerMatches.forEach { innerMatch ->
                    results.add(innerMatch.value.trim())
                }
            }
        }

        return results
    }
}

/**
 * Result of DSL generation
 */
data class DslGenerationResult(
    val success: Boolean,
    val dsl: String,
    val scenario: cc.unitmesh.agent.webagent.model.TestScenario? = null,
    val errors: List<String> = emptyList(),
    val rawResponse: String? = null
)

