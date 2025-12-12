package cc.unitmesh.viewer.web.webedit

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.multiplatform.webview.jsbridge.IJsMessageHandler
import com.multiplatform.webview.jsbridge.JsMessage
import com.multiplatform.webview.jsbridge.rememberWebViewJsBridge
import com.multiplatform.webview.web.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * JVM implementation of WebEditView using compose-webview-multiplatform
 */
@Composable
actual fun WebEditView(
    bridge: WebEditBridge,
    modifier: Modifier,
    onPageLoaded: (url: String, title: String) -> Unit,
    onElementSelected: (DOMElement) -> Unit,
    onDOMTreeUpdated: (DOMElement) -> Unit
) {
    val currentUrl by bridge.currentUrl.collectAsState()

    // Initialize WebView state with default URL
    val webViewState = rememberWebViewState(url = "https://ide.unitmesh.cc")
    val webViewNavigator = rememberWebViewNavigator()
    val jsBridge = rememberWebViewJsBridge()

    // Track if script has been injected for this page
    var scriptInjected by remember { mutableStateOf(false) }

    // Configure the JVM bridge with WebView callbacks
    LaunchedEffect(Unit) {
        if (bridge is JvmWebEditBridge) {
            bridge.executeJavaScript = { script ->
                webViewNavigator.evaluateJavaScript(script)
            }
            bridge.navigateCallback = { url ->
                println("[WebEditView] Navigate callback: $url")
                webViewNavigator.loadUrl(url)
            }
            bridge.reloadCallback = {
                webViewNavigator.reload()
            }
            bridge.goBackCallback = {
                if (webViewNavigator.canGoBack) {
                    webViewNavigator.navigateBack()
                }
            }
            bridge.goForwardCallback = {
                if (webViewNavigator.canGoForward) {
                    webViewNavigator.navigateForward()
                }
            }
        }
    }

    // Navigate when URL changes from the bridge
    LaunchedEffect(currentUrl) {
        if (currentUrl.isNotEmpty() && currentUrl != webViewState.lastLoadedUrl) {
            println("[WebEditView] URL changed to: $currentUrl")
            webViewNavigator.loadUrl(currentUrl)
        }
    }

    // Register JS bridge handlers for messages from WebView
    LaunchedEffect(Unit) {
        jsBridge.register(object : IJsMessageHandler {
            override fun methodName(): String = "webEditMessage"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit
            ) {
                try {
                    val json = Json.parseToJsonElement(message.params).jsonObject
                    val type = json["type"]?.jsonPrimitive?.content ?: return
                    val data = json["data"]?.jsonPrimitive?.content ?: "{}"

                    when (type) {
                        "PageLoaded" -> {
                            val pageData = Json.parseToJsonElement(data).jsonObject
                            val url = pageData["url"]?.jsonPrimitive?.content ?: ""
                            val title = pageData["title"]?.jsonPrimitive?.content ?: ""
                            bridge.handleMessage(WebEditMessage.PageLoaded(url, title))
                            onPageLoaded(url, title)
                        }
                        "ElementSelected" -> {
                            val elementData = Json.parseToJsonElement(data).jsonObject
                            val elementJson = elementData["element"]?.jsonObject
                            if (elementJson != null) {
                                val element = parseElement(elementJson.toString())
                                if (element != null) {
                                    bridge.handleMessage(WebEditMessage.ElementSelected(element))
                                    onElementSelected(element)
                                }
                            }
                        }
                        "DOMTreeUpdated" -> {
                            val treeData = Json.parseToJsonElement(data).jsonObject
                            val rootJson = treeData["root"]?.jsonObject
                            if (rootJson != null) {
                                val root = parseElement(rootJson.toString())
                                if (root != null) {
                                    bridge.handleMessage(WebEditMessage.DOMTreeUpdated(root))
                                    onDOMTreeUpdated(root)
                                }
                            }
                        }
                        "Error" -> {
                            val errorData = Json.parseToJsonElement(data).jsonObject
                            val errorMessage = errorData["message"]?.jsonPrimitive?.content ?: "Unknown error"
                            bridge.handleMessage(WebEditMessage.Error(errorMessage))
                        }
                    }
                } catch (e: Exception) {
                    println("[WebEditView] Error parsing message: ${e.message}")
                }
                callback("ok")
            }
        })
    }

    // Monitor loading state and inject script when page loads
    LaunchedEffect(webViewState.isLoading, webViewState.lastLoadedUrl) {
        if (!webViewState.isLoading && webViewState.loadingState is LoadingState.Finished) {
            // Reset script injection flag for new page
            if (webViewState.lastLoadedUrl != null && webViewState.lastLoadedUrl != "about:blank") {
                scriptInjected = false

                // Update bridge state
                if (bridge is JvmWebEditBridge) {
                    bridge.setLoading(false)
                    bridge.setUrl(webViewState.lastLoadedUrl ?: "")
                    bridge.setTitle(webViewState.pageTitle ?: "")
                }

                // Inject the WebEdit bridge script
                kotlinx.coroutines.delay(300) // Wait for page to stabilize
                val script = getWebEditBridgeScript()
                webViewNavigator.evaluateJavaScript(script)
                scriptInjected = true

                bridge.markReady()
                println("[WebEditView] Bridge script injected for: ${webViewState.lastLoadedUrl}")
            }
        } else if (webViewState.isLoading) {
            if (bridge is JvmWebEditBridge) {
                bridge.setLoading(true)
            }
        }
    }

    WebView(
        state = webViewState,
        navigator = webViewNavigator,
        modifier = modifier,
        captureBackPresses = false,
        webViewJsBridge = jsBridge
    )
}

/**
 * Parse a JSON string into a DOMElement
 */
private fun parseElement(jsonString: String): DOMElement? {
    return try {
        Json.decodeFromString<DOMElement>(jsonString)
    } catch (e: Exception) {
        println("[WebEditView] Failed to parse element: ${e.message}")
        null
    }
}

