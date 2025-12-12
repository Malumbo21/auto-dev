package cc.unitmesh.devins.idea.toolwindow.webedit

import cc.unitmesh.agent.config.McpToolConfigService
import cc.unitmesh.agent.config.ToolConfigFile
import cc.unitmesh.devins.idea.renderer.JewelRenderer
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.llm.KoogLLMService
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.*
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import javax.swing.JComponent

/**
 * ViewModel for WebEdit Agent in IntelliJ IDEA plugin.
 * Uses JBCefBrowser for WebView rendering with DOM selection capabilities.
 */
class IdeaWebEditViewModel(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) : Disposable {

    // Renderer for agent output (chat)
    val renderer = JewelRenderer()

    // State
    private val _state = MutableStateFlow(IdeaWebEditState())
    val state: StateFlow<IdeaWebEditState> = _state.asStateFlow()

    // JCEF Browser
    private var browser: JBCefBrowser? = null
    private var jsQuery: JBCefJSQuery? = null
    private var scriptInjected = false

    // LLM Service for chat
    private var llmService: KoogLLMService? = null
    private var currentJob: Job? = null

    // Check if JCEF is supported
    val isJcefSupported: Boolean = JBCefApp.isSupported()

    init {
        if (isJcefSupported) {
            initializeBrowser()
        }
        initializeLLMService()
    }

