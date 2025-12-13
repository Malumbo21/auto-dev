package cc.unitmesh.viewer.web.webedit

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.multiplatform.webview.jsbridge.IJsMessageHandler
import com.multiplatform.webview.jsbridge.JsMessage
import com.multiplatform.webview.jsbridge.rememberWebViewJsBridge
import com.multiplatform.webview.util.KLogSeverity
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

    val webViewState = rememberWebViewState(url = currentUrl.ifEmpty { "about:blank" })
    LaunchedEffect(Unit) {
        webViewState.webSettings.apply {
            logSeverity = KLogSeverity.Info
            allowUniversalAccessFromFileURLs = true
            customUserAgentString =
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 11_1) AppleWebKit/625.20 (KHTML, like Gecko) Version/14.3.43 Safari/625.20"
        }
    }

    val loadingState = webViewState.loadingState
    if (loadingState is LoadingState.Loading) {
        LinearProgressIndicator(
            progress = loadingState.progress,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    val webViewNavigator = rememberWebViewNavigator()
    val jsBridge = rememberWebViewJsBridge()


    // Configure the JVM bridge with WebView callbacks
    LaunchedEffect(Unit) {
        if (bridge is JvmWebEditBridge) {
            bridge.executeJavaScript = { script ->
                webViewNavigator.evaluateJavaScript(script)
            }
            bridge.navigateCallback = { url ->
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
                    // data is now an object, not a string
                    val data = json["data"]?.jsonObject ?: return

                    when (type) {
                        "PageLoaded" -> {
                            val url = data["url"]?.jsonPrimitive?.content ?: ""
                            val title = data["title"]?.jsonPrimitive?.content ?: ""
                            bridge.handleMessage(WebEditMessage.PageLoaded(url, title))
                            onPageLoaded(url, title)
                        }
                        "ElementSelected" -> {
                            val elementJson = data["element"]?.jsonObject
                            if (elementJson != null) {
                                val element = parseElement(elementJson.toString())
                                if (element != null) {
                                    bridge.handleMessage(WebEditMessage.ElementSelected(element))
                                    onElementSelected(element)
                                }
                            }
                        }
                        "DOMTreeUpdated" -> {
                            val rootJson = data["root"]?.jsonObject
                            if (rootJson != null) {
                                val root = parseElement(rootJson.toString())
                                if (root != null) {
                                    bridge.handleMessage(WebEditMessage.DOMTreeUpdated(root))
                                    onDOMTreeUpdated(root)
                                }
                            }
                        }
                        "Error" -> {
                            val errorMessage = data["message"]?.jsonPrimitive?.content ?: "Unknown error"
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
    LaunchedEffect(webViewState.isLoading, webViewState.lastLoadedUrl, loadingState) {
        when {
            // Handle finished state
            !webViewState.isLoading && loadingState is LoadingState.Finished -> {
                if (webViewState.lastLoadedUrl != null && webViewState.lastLoadedUrl != "about:blank") {
                    // Update bridge state
                    if (bridge is JvmWebEditBridge) {
                        bridge.setLoading(false)
                        bridge.setUrl(webViewState.lastLoadedUrl ?: "")
                        bridge.setTitle(webViewState.pageTitle ?: "")
                    }

                    // Check if the page loaded successfully by examining the URL
                    // If there's a certificate error or network error, the page might not load
                    // We'll detect this by checking if the JavaScript bridge can be injected
                    try {
                        // Inject the WebEdit bridge script
                        kotlinx.coroutines.delay(300) // Wait for page to stabilize
                        val script = getWebEditBridgeScript()
                        webViewNavigator.evaluateJavaScript(script)

                        bridge.markReady()
                        println("[WebEditView] Bridge script injected for: ${webViewState.lastLoadedUrl}")
                    } catch (e: Exception) {
                        println("[WebEditView] Failed to inject bridge script: ${e.message}")
                        if (bridge is JvmWebEditBridge) {
                            bridge.handleMessage(WebEditMessage.Error("Failed to load page: ${e.message}"))
                        }
                    }
                }
            }
            // Handle loading state
            webViewState.isLoading -> {
                if (bridge is JvmWebEditBridge) {
                    bridge.setLoading(true)
                }
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

