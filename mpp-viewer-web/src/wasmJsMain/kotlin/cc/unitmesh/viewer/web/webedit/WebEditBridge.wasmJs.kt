package cc.unitmesh.viewer.web.webedit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * WASM JS implementation of WebEditBridge
 */
class WasmWebEditBridge : WebEditBridge {
    private val _currentUrl = MutableStateFlow("")
    override val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()
    
    private val _pageTitle = MutableStateFlow("")
    override val pageTitle: StateFlow<String> = _pageTitle.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _loadProgress = MutableStateFlow(0)
    override val loadProgress: StateFlow<Int> = _loadProgress.asStateFlow()
    
    private val _domTree = MutableStateFlow<DOMElement?>(null)
    override val domTree: StateFlow<DOMElement?> = _domTree.asStateFlow()
    
    private val _selectedElement = MutableStateFlow<DOMElement?>(null)
    override val selectedElement: StateFlow<DOMElement?> = _selectedElement.asStateFlow()

    private val _d2SnapTree = MutableStateFlow<D2SnapElement?>(null)
    override val d2SnapTree: StateFlow<D2SnapElement?> = _d2SnapTree.asStateFlow()

    private val _accessibilityTree = MutableStateFlow<AccessibilityNode?>(null)
    override val accessibilityTree: StateFlow<AccessibilityNode?> = _accessibilityTree.asStateFlow()

    private val _actionableElements = MutableStateFlow<List<AccessibilityNode>>(emptyList())
    override val actionableElements: StateFlow<List<AccessibilityNode>> = _actionableElements.asStateFlow()

    private val _lastActionResult = MutableStateFlow<WebEditMessage.ActionResult?>(null)
    override val lastActionResult: StateFlow<WebEditMessage.ActionResult?> = _lastActionResult.asStateFlow()
    
    private val _lastScreenshot = MutableStateFlow<WebEditMessage.ScreenshotCaptured?>(null)
    override val lastScreenshot: StateFlow<WebEditMessage.ScreenshotCaptured?> = _lastScreenshot.asStateFlow()
    
    private val _isSelectionMode = MutableStateFlow(false)
    override val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()
    
    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    var executeJavaScript: ((String) -> Unit)? = null
    var navigateCallback: ((String) -> Unit)? = null
    var reloadCallback: (() -> Unit)? = null
    var goBackCallback: (() -> Unit)? = null
    var goForwardCallback: (() -> Unit)? = null

    override suspend fun navigateTo(url: String) {
        _isLoading.value = true
        _currentUrl.value = url
        _errorMessage.value = null // Clear previous errors
        try {
            navigateCallback?.invoke(url)
        } catch (e: Exception) {
            _errorMessage.value = "Failed to navigate: ${e.message}"
            _isLoading.value = false
        }
    }

    override suspend fun reload() {
        _isLoading.value = true
        reloadCallback?.invoke()
    }

    override suspend fun goBack() {
        goBackCallback?.invoke()
    }

    override suspend fun goForward() {
        goForwardCallback?.invoke()
    }

    override suspend fun setSelectionMode(enabled: Boolean) {
        _isSelectionMode.value = enabled
        val script = "window.webEditBridge?.setSelectionMode($enabled);"
        executeJavaScript?.invoke(script)
    }

    override suspend fun enableInspectMode() {
        _isSelectionMode.value = true
        val script = "window.webEditBridge?.enableInspectMode();"
        executeJavaScript?.invoke(script)
    }

    override suspend fun disableInspectMode() {
        _isSelectionMode.value = false
        val script = "window.webEditBridge?.disableInspectMode();"
        executeJavaScript?.invoke(script)
    }

    override suspend fun highlightElement(selector: String) {
        val escapedSelector = selector
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        val script = "window.webEditBridge?.highlightElement('$escapedSelector');"
        executeJavaScript?.invoke(script)
    }

    override suspend fun clearHighlights() {
        val script = "window.webEditBridge?.clearHighlights();"
        executeJavaScript?.invoke(script)
    }

    override suspend fun scrollToElement(selector: String) {
        val escapedSelector = selector
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        val script = "window.webEditBridge?.scrollToElement('$escapedSelector');"
        executeJavaScript?.invoke(script)
    }

