package cc.unitmesh.e2e.dsl

import cc.unitmesh.agent.e2etest.model.*

/**
 * Generator for E2E Test DSL.
 *
 * Converts TestScenario objects into human-readable DSL text.
 */
class E2EDslGenerator {

    /**
     * Generate DSL text from a TestScenario
     */
    fun generate(scenario: TestScenario): String {
        return buildString {
            appendLine("scenario \"${escapeString(scenario.name)}\" {")
            appendLine("    description \"${escapeString(scenario.description)}\"")
            appendLine("    url \"${escapeString(scenario.startUrl)}\"")

            if (scenario.tags.isNotEmpty()) {
                val tagsStr = scenario.tags.joinToString(", ") { "\"$it\"" }
                appendLine("    tags [$tagsStr]")
            }

            appendLine("    priority ${scenario.priority.name.lowercase()}")
            appendLine()

            scenario.steps.forEach { step ->
                appendLine(generateStep(step))
            }

            appendLine("}")
        }
    }

    private fun generateStep(step: TestStep): String {
        return buildString {
            appendLine("    step \"${escapeString(step.description)}\" {")
            appendLine("        ${generateAction(step.action)}")

            step.expectedOutcome?.let {
                appendLine("        expect \"${escapeString(it)}\"")
            }

            if (step.timeoutMs != null) {
                appendLine("        timeout ${step.timeoutMs}")
            }

            if (step.retryCount > 0) {
                appendLine("        retry ${step.retryCount}")
            }

            if (step.continueOnFailure) {
                appendLine("        continueOnFailure")
            }

            append("    }")
        }
    }

    private fun generateAction(action: TestAction): String {
        return when (action) {
            is TestAction.Click -> generateClickAction(action)
            is TestAction.Type -> generateTypeAction(action)
            is TestAction.Hover -> generateHoverAction(action)
            is TestAction.Scroll -> generateScrollAction(action)
            is TestAction.Wait -> generateWaitAction(action)
            is TestAction.PressKey -> generatePressKeyAction(action)
            is TestAction.Navigate -> "navigate \"${escapeString(action.url)}\""
            is TestAction.GoBack -> "goBack"
            is TestAction.GoForward -> "goForward"
            is TestAction.Refresh -> "refresh"
            is TestAction.Assert -> generateAssertAction(action)
            is TestAction.Select -> generateSelectAction(action)
            is TestAction.UploadFile -> generateUploadFileAction(action)
            is TestAction.Screenshot -> generateScreenshotAction(action)
        }
    }

    /**
     * Generate target reference - prefers selector over targetId
     */
    private fun generateTarget(targetId: Int, selector: String?): String {
        return if (selector != null) {
            "\"${escapeString(selector)}\""
        } else {
            "#$targetId"
        }
    }

    private fun generateClickAction(action: TestAction.Click): String {
        val parts = mutableListOf("click", generateTarget(action.targetId, action.selector))

        if (action.button != MouseButton.LEFT) {
            parts.add(action.button.name.lowercase())
        }

        if (action.clickCount == 2) {
            parts.add("double")
        }

        return parts.joinToString(" ")
    }

    private fun generateTypeAction(action: TestAction.Type): String {
        val parts = mutableListOf("type", generateTarget(action.targetId, action.selector), "\"${escapeString(action.text)}\"")

        if (action.clearFirst) {
            parts.add("clearFirst")
        }

        if (action.pressEnter) {
            parts.add("pressEnter")
        }

        return parts.joinToString(" ")
    }

    private fun generateHoverAction(action: TestAction.Hover): String {
        return "hover ${generateTarget(action.targetId, action.selector)}"
    }

    private fun generateScrollAction(action: TestAction.Scroll): String {
        val parts = mutableListOf("scroll", action.direction.name.lowercase())

        if (action.amount != 300) {
            parts.add(action.amount.toString())
        }

        action.selector?.let { sel ->
            parts.add("\"${escapeString(sel)}\"")
        } ?: action.targetId?.let { id ->
            parts.add("#$id")
        }

        return parts.joinToString(" ")
    }

    private fun generateWaitAction(action: TestAction.Wait): String {
        val conditionStr = when (val condition = action.condition) {
            is WaitCondition.Duration -> "duration ${condition.ms}"
            is WaitCondition.ElementVisible -> "visible ${generateTarget(condition.targetId, condition.selector)}"
            is WaitCondition.ElementHidden -> "hidden ${generateTarget(condition.targetId, condition.selector)}"
            is WaitCondition.ElementEnabled -> "enabled ${generateTarget(condition.targetId, condition.selector)}"
            is WaitCondition.TextPresent -> "textPresent \"${escapeString(condition.text)}\""
            is WaitCondition.UrlContains -> "urlContains \"${escapeString(condition.substring)}\""
            is WaitCondition.PageLoaded -> "pageLoaded"
            is WaitCondition.NetworkIdle -> "networkIdle"
        }

        return if (action.timeoutMs != 5000L) {
            "wait $conditionStr timeout ${action.timeoutMs}"
        } else {
            "wait $conditionStr"
        }
    }

    private fun generatePressKeyAction(action: TestAction.PressKey): String {
        val parts = mutableListOf("pressKey", "\"${escapeString(action.key)}\"")

        action.modifiers.forEach { modifier ->
            parts.add(modifier.name.lowercase())
        }

        return parts.joinToString(" ")
    }

    private fun generateAssertAction(action: TestAction.Assert): String {
        val assertionStr = when (val assertion = action.assertion) {
            is AssertionType.Visible -> "visible"
            is AssertionType.Hidden -> "hidden"
            is AssertionType.Enabled -> "enabled"
            is AssertionType.Disabled -> "disabled"
            is AssertionType.Checked -> "checked"
            is AssertionType.Unchecked -> "unchecked"
            is AssertionType.TextEquals -> "textEquals \"${escapeString(assertion.text)}\""
            is AssertionType.TextContains -> "textContains \"${escapeString(assertion.text)}\""
            is AssertionType.AttributeEquals -> "attributeEquals \"${escapeString(assertion.attribute)}\" \"${escapeString(assertion.value)}\""
            is AssertionType.HasClass -> "hasClass \"${escapeString(assertion.className)}\""
        }

        return "assert ${generateTarget(action.targetId, action.selector)} $assertionStr"
    }

    private fun generateSelectAction(action: TestAction.Select): String {
        val parts = mutableListOf("select", generateTarget(action.targetId, action.selector))

        action.value?.let {
            parts.add("value")
            parts.add("\"${escapeString(it)}\"")
        }

        action.label?.let {
            parts.add("label")
            parts.add("\"${escapeString(it)}\"")
        }

        action.index?.let {
            parts.add("index")
            parts.add(it.toString())
        }

        return parts.joinToString(" ")
    }

    private fun generateUploadFileAction(action: TestAction.UploadFile): String {
        return "uploadFile ${generateTarget(action.targetId, action.selector)} \"${escapeString(action.filePath)}\""
    }

    private fun generateScreenshotAction(action: TestAction.Screenshot): String {
        val parts = mutableListOf("screenshot", "\"${escapeString(action.name)}\"")

        if (action.fullPage) {
            parts.add("fullPage")
        }

        return parts.joinToString(" ")
    }

    private fun escapeString(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
    }
}