    private fun initializeBrowser() {
        try {
            browser = JBCefBrowser.createBuilder()
                .setEnableOpenDevToolsMenuItem(true)
                .build()

            // Register this as parent disposable
            browser?.let { Disposer.register(this, it) }

            // Create JS query for communication
            jsQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
            jsQuery?.let { query ->
                Disposer.register(this, query)
                query.addHandler { message ->
                    handleJsMessage(message)
                    JBCefJSQuery.Response(null)
                }
            }

            // Add load handler
            val cefBrowser = browser?.cefBrowser
            if (cefBrowser != null) {
                browser?.jbCefClient?.addLoadHandler(object : CefLoadHandlerAdapter() {
                    override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                        if (frame?.isMain == true) {
                            val url = browser?.url ?: ""
                            // Title will be updated via JS bridge callback, not from frame.name
                            updateState {
                                it.copy(
                                    currentUrl = url,
                                    isLoading = false,
                                    loadProgress = 100
                                )
                            }
                            // Inject bridge script after page load
                            injectBridgeScript()
                        }
                    }

                    override fun onLoadStart(
                        browser: CefBrowser?,
                        frame: CefFrame?,
                        transitionType: org.cef.network.CefRequest.TransitionType?
                    ) {
                        if (frame?.isMain == true) {
                            scriptInjected = false
                            updateState { it.copy(isLoading = true, loadProgress = 0) }
                        }
                    }
                }, cefBrowser)
            }

        } catch (e: Exception) {
            updateState { it.copy(error = "Failed to initialize browser: ${e.message}") }
        }
    }

    private fun initializeLLMService() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val configWrapper = ConfigManager.load()
                val activeConfig = configWrapper.getActiveModelConfig()
                if (activeConfig != null && activeConfig.isValid()) {
                    llmService = KoogLLMService.create(activeConfig)
                }
            } catch (e: Exception) {
                // LLM service is optional for WebEdit
            }
        }
    }

    /**
     * Get the browser component for embedding in Compose
     */
    fun getBrowserComponent(): JComponent? = browser?.component

    /**
     * Navigate to a URL
     */
    fun navigateTo(url: String) {
        val normalizedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else url

        updateState { it.copy(currentUrl = normalizedUrl, isLoading = true) }
        browser?.loadURL(normalizedUrl)
    }

    /**
     * Reload current page
     */
    fun reload() {
        browser?.cefBrowser?.reload()
    }

    /**
     * Go back in history
     */
    fun goBack() {
        if (browser?.cefBrowser?.canGoBack() == true) {
            browser?.cefBrowser?.goBack()
        }
    }

    /**
     * Go forward in history
     */
    fun goForward() {
        if (browser?.cefBrowser?.canGoForward() == true) {
            browser?.cefBrowser?.goForward()
        }
    }

    /**
     * Toggle selection mode
     */
    fun toggleSelectionMode() {
        val newMode = !_state.value.isSelectionMode
        updateState { it.copy(isSelectionMode = newMode) }
        executeJavaScript("window.webEditBridge?.setSelectionMode($newMode);")
    }

    /**
     * Toggle DOM sidebar visibility
     */
    fun toggleDOMSidebar() {
        updateState { it.copy(showDOMSidebar = !it.showDOMSidebar) }
    }

    /**
     * Highlight an element by selector
     */
    fun highlightElement(selector: String) {
        val escapedSelector = selector
            .replace("\\", "\\\\")
            .replace("'", "\\'")
        executeJavaScript("window.webEditBridge?.highlightElement('$escapedSelector');")
    }

    /**
     * Clear all highlights
     */
    fun clearHighlights() {
        executeJavaScript("window.webEditBridge?.clearHighlights();")
    }

    /**
     * Scroll to an element
     */
    fun scrollToElement(selector: String) {
        val escapedSelector = selector
            .replace("\\", "\\\\")
            .replace("'", "\\'")
        executeJavaScript("window.webEditBridge?.scrollToElement('$escapedSelector');")
    }

    /**
     * Refresh DOM tree
     */
    fun refreshDOMTree() {
        executeJavaScript("window.webEditBridge?.getDOMTree();")
    }

    /**
     * Send a chat message about the page/element
     */
    fun sendChatMessage(message: String) {
        if (_state.value.isLoading) return

        currentJob?.cancel()
        currentJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                renderer.addUserMessage(message)

                val service = llmService
                if (service == null) {
                    renderer.renderError("LLM service not configured. Please set up your model in settings.")
                    return@launch
                }

                // Build context with selected element info
                val context = buildChatContext()
                val fullPrompt = """
                    |Context: User is browsing a web page.
                    |$context
                    |
                    |User question: $message
                """.trimMargin()

                // Stream response using JewelRenderer's streaming API
                renderer.renderLLMResponseStart()
                service.streamPrompt(fullPrompt).collect { chunk ->
                    renderer.renderLLMResponseChunk(chunk)
                }
                renderer.renderLLMResponseEnd()

            } catch (e: CancellationException) {
                renderer.forceStop()
            } catch (e: Exception) {
                renderer.renderError("Error: ${e.message}")
            }
        }
    }

    /**
     * Stop current chat generation
     */
    fun stopGeneration() {
        currentJob?.cancel()
        currentJob = null
        renderer.forceStop()
    }

    /**
     * Clear chat history
     */
    fun clearChatHistory() {
        renderer.clearTimeline()
        currentJob?.cancel()
        currentJob = null
    }

    private fun buildChatContext(): String {
        val state = _state.value
        val sb = StringBuilder()
        sb.appendLine("Current URL: ${state.currentUrl}")
        sb.appendLine("Page Title: ${state.pageTitle}")

        state.selectedElement?.let { element ->
            sb.appendLine("Selected Element:")
            sb.appendLine("  Tag: ${element.tagName}")
            sb.appendLine("  Selector: ${element.selector}")
            element.textContent?.let { sb.appendLine("  Text: $it") }
            if (element.attributes.isNotEmpty()) {
                sb.appendLine("  Attributes: ${element.attributes}")
            }
        }

        return sb.toString()
    }

    private fun injectBridgeScript() {
        if (scriptInjected) return

        val callbackFunction = jsQuery?.inject("message") ?: return
        val script = IdeaWebEditBridgeScript.getScript(callbackFunction)
        executeJavaScript(script)
        scriptInjected = true
        updateState { it.copy(isReady = true) }

        // Request initial DOM tree
        coroutineScope.launch {
            delay(500) // Wait for script to initialize
            refreshDOMTree()
        }
    }

    private fun executeJavaScript(script: String) {
        browser?.cefBrowser?.executeJavaScript(script, browser?.cefBrowser?.url ?: "", 0)
    }

    private fun handleJsMessage(message: String) {
        try {
            val json = Json.parseToJsonElement(message).jsonObject
            val type = json["type"]?.jsonPrimitive?.content ?: return
            // data is now an object, not a string
            val data = json["data"]?.jsonObject ?: return

            when (type) {
                "PageLoaded" -> {
                    val url = data["url"]?.jsonPrimitive?.content ?: ""
                    val title = data["title"]?.jsonPrimitive?.content ?: ""
                    updateState {
                        it.copy(
                            currentUrl = url,
                            pageTitle = title,
                            isLoading = false
                        )
                    }
                }

                "ElementSelected" -> {
                    val elementJson = data["element"]?.jsonObject
                    if (elementJson != null) {
                        val element = parseElement(elementJson)
                        updateState { it.copy(selectedElement = element) }
                    }
                }

                "DOMTreeUpdated" -> {
                    val rootJson = data["root"]?.jsonObject
                    if (rootJson != null) {
                        val root = parseElement(rootJson)
                        updateState { it.copy(domTree = root) }
                    }
                }

                "Error" -> {
                    val errorMessage = data["message"]?.jsonPrimitive?.content ?: "Unknown error"
                    updateState { it.copy(error = errorMessage) }
                }
            }
        } catch (e: Exception) {
            println("[IdeaWebEditViewModel] Error parsing message: ${e.message}")
        }
    }

    private fun parseElement(json: JsonObject): IdeaDOMElement {
        val attributes = json["attributes"]?.jsonObject?.let { attrs ->
            attrs.entries.associate { (k, v) -> k to (v.jsonPrimitive.contentOrNull ?: "") }
        } ?: emptyMap()

        val boundingBox = json["boundingBox"]?.jsonObject?.let { bb ->
            IdeaBoundingBox(
                x = bb["x"]?.jsonPrimitive?.double ?: 0.0,
                y = bb["y"]?.jsonPrimitive?.double ?: 0.0,
                width = bb["width"]?.jsonPrimitive?.double ?: 0.0,
                height = bb["height"]?.jsonPrimitive?.double ?: 0.0
            )
        }

        val children = json["children"]?.jsonArray?.mapNotNull { child ->
            try {
                parseElement(child.jsonObject)
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()

        return IdeaDOMElement(
            id = json["id"]?.jsonPrimitive?.content ?: "",
            tagName = json["tagName"]?.jsonPrimitive?.content ?: "",
            selector = json["selector"]?.jsonPrimitive?.content ?: "",
            textContent = json["textContent"]?.jsonPrimitive?.contentOrNull,
            attributes = attributes,
            boundingBox = boundingBox,
            children = children
        )
    }

    private fun updateState(update: (IdeaWebEditState) -> IdeaWebEditState) {
        _state.update(update)
    }

    override fun dispose() {
        currentJob?.cancel()
        // Browser and jsQuery are disposed via Disposer.register
    }
}