    override suspend fun refreshDOMTree() {
        val script = "window.webEditBridge?.getDOMTree();"
        executeJavaScript?.invoke(script)
    }

    override suspend fun refreshD2SnapTree() {
        val script = "window.webEditBridge?.getD2SnapTree();"
        executeJavaScript?.invoke(script)
    }

    override suspend fun refreshAccessibilityTree() {
        val script = "window.webEditBridge?.getAccessibilityTree();"
        executeJavaScript?.invoke(script)
    }

    override suspend fun refreshActionableElements() {
        val script = "window.webEditBridge?.getActionableElements();"
        executeJavaScript?.invoke(script)
    }

    override suspend fun getElementAtPoint(x: Int, y: Int): DOMElement? {
        // TODO: Implement using JavaScript callback mechanism similar to getSelectedElementHtml
        // This requires invoking JS to call window.webEditBridge.getElementAtPoint(x, y)
        // and receiving the result via a callback or promise-based bridge.
        throw NotImplementedError("getElementAtPoint requires callback mechanism - not yet implemented")
    }

    /**
     * Get HTML content of selected element.
     * TODO: Implement for WASM platform using JavaScript interop when available.
     * Currently returns null as WASM-JS bridge mechanism is not yet implemented.
     */
    override suspend fun getSelectedElementHtml(): String? {
        return null
    }

    override suspend fun performAction(action: WebEditAction) {
        val payload = json.encodeToString(action)
        executeJavaScript?.invoke("window.webEditBridge?.performAction($payload);")
    }

    override suspend fun click(selector: String) {
        performAction(WebEditAction(action = "click", selector = selector))
    }

    override suspend fun typeText(selector: String, text: String, clearFirst: Boolean) {
        performAction(WebEditAction(action = "type", selector = selector, text = text, clearFirst = clearFirst))
    }

    override suspend fun selectOption(selector: String, value: String) {
        performAction(WebEditAction(action = "select", selector = selector, value = value))
    }

    override suspend fun pressKey(key: String, selector: String?) {
        performAction(WebEditAction(action = "pressKey", selector = selector, key = key))
    }

    override suspend fun captureScreenshot(maxWidth: Int, quality: Double) {
        val script = "window.webEditBridge?.captureScreenshot($maxWidth, $quality);"
        executeJavaScript?.invoke(script)
    }

    override fun markReady() {
        _isReady.value = true
    }

    override fun handleMessage(message: WebEditMessage) {
        when (message) {
            is WebEditMessage.DOMTreeUpdated -> {
                _domTree.value = message.root
                _errorMessage.value = null // Clear error on successful update
            }
            is WebEditMessage.D2SnapTreeUpdated -> {
                _d2SnapTree.value = message.root
            }
            is WebEditMessage.AccessibilityTreeUpdated -> {
                _accessibilityTree.value = message.root
            }
            is WebEditMessage.ActionableElementsUpdated -> {
                _actionableElements.value = message.elements
            }
            is WebEditMessage.ElementSelected -> {
                _selectedElement.value = message.element
            }
            is WebEditMessage.PageLoaded -> {
                _currentUrl.value = message.url
                _pageTitle.value = message.title
                _isLoading.value = false
                _loadProgress.value = 100
                _errorMessage.value = null // Clear error on successful page load
            }
            is WebEditMessage.Error -> {
                _errorMessage.value = message.message
                println("[WebEditBridge] Error: ${message.message}")
            }
            is WebEditMessage.LoadProgress -> {
                _loadProgress.value = message.progress
            }
            is WebEditMessage.DOMChanged -> {
                // DOM changes trigger automatic refresh via MutationObserver
            }
            is WebEditMessage.ActionResult -> {
                _lastActionResult.value = message
                if (!message.ok) _errorMessage.value = message.message ?: "Action failed: ${message.action}"
            }
            is WebEditMessage.ScreenshotCaptured -> {
                _lastScreenshot.value = message
                if (message.error != null) {
                    _errorMessage.value = "Screenshot capture failed: ${message.error}"
                }
            }
        }
    }
    
    fun setUrl(url: String) {
        _currentUrl.value = url
    }
    
    fun setTitle(title: String) {
        _pageTitle.value = title
    }
    
    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }
}

actual fun createWebEditBridge(): WebEditBridge = WasmWebEditBridge()

