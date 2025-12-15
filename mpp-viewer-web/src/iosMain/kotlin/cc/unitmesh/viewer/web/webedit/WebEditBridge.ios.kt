package cc.unitmesh.viewer.web.webedit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * iOS implementation of WebEditBridge using WebViewNavigator
 */
class IosWebEditBridge : WebEditBridge {
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
        println("[IosWebEditBridge] 🚀 navigateTo called: '$url'")
        _isLoading.value = true
        _currentUrl.value = url
        _errorMessage.value = null
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
        executeJavaScript?.invoke("window.webEditBridge?.setSelectionMode($enabled);")
    }

    override suspend fun enableInspectMode() {
        _isSelectionMode.value = true
        executeJavaScript?.invoke("window.webEditBridge?.enableInspectMode();")
    }

    override suspend fun disableInspectMode() {
        _isSelectionMode.value = false
        executeJavaScript?.invoke("window.webEditBridge?.disableInspectMode();")
    }

    override suspend fun highlightElement(selector: String) {
        val escapedSelector = selector
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
        executeJavaScript?.invoke("window.webEditBridge?.highlightElement('$escapedSelector');")
    }

    override suspend fun clearHighlights() {
        executeJavaScript?.invoke("window.webEditBridge?.clearHighlights();")
    }

    override suspend fun scrollToElement(selector: String) {
        val escapedSelector = selector
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
        executeJavaScript?.invoke("window.webEditBridge?.scrollToElement('$escapedSelector');")
    }

    override suspend fun refreshDOMTree() {
        executeJavaScript?.invoke("window.webEditBridge?.getDOMTree();")
    }

    override suspend fun refreshD2SnapTree() {
        executeJavaScript?.invoke("window.webEditBridge?.getD2SnapTree();")
    }

    override suspend fun refreshAccessibilityTree() {
        executeJavaScript?.invoke("window.webEditBridge?.getAccessibilityTree();")
    }

    override suspend fun refreshActionableElements() {
        executeJavaScript?.invoke("window.webEditBridge?.getActionableElements();")
    }

    override suspend fun getElementAtPoint(x: Int, y: Int): DOMElement? {
        throw NotImplementedError("getElementAtPoint requires callback mechanism")
    }

    override suspend fun getSelectedElementHtml(): String? {
        return null
    }

    override suspend fun click(selector: String) {
        val payload = json.encodeToString(WebEditAction(action = "click", selector = selector))
        executeJavaScript?.invoke("window.webEditBridge?.performAction($payload);")
    }

    override suspend fun typeText(selector: String, text: String, clearFirst: Boolean) {
        val payload = json.encodeToString(WebEditAction(action = "type", selector = selector, text = text, clearFirst = clearFirst))
        executeJavaScript?.invoke("window.webEditBridge?.performAction($payload);")
    }

    override suspend fun selectOption(selector: String, value: String) {
        val payload = json.encodeToString(WebEditAction(action = "select", selector = selector, value = value))
        executeJavaScript?.invoke("window.webEditBridge?.performAction($payload);")
    }

    override suspend fun pressKey(key: String, selector: String?) {
        val payload = json.encodeToString(WebEditAction(action = "pressKey", selector = selector, key = key))
        executeJavaScript?.invoke("window.webEditBridge?.performAction($payload);")
    }

    override fun markReady() {
        _isReady.value = true
    }

    override fun handleMessage(message: WebEditMessage) {
        when (message) {
            is WebEditMessage.DOMTreeUpdated -> _domTree.value = message.root
            is WebEditMessage.D2SnapTreeUpdated -> _d2SnapTree.value = message.root
            is WebEditMessage.AccessibilityTreeUpdated -> _accessibilityTree.value = message.root
            is WebEditMessage.ActionableElementsUpdated -> _actionableElements.value = message.elements
            is WebEditMessage.ElementSelected -> _selectedElement.value = message.element
            is WebEditMessage.PageLoaded -> {
                _currentUrl.value = message.url
                _pageTitle.value = message.title
                _isLoading.value = false
                _loadProgress.value = 100
            }
            is WebEditMessage.Error -> _errorMessage.value = message.message
            is WebEditMessage.LoadProgress -> _loadProgress.value = message.progress
            is WebEditMessage.ActionResult -> {
                _lastActionResult.value = message
                if (!message.ok) _errorMessage.value = message.message ?: "Action failed: ${message.action}"
            }
            else -> {}
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

actual fun createWebEditBridge(): WebEditBridge {
    return IosWebEditBridge()
}
