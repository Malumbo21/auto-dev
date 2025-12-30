package cc.unitmesh.viewer.web.webedit

import cc.unitmesh.agent.e2etest.model.*
import cc.unitmesh.agent.e2etest.perception.PageStateExtractor
import cc.unitmesh.agent.e2etest.perception.PageStateExtractorConfig
import cc.unitmesh.agent.e2etest.model.AccessibilityNode as E2EAccessibilityNode
import cc.unitmesh.agent.e2etest.model.BoundingBox as E2EBoundingBox

/**
 * PageStateExtractor implementation that uses WebEditBridge.
 *
 * This allows E2ETestAgent to use WebEditBridge for page state extraction,
 * enabling reuse of the existing WebView integration across platforms.
 *
 * @param bridge The WebEditBridge instance to use for page state extraction
 * @param config Configuration for the extractor
 */
class WebEditBridgePageStateExtractor(
    private val bridge: WebEditBridge,
    private val config: PageStateExtractorConfig = PageStateExtractorConfig()
) : PageStateExtractor {

    private var tagIdCounter = 0
    private val tagMapping = mutableMapOf<Int, ActionableElement>()

    override val isAvailable: Boolean
        get() = bridge.isReady.value

    override suspend fun extractPageState(): PageState {
        val elements = getActionableElements()
        val accessibilityTree = extractAccessibilityTree()

        return PageState(
            url = getCurrentUrl(),
            title = getPageTitle(),
            viewport = Viewport(config.viewportWidth, config.viewportHeight),
            actionableElements = elements,
            accessibilityTree = accessibilityTree,
            domSummary = extractCleanDOM(),
            loadState = if (isPageLoaded()) PageLoadState.COMPLETE else PageLoadState.LOADING,
            capturedAt = currentTimeMillis()
        )
    }

    override suspend fun extractAccessibilityTree(): E2EAccessibilityNode? {
        val accessibilityRoot = bridge.accessibilityTree.value
        return accessibilityRoot?.toE2EAccessibilityNode(tagIdCounter)
    }

    override suspend fun extractCleanDOM(): String {
        val d2SnapTree = bridge.d2SnapTree.value
        return d2SnapTree?.toCleanDOMString() ?: ""
    }

    override suspend fun captureScreenshotWithSoM(): SoMScreenshot? {
        // Capture screenshot via bridge
        bridge.captureScreenshot(maxWidth = config.viewportWidth, quality = 0.8)

        // Wait for screenshot result
        kotlinx.coroutines.delay(500)
        val screenshot = bridge.lastScreenshot.value

        if (screenshot?.base64 == null || screenshot.error != null) {
            return null
        }

        // Build tag mapping for SoM
        val elements = getActionableElements()
        val mapping = elements.associateBy { it.tagId }

        return SoMScreenshot(
            imageBase64 = screenshot.base64!!,
            format = screenshot.mimeType?.substringAfter("/") ?: "jpeg",
            width = screenshot.width ?: config.viewportWidth,
            height = screenshot.height ?: config.viewportHeight,
            tagMapping = mapping
        )
    }

    override suspend fun getActionableElements(): List<ActionableElement> {
        tagIdCounter = 0
        tagMapping.clear()

        val accessibilityNodes = bridge.actionableElements.value
        return accessibilityNodes.mapNotNull { node ->
            val tagId = ++tagIdCounter
            val element = node.toActionableElement(tagId)
            tagMapping[tagId] = element
            element
        }
    }

    override suspend fun getElementByTagId(tagId: Int): ActionableElement? {
        return tagMapping[tagId]
    }

    override suspend fun getElementFingerprint(selector: String): ElementFingerprint? {
        // Find element by selector in actionable elements
        val element = tagMapping.values.find { it.selector == selector }
        return element?.fingerprint
    }

    override suspend fun isPageLoaded(): Boolean {
        return !bridge.isLoading.value
    }

    override suspend fun getCurrentUrl(): String {
        return bridge.currentUrl.value
    }

    override suspend fun getPageTitle(): String {
        return bridge.pageTitle.value
    }

    override fun close() {
        tagMapping.clear()
    }

    private fun currentTimeMillis(): Long {
        return kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds
    }
}

/**
 * Extension function to convert AccessibilityNode from WebEditBridge to E2E model
 */
private fun AccessibilityNode.toE2EAccessibilityNode(startTagId: Int): E2EAccessibilityNode {
    var currentTagId = startTagId
    return E2EAccessibilityNode(
        role = this.role,
        name = this.name ?: "",
        description = this.description,
        value = this.value,
        children = this.children.map {
            currentTagId++
            it.toE2EAccessibilityNode(currentTagId)
        },
        tagId = currentTagId
    )
}

/**
 * Extension function to convert AccessibilityNode to ActionableElement
 */
private fun AccessibilityNode.toActionableElement(tagId: Int): ActionableElement {
    return ActionableElement(
        tagId = tagId,
        selector = this.selector,
        tagName = this.role,
        role = this.role,
        name = this.name ?: "",
        isVisible = true,
        isEnabled = !this.state.getOrElse("disabled") { false },
        boundingBox = E2EBoundingBox(0.0, 0.0, 0.0, 0.0), // No bounding box in WebEditBridge AccessibilityNode
        fingerprint = ElementFingerprint(
            selector = this.selector,
            tagName = this.role,
            textContent = this.name,
            role = this.role
        ),
        value = this.value
    )
}

/**
 * Extension function to convert D2SnapElement to clean DOM string
 */
private fun D2SnapElement.toCleanDOMString(indent: Int = 0): String {
    val sb = StringBuilder()
    val prefix = "  ".repeat(indent)

    sb.append("$prefix<${tagName}")
    attributes.forEach { (key, value) ->
        sb.append(" $key=\"$value\"")
    }
    sb.append(">")

    text?.let { sb.append(it.take(50)) }

    if (children.isNotEmpty()) {
        sb.appendLine()
        children.forEach { child ->
            sb.append(child.toCleanDOMString(indent + 1))
        }
        sb.append("$prefix</${tagName}>")
    } else {
        sb.append("</${tagName}>")
    }
    sb.appendLine()

    return sb.toString()
}

/**
 * Extension function to create a PageStateExtractor from a WebEditBridge.
 */
fun WebEditBridge.asPageStateExtractor(
    config: PageStateExtractorConfig = PageStateExtractorConfig()
): PageStateExtractor = WebEditBridgePageStateExtractor(this, config)

