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
    
    // Callback to execute JavaScript in WebView
    var executeJavaScript: ((String) -> Unit)? = null
    
    // Callback to navigate in WebView
    var navigateCallback: ((String) -> Unit)? = null
    var reloadCallback: (() -> Unit)? = null
    var goBackCallback: (() -> Unit)? = null
    var goForwardCallback: (() -> Unit)? = null

    override suspend fun navigateTo(url: String) {
        _isLoading.value = true
        _currentUrl.value = url
        navigateCallback?.invoke(url)
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

    override suspend fun highlightElement(selector: String) {
        val escapedSelector = selector.replace("'", "\\'")
        val script = "window.webEditBridge?.highlightElement('$escapedSelector');"
        executeJavaScript?.invoke(script)
    }

    override suspend fun clearHighlights() {
        val script = "window.webEditBridge?.clearHighlights();"
        executeJavaScript?.invoke(script)
    }

    override suspend fun scrollToElement(selector: String) {
        val escapedSelector = selector.replace("'", "\\'")
        val script = "window.webEditBridge?.scrollToElement('$escapedSelector');"
        executeJavaScript?.invoke(script)
    }

    override suspend fun refreshDOMTree() {
        val script = "window.webEditBridge?.getDOMTree();"
        executeJavaScript?.invoke(script)
    }

    override suspend fun getSelectedElementHtml(): String? {
        return _selectedElement.value?.let {
            val script = "document.querySelector('${it.selector}')?.outerHTML || '';"
            // This is a simplified version - in practice you'd use a callback
            null
        }
    }

    override fun markReady() {
        _isReady.value = true
    }

    override fun handleMessage(message: WebEditMessage) {
        when (message) {
            is WebEditMessage.DOMTreeUpdated -> {
                _domTree.value = message.root
            }
            is WebEditMessage.ElementSelected -> {
                _selectedElement.value = message.element
            }
            is WebEditMessage.PageLoaded -> {
                _currentUrl.value = message.url
                _pageTitle.value = message.title
                _isLoading.value = false
                _loadProgress.value = 100
            }
            is WebEditMessage.Error -> {
                println("[WebEditBridge] Error: ${message.message}")
            }
            is WebEditMessage.LoadProgress -> {
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

