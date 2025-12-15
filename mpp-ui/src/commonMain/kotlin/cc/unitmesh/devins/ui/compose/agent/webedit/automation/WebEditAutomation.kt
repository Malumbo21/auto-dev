package cc.unitmesh.devins.ui.compose.agent.webedit.automation

import cc.unitmesh.llm.KoogLLMService
import cc.unitmesh.viewer.web.webedit.AccessibilityNode
import cc.unitmesh.viewer.web.webedit.WebEditAction
import cc.unitmesh.viewer.web.webedit.WebEditBridge
import cc.unitmesh.viewer.web.webedit.WebEditMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class WebEditAutomationResult(
    val actions: List<WebEditAction>,
    val results: List<WebEditMessage.ActionResult>,
    val rawModelOutput: String
)

private val automationJson = Json {
    ignoreUnknownKeys = true
}

/**
 * Convert a single user instruction into browser actions (via LLM), execute them, and collect results.
 *
 * This is a "self-healing" style loop:
 * - build context (url/title/actionable elements)
 * - ask LLM for JSON actions
 * - execute actions (click/type/select/pressKey)
 * - wait for ActionResult after each step
 *
 * Visual fallback is intentionally not implemented here yet; DOM + accessibility selectors are the primary path.
 */
suspend fun runOneSentenceCommand(
    bridge: WebEditBridge,
    llmService: KoogLLMService,
    instruction: String,
    actionableElements: List<AccessibilityNode>,
    timeoutMsPerAction: Long = 6_000,
    onPlanningChunk: ((String) -> Unit)? = null
): WebEditAutomationResult {
    // Ensure we operate on the latest page snapshot.
    bridge.refreshActionableElements()
    delay(250)

    val prompt = buildAutomationPrompt(
        instruction = instruction,
        currentUrl = bridge.currentUrl.value,
        pageTitle = bridge.pageTitle.value,
        actionableElements = actionableElements.ifEmpty { bridge.actionableElements.value }
    )

    val raw = buildString {
        llmService.streamPrompt(
            userPrompt = prompt,
            compileDevIns = false
        ).collect { chunk ->
            append(chunk)
            onPlanningChunk?.invoke(chunk)
        }
    }.trim()
    val jsonText = extractJsonArray(raw)
    val parsed = automationJson.decodeFromString(ListSerializer(WebEditAction.serializer()), jsonText)

    val results = mutableListOf<WebEditMessage.ActionResult>()
    for ((idx, action) in parsed.withIndex()) {
        // Attach id for correlation (the JS bridge echoes it back in ActionResult.id)
        val actWithId = if (action.id.isNullOrBlank()) {
            action.copy(id = "step-${idx + 1}")
        } else action

        val beforeUrl = bridge.currentUrl.value
        val beforeTitle = bridge.pageTitle.value
        val beforeId = bridge.lastActionResult.value?.id
        bridge.performAction(actWithId)

        val result = withTimeout(timeoutMsPerAction) {
            bridge.lastActionResult
                .filterNotNull()
                .first { it.id == actWithId.id || (beforeId != null && it.id != beforeId) }
        }
        results += result

        // Post-condition: for Enter key, try to observe navigation; if not, attempt a self-healing fallback.
        if (actWithId.action == "pressKey" && actWithId.key == "Enter") {
            val navigated = waitForNavigation(bridge, beforeUrl, beforeTitle, timeoutMs = 3_000)
            if (!navigated) {
                // Try clicking a likely "Search" button if present.
                bridge.refreshActionableElements()
                delay(250)
                val elems = bridge.actionableElements.value
                val btn = elems.firstOrNull { n ->
                    n.role == "button" && (n.name?.contains("Google Search", ignoreCase = true) == true ||
                        n.name?.contains("Search", ignoreCase = true) == true ||
                        n.name?.contains("搜索") == true)
                }
                if (btn != null) {
                    val fallback = WebEditAction(action = "click", selector = btn.selector, id = "fallback-click-search")
                    val beforeId2 = bridge.lastActionResult.value?.id
                    bridge.performAction(fallback)
                    val fallbackResult = withTimeout(timeoutMsPerAction) {
                        bridge.lastActionResult
                            .filterNotNull()
                            .first { it.id == fallback.id || (beforeId2 != null && it.id != beforeId2) }
                    }
                    results += fallbackResult

                    val navigatedAfter = waitForNavigation(bridge, beforeUrl, beforeTitle, timeoutMs = 3_000)
                    results += WebEditMessage.ActionResult(
                        action = "observeNavigation",
                        ok = navigatedAfter,
                        message = if (navigatedAfter) "Navigation detected after fallback click" else "No navigation detected after fallback click",
                        id = fallback.id
                    )
                } else {
                    results += WebEditMessage.ActionResult(
                        action = "observeNavigation",
                        ok = false,
                        message = "No navigation detected after Enter; no suitable search button found",
                        id = actWithId.id
                    )
                }
            } else {
                results += WebEditMessage.ActionResult(
                    action = "observeNavigation",
                    ok = true,
                    message = "Navigation detected after Enter",
                    id = actWithId.id
                )
            }
        }

        // Small settle time for DOM changes/navigation
        delay(200)
    }

    return WebEditAutomationResult(
        actions = parsed,
        results = results,
        rawModelOutput = raw
    )
}

private fun buildAutomationPrompt(
    instruction: String,
    currentUrl: String,
    pageTitle: String,
    actionableElements: List<AccessibilityNode>
): String {
    val elements = actionableElements.take(40).joinToString("\n") { node ->
        val name = node.name?.replace("\n", " ")?.take(80)
        "- role=${node.role}, name=${name ?: ""}, selector=${node.selector}"
    }

    return """
You are a browser automation agent.

Current page:
- title: $pageTitle
- url: $currentUrl

Actionable elements (role/name/selector):
$elements

Task:
$instruction

Output ONLY a JSON array. No markdown. No extra keys beyond the schema.

Schema (WebEditAction):
[
  {"action":"click","selector":"CSS_SELECTOR","id":"step-1"},
  {"action":"type","selector":"CSS_SELECTOR","text":"TEXT","clearFirst":true,"id":"step-2"},
  {"action":"pressKey","key":"Enter","id":"step-3"},
  {"action":"select","selector":"CSS_SELECTOR","value":"VALUE","id":"step-4"}
]

Rules:
- Prefer selectors from the provided actionable elements list.
- If you need to type, target an input/textarea-like control.
- Use key names like Enter, Tab, Escape.
""".trim()
}

private fun extractJsonArray(text: String): String {
    // Strip code fences if present
    val cleaned = text
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

    val start = cleaned.indexOf('[')
    val end = cleaned.lastIndexOf(']')
    require(start >= 0 && end > start) { "Model output does not contain a JSON array: $text" }
    return cleaned.substring(start, end + 1)
}

private suspend fun waitForNavigation(
    bridge: WebEditBridge,
    beforeUrl: String,
    beforeTitle: String,
    timeoutMs: Long
): Boolean {
    val stepMs = 150L
    var waited = 0L
    while (waited < timeoutMs) {
        if (bridge.isLoading.value) return true
        if (bridge.currentUrl.value != beforeUrl) return true
        if (bridge.pageTitle.value.isNotBlank() && bridge.pageTitle.value != beforeTitle) return true
        delay(stepMs)
        waited += stepMs
    }
    return false
}


