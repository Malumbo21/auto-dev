package cc.unitmesh.viewer.web.webedit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * JS implementation of WebEditBridge (stub for now, as WebView is not supported in JS CLI)
 */
class JsWebEditBridge : WebEditBridge {
    private val _currentUrl = MutableStateFlow("")
    override val currentUrl: StateFlow<String> = _currentUrl

    private val _pageTitle = MutableStateFlow("")
    override val pageTitle: StateFlow<String> = _pageTitle

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading

    private val _loadProgress = MutableStateFlow(0)
    override val loadProgress: StateFlow<Int> = _loadProgress

    private val _isSelectionMode = MutableStateFlow(false)
    override val isSelectionMode: StateFlow<Boolean> = _isSelectionMode

    private val _selectedElement = MutableStateFlow<DOMElement?>(null)
    override val selectedElement: StateFlow<DOMElement?> = _selectedElement

    private val _domTree = MutableStateFlow<DOMElement?>(null)
    override val domTree: StateFlow<DOMElement?> = _domTree

    private val _d2SnapTree = MutableStateFlow<D2SnapElement?>(null)
    override val d2SnapTree: StateFlow<D2SnapElement?> = _d2SnapTree

    private val _accessibilityTree = MutableStateFlow<AccessibilityNode?>(null)
    override val accessibilityTree: StateFlow<AccessibilityNode?> = _accessibilityTree

    private val _actionableElements = MutableStateFlow<List<AccessibilityNode>>(emptyList())
    override val actionableElements: StateFlow<List<AccessibilityNode>> = _actionableElements

    private val _lastActionResult = MutableStateFlow<WebEditMessage.ActionResult?>(null)
    override val lastActionResult: StateFlow<WebEditMessage.ActionResult?> = _lastActionResult

    private val _lastScreenshot = MutableStateFlow<WebEditMessage.ScreenshotCaptured?>(null)
    override val lastScreenshot: StateFlow<WebEditMessage.ScreenshotCaptured?> = _lastScreenshot

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady

    override suspend fun navigateTo(url: String) {
        console.log("JsWebEditBridge: navigateTo not supported in JS CLI")
    }

    override suspend fun goBack() {
        console.log("JsWebEditBridge: goBack not supported in JS CLI")
    }

    override suspend fun goForward() {
        console.log("JsWebEditBridge: goForward not supported in JS CLI")
    }

    override suspend fun reload() {
        console.log("JsWebEditBridge: reload not supported in JS CLI")
    }

    override suspend fun setSelectionMode(enabled: Boolean) {
        console.log("JsWebEditBridge: setSelectionMode not supported in JS CLI")
    }

    override suspend fun enableInspectMode() {
        console.log("JsWebEditBridge: enableInspectMode not supported in JS CLI")
    }

    override suspend fun disableInspectMode() {
        console.log("JsWebEditBridge: disableInspectMode not supported in JS CLI")
    }

    override suspend fun highlightElement(selector: String) {
        console.log("JsWebEditBridge: highlightElement not supported in JS CLI")
    }

    override suspend fun scrollToElement(selector: String) {
        console.log("JsWebEditBridge: scrollToElement not supported in JS CLI")
    }

    override suspend fun clearHighlights() {
        console.log("JsWebEditBridge: clearHighlights not supported in JS CLI")
    }

    override suspend fun refreshDOMTree() {
        console.log("JsWebEditBridge: refreshDOMTree not supported in JS CLI")
    }

    override suspend fun refreshD2SnapTree() {
        console.log("JsWebEditBridge: refreshD2SnapTree not supported in JS CLI")
    }

    override suspend fun refreshAccessibilityTree() {
        console.log("JsWebEditBridge: refreshAccessibilityTree not supported in JS CLI")
    }

    override suspend fun refreshActionableElements() {
        console.log("JsWebEditBridge: refreshActionableElements not supported in JS CLI")
    }

    override suspend fun getElementAtPoint(x: Int, y: Int): DOMElement? {
        console.log("JsWebEditBridge: getElementAtPoint not supported in JS CLI")
        return null
    }

    override suspend fun getSelectedElementHtml(): String? {
        console.log("JsWebEditBridge: getSelectedElementHtml not supported in JS CLI")
        return null
    }

    override suspend fun performAction(action: WebEditAction) {
        console.log("JsWebEditBridge: performAction not supported in JS CLI")
    }

    override suspend fun click(selector: String) {
        console.log("JsWebEditBridge: click not supported in JS CLI")
    }

    override suspend fun typeText(selector: String, text: String, clearFirst: Boolean) {
        console.log("JsWebEditBridge: typeText not supported in JS CLI")
    }

    override suspend fun selectOption(selector: String, value: String) {
        console.log("JsWebEditBridge: selectOption not supported in JS CLI")
    }

    override suspend fun pressKey(key: String, selector: String?) {
        console.log("JsWebEditBridge: pressKey not supported in JS CLI")
    }

    override suspend fun captureScreenshot(maxWidth: Int, quality: Double) {
        console.log("JsWebEditBridge: captureScreenshot not supported in JS CLI")
    }

    override fun markReady() {
        console.log("JsWebEditBridge: markReady")
        _isReady.value = true
    }

    override fun handleMessage(message: WebEditMessage) {
        console.log("JsWebEditBridge: handleMessage not supported in JS CLI")
    }
}

actual fun createWebEditBridge(): WebEditBridge = JsWebEditBridge()
