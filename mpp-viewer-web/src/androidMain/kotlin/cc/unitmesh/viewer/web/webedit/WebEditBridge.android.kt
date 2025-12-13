package cc.unitmesh.viewer.web.webedit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android implementation of WebEditBridge using WebViewNavigator
 */
class AndroidWebEditBridge : WebEditBridge {
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
    
    private val _isSelectionMode = MutableStateFlow(false)
    override val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()
    
    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    var executeJavaScript: ((String) -> Unit)? = null
    var navigateCallback: ((String) -> Unit)? = null
    var reloadCallback: (() -> Unit)? = null
    var goBackCallback: (() -> Unit)? = null
    var goForwardCallback: (() -> Unit)? = null

    override suspend fun navigateTo(url: String) {
        println("[AndroidWebEditBridge] 🚀 navigateTo called: '$url'")
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

    override fun markReady() {
        _isReady.value = true
    }

    override fun handleMessage(message: WebEditMessage) {
        when (message) {
            is WebEditMessage.DOMTreeUpdated -> _domTree.value = message.root
            is WebEditMessage.ElementSelected -> _selectedElement.value = message.element
            is WebEditMessage.PageLoaded -> {
                _currentUrl.value = message.url
                _pageTitle.value = message.title
                _isLoading.value = false
                _loadProgress.value = 100
            }
            is WebEditMessage.Error -> _errorMessage.value = message.message
            is WebEditMessage.LoadProgress -> _loadProgress.value = message.progress
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
    return AndroidWebEditBridge()
}
