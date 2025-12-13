package cc.unitmesh.viewer.web.webedit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * JVM implementation of WebEditBridge using WebViewNavigator
 */
class JvmWebEditBridge : WebEditBridge {
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

    // Callback to execute JavaScript in WebView
    var executeJavaScript: ((String) -> Unit)? = null
    
    // Callback to navigate in WebView
    var navigateCallback: ((String) -> Unit)? = null
    var reloadCallback: (() -> Unit)? = null
    var goBackCallback: (() -> Unit)? = null
    var goForwardCallback: (() -> Unit)? = null

    override suspend fun navigateTo(url: String) {
        println("[JvmWebEditBridge] 🚀 navigateTo called: '$url'")
        _isLoading.value = true
        _currentUrl.value = url
        _errorMessage.value = null // Clear previous errors
        try {
            println("[JvmWebEditBridge] 📞 Calling navigateCallback...")
            navigateCallback?.invoke(url) ?: println("[JvmWebEditBridge] ⚠️ navigateCallback is null!")
            println("[JvmWebEditBridge] ✅ navigateCallback invoked")
        } catch (e: Exception) {
            val errorMsg = "Failed to navigate: ${e.message}"
            println("[JvmWebEditBridge] ❌ Navigation error: $errorMsg")
            e.printStackTrace()
            _errorMessage.value = errorMsg
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
        println("[JvmWebEditBridge] 🎯 setSelectionMode: $enabled")
        _isSelectionMode.value = enabled
        val script = "window.webEditBridge?.setSelectionMode($enabled);"
        println("[JvmWebEditBridge] 📜 Executing JS: $script")
        executeJavaScript?.invoke(script) ?: println("[JvmWebEditBridge] ⚠️ executeJavaScript is null!")
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

    override suspend fun getElementAtPoint(x: Int, y: Int): DOMElement? {
        // TODO: Implement using JavaScript callback mechanism similar to getSelectedElementHtml
        // This requires invoking JS to call window.webEditBridge.getElementAtPoint(x, y)
        // and receiving the result via a callback or promise-based bridge.
        throw NotImplementedError("getElementAtPoint requires callback mechanism - not yet implemented")
    }

    /**
     * Get HTML content of selected element.
     * TODO: This is currently a stub. Implement using JavaScript callback mechanism
     * to properly retrieve outerHTML from the WebView.
     */
    override suspend fun getSelectedElementHtml(): String? {
        // Stub implementation - actual retrieval requires callback mechanism
        return null
    }

    override fun markReady() {
        println("[JvmWebEditBridge] ✅ Bridge marked as READY")
        _isReady.value = true
    }

    override fun handleMessage(message: WebEditMessage) {
        println("[JvmWebEditBridge] 📨 handleMessage: ${message::class.simpleName}")
        when (message) {
            is WebEditMessage.DOMTreeUpdated -> {
                println("[JvmWebEditBridge] 🌳 DOM Tree Updated:")
                println("  - Root: ${message.root.tagName}")
                println("  - Children: ${message.root.children.size}")
                println("  - Selector: ${message.root.selector}")
                _domTree.value = message.root
                _errorMessage.value = null // Clear error on successful update
            }
            is WebEditMessage.ElementSelected -> {
                println("[JvmWebEditBridge] ✨ Element Selected: ${message.element.tagName}")
                _selectedElement.value = message.element
            }
            is WebEditMessage.PageLoaded -> {
                println("[JvmWebEditBridge] 📄 Page Loaded: ${message.title} (${message.url})")
                _currentUrl.value = message.url
                _pageTitle.value = message.title
                _isLoading.value = false
                _loadProgress.value = 100
                _errorMessage.value = null // Clear error on successful page load
            }
            is WebEditMessage.Error -> {
                println("[JvmWebEditBridge] ❌ Error: ${message.message}")
                _errorMessage.value = message.message
            }
            is WebEditMessage.LoadProgress -> {
                println("[JvmWebEditBridge] ⏳ Load Progress: ${message.progress}%")
                _loadProgress.value = message.progress
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

actual fun createWebEditBridge(): WebEditBridge = JvmWebEditBridge()

