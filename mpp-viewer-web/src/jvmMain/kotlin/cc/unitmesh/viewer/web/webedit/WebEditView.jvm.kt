package cc.unitmesh.viewer.web.webedit

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.multiplatform.webview.jsbridge.IJsMessageHandler
import com.multiplatform.webview.jsbridge.JsMessage
import com.multiplatform.webview.jsbridge.rememberWebViewJsBridge
import com.multiplatform.webview.util.KLogSeverity
import com.multiplatform.webview.web.*
import kotlinx.coroutines.delay
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

    val latestOnPageLoaded by rememberUpdatedState(onPageLoaded)
    val latestOnElementSelected by rememberUpdatedState(onElementSelected)
    val latestOnDOMTreeUpdated by rememberUpdatedState(onDOMTreeUpdated)

    // IMPORTANT:
    // We always start from about:blank so that JS bridge handlers are registered
    // BEFORE the first real navigation. Otherwise kmpJsBridge might not get injected
    // for the initial page, which breaks JS -> Kotlin messages (DOMTreeUpdated, etc.).
    val webViewState = rememberWebViewState(url = "about:blank")
    LaunchedEffect(Unit) {
        webViewState.webSettings.apply {
            logSeverity = KLogSeverity.Info
            allowUniversalAccessFromFileURLs = true
            customUserAgentString =
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 11_1) AppleWebKit/625.20 (KHTML, like Gecko) Version/14.3.43 Safari/625.20"
        }
        
        println("[WebEditView] WebView settings configured")
        println("[WebEditView] Log severity: ${webViewState.webSettings.logSeverity}")
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
    var isJsBridgeRegistered by remember { mutableStateOf(false) }


    // Configure the JVM bridge with WebView callbacks
    LaunchedEffect(Unit) {
        println("[WebEditView] 🔧 Configuring bridge callbacks...")
        if (bridge is JvmWebEditBridge) {
            println("[WebEditView] ✅ Setting up JvmWebEditBridge callbacks")
            bridge.executeJavaScript = { script ->
                println("[WebEditView] 📜 Executing JS: ${script.take(100)}...")
                webViewNavigator.evaluateJavaScript(script)
            }
            bridge.navigateCallback = { url ->
                println("[WebEditView] 🌐 Navigate to: $url")
                webViewNavigator.loadUrl(url)
            }
            bridge.reloadCallback = {
                println("[WebEditView] 🔄 Reload")
                webViewNavigator.reload()
            }
            bridge.goBackCallback = {
                println("[WebEditView] ⬅️ Go back (canGoBack=${webViewNavigator.canGoBack})")
                if (webViewNavigator.canGoBack) {
                    webViewNavigator.navigateBack()
                }
            }
            bridge.goForwardCallback = {
                println("[WebEditView] ➡️ Go forward (canGoForward=${webViewNavigator.canGoForward})")
                if (webViewNavigator.canGoForward) {
                    webViewNavigator.navigateForward()
                }
            }
            println("[WebEditView] ✅ All bridge callbacks configured")
        } else {
            println("[WebEditView] ⚠️ Bridge is not JvmWebEditBridge: ${bridge::class.simpleName}")
        }
    }

    // Register JS bridge handlers for messages from WebView
    // IMPORTANT: This must be done BEFORE any page loads
    LaunchedEffect(Unit) {
        println("[WebEditView] 📡 Registering JS message handler...")
        
        jsBridge.register(object : IJsMessageHandler {
            override fun methodName(): String {
                val name = "webEditMessage"
                println("[WebEditView] ✅ Registered JS handler: $name")
                return name
            }

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit
            ) {
                println("[WebEditView] 📨 Received JS message:")
                println("  - Params length: ${message.params.length} chars")
                println("  - Params preview: ${message.params.take(200)}...")
                
                try {
                    val json = Json.parseToJsonElement(message.params).jsonObject
                    val type = json["type"]?.jsonPrimitive?.content
                    println("[WebEditView] 📋 Message type: $type")
                    
                    if (type == null) {
                        println("[WebEditView] ⚠️ Message type is null!")
                        callback("error: no type")
                        return
                    }
                    
                    // data is now an object, not a string
                    val data = json["data"]?.jsonObject
                    if (data == null) {
                        println("[WebEditView] ⚠️ Message data is null!")
                        callback("error: no data")
                        return
                    }

                    when (type) {
                        "PageLoaded" -> {
                            val url = data["url"]?.jsonPrimitive?.content ?: ""
                            val title = data["title"]?.jsonPrimitive?.content ?: ""
                            println("[WebEditView] ✓ PageLoaded: url=$url, title=$title")
                            bridge.handleMessage(WebEditMessage.PageLoaded(url, title))
                            latestOnPageLoaded(url, title)
                        }
                        "ElementSelected" -> {
                            val elementJson = data["element"]?.jsonObject
                            if (elementJson != null) {
                                val element = parseElement(elementJson.toString())
                                if (element != null) {
                                    println("[WebEditView] ✓ ElementSelected: ${element.tagName} - ${element.selector}")
                                    bridge.handleMessage(WebEditMessage.ElementSelected(element))
                                    latestOnElementSelected(element)
                                }
                            }
                        }
                        "DOMTreeUpdated" -> {
                            val rootJson = data["root"]?.jsonObject
                            if (rootJson != null) {
                                val root = parseElement(rootJson.toString())
                                if (root != null) {
                                    println("[WebEditView] ✓ DOMTreeUpdated: ${root.children.size} children")
                                    bridge.handleMessage(WebEditMessage.DOMTreeUpdated(root))
                                    latestOnDOMTreeUpdated(root)
                                }
                            }
                        }
                        "D2SnapTreeUpdated" -> {
                            val rootJson = data["root"]?.jsonObject
                            if (rootJson != null) {
                                val root = parseD2SnapElement(rootJson.toString())
                                if (root != null) {
                                    bridge.handleMessage(WebEditMessage.D2SnapTreeUpdated(root))
                                }
                            }
                        }
                        "AccessibilityTreeUpdated" -> {
                            val rootJson = data["root"]?.jsonObject
                            if (rootJson != null) {
                                val root = parseAccessibilityNode(rootJson.toString())
                                if (root != null) {
                                    bridge.handleMessage(WebEditMessage.AccessibilityTreeUpdated(root))
                                }
                            }
                        }
                        "ActionableElementsUpdated" -> {
                            val elementsJson = data["elements"]
                            if (elementsJson != null) {
                                val elements = parseAccessibilityNodeList(elementsJson.toString())
                                if (elements != null) {
                                    bridge.handleMessage(WebEditMessage.ActionableElementsUpdated(elements))
                                }
                            }
                        }
                        "ActionResult" -> {
                            val result = parseActionResult(data.toString())
                            if (result != null) {
                                bridge.handleMessage(result)
                            }
                        }
                        "ScreenshotCaptured" -> {
                            val screenshot = parseScreenshotCaptured(data.toString())
                            if (screenshot != null) {
                                bridge.handleMessage(screenshot)
                            }
                        }
                        "Diagnostic" -> {
                            val payload = data["payload"]?.jsonPrimitive?.content ?: data.toString()
                            println("[WebEditView] 🔎 Diagnostic from JS: $payload")
                        }
                        "Error" -> {
                            val errorMessage = data["message"]?.jsonPrimitive?.content ?: "Unknown error"
                            println("[WebEditView] ✗ Error from JS: $errorMessage")
                            bridge.handleMessage(WebEditMessage.Error(errorMessage))
                        }
                    }
                    callback("ok")
                } catch (e: Exception) {
                    println("[WebEditView] ✗ Error parsing message: ${e.message}")
                    e.printStackTrace()
                    callback("error: ${e.message}")
                }
            }
        })
        
        // Wait a bit to ensure bridge is fully registered
        delay(100)
        isJsBridgeRegistered = true
        println("[WebEditView] ✅ JS bridge registration complete")
    }

    // Navigate when URL changes from the bridge, but only after JS bridge is registered.
    LaunchedEffect(currentUrl, isJsBridgeRegistered) {
        if (!isJsBridgeRegistered) return@LaunchedEffect
        if (currentUrl.isNotEmpty() && currentUrl != webViewState.lastLoadedUrl) {
            println("[WebEditView] URL changed to: $currentUrl")
            webViewNavigator.loadUrl(currentUrl)
        }
    }

    // Monitor loading state and inject script when page loads
    LaunchedEffect(webViewState.isLoading, webViewState.lastLoadedUrl, loadingState) {
        println("[WebEditView] State changed: isLoading=${webViewState.isLoading}, lastLoadedUrl=${webViewState.lastLoadedUrl}, loadingState=$loadingState")
        
        when {
            // Handle finished state
            !webViewState.isLoading && loadingState is LoadingState.Finished -> {
                println("[WebEditView] Page finished loading: ${webViewState.lastLoadedUrl}")
                
                if (webViewState.lastLoadedUrl != null && webViewState.lastLoadedUrl != "about:blank") {
                    println("[WebEditView] Processing loaded page: ${webViewState.lastLoadedUrl}")
                    
                    // Update bridge state
                    if (bridge is JvmWebEditBridge) {
                        bridge.setLoading(false)
                        bridge.setUrl(webViewState.lastLoadedUrl ?: "")
                        bridge.setTitle(webViewState.pageTitle ?: "")
                    }

                    // Inject the bridge script
                    try {
                        println("[WebEditView] Waiting 500ms for page to stabilize...")
                        delay(500) // Increased from 300ms to ensure page is ready
                        
                        println("[WebEditView] Injecting bridge script...")
                        val script = getWebEditBridgeScript()
                        println("[WebEditView] Script length: ${script.length} chars")
                        
                        // First, check if kmpJsBridge exists BEFORE injecting our script
                        println("[WebEditView] 🔍 Pre-injection diagnostic...")
                        val preCheckScript = """
                            (function() {
                                var result = {
                                    kmpJsBridgeType: typeof window.kmpJsBridge,
                                    hasCallNative: window.kmpJsBridge ? (typeof window.kmpJsBridge.callNative) : 'N/A',
                                    windowKeys: Object.keys(window).filter(k => k.toLowerCase().includes('bridge') || k.toLowerCase().includes('kmp')).join(', ')
                                };
                                console.log('[Diagnostic] Pre-injection check:', JSON.stringify(result));
                                if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                                    try {
                                        window.kmpJsBridge.callNative(
                                            'webEditMessage',
                                            JSON.stringify({ type: 'Diagnostic', data: { payload: JSON.stringify(result) } }),
                                            function(r) { console.log('[Diagnostic] Kotlin callback:', r); }
                                        );
                                    } catch (e) {
                                        console.error('[Diagnostic] callNative failed', e);
                                    }
                                }
                                return JSON.stringify(result);
                            })();
                        """.trimIndent()
                        
                        try {
                            // NOTE: evaluateJavaScript does not provide a result on this platform.
                            // We send diagnostics back via kmpJsBridge (when available) and also log to console.
                            webViewNavigator.evaluateJavaScript(preCheckScript)
                            delay(500)
                        } catch (e: Exception) {
                            println("[WebEditView] ⚠️ Pre-check failed: ${e.message}")
                        }
                        
                        webViewNavigator.evaluateJavaScript(script)
                        
                        println("[WebEditView] ✓ Bridge script injected successfully")
                        
                        // Wait for script to execute
                        delay(200)
                        
                        // Check if kmpJsBridge is available
                        println("[WebEditView] Checking bridge availability...")
                        webViewNavigator.evaluateJavaScript("""
                            (function() {
                                console.log('[WebEditView Check] typeof window.kmpJsBridge:', typeof window.kmpJsBridge);
                                console.log('[WebEditView Check] typeof window.webEditBridge:', typeof window.webEditBridge);
                                if (window.kmpJsBridge) {
                                    console.log('[WebEditView Check] kmpJsBridge.callNative:', typeof window.kmpJsBridge.callNative);
                                }
                                if (window.webEditBridge) {
                                    console.log('[WebEditView Check] webEditBridge.sendToKotlin:', typeof window.webEditBridge.sendToKotlin);
                                }
                            })();
                        """.trimIndent())
                        
                        // Wait a bit more for checks to complete
                        delay(200)
                        
                        // Test bridge communication
                        println("[WebEditView] Testing bridge communication...")
                        webViewNavigator.evaluateJavaScript("""
                            (function() {
                                console.log('[WebEditView Test] Manually triggering PageLoaded...');
                                if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                                    try {
                                        window.kmpJsBridge.callNative(
                                            'webEditMessage',
                                            JSON.stringify({
                                                type: 'PageLoaded',
                                                data: { 
                                                    url: window.location.href, 
                                                    title: document.title 
                                                }
                                            }),
                                            function(r) { 
                                                console.log('[WebEditView Test] ✓ Callback received:', r); 
                                            }
                                        );
                                        console.log('[WebEditView Test] ✓ callNative completed');
                                    } catch (e) {
                                        console.error('[WebEditView Test] ✗ Error:', e);
                                    }
                                } else {
                                    console.error('[WebEditView Test] ✗ kmpJsBridge not available!');
                                }
                            })();
                        """.trimIndent())
                        
                        bridge.markReady()
                        println("[WebEditView] ✓ Bridge marked as ready for: ${webViewState.lastLoadedUrl}")
                    } catch (e: Exception) {
                        println("[WebEditView] ✗ Failed to inject bridge script: ${e.message}")
                        e.printStackTrace()
                        if (bridge is JvmWebEditBridge) {
                            bridge.handleMessage(WebEditMessage.Error("Failed to load page: ${e.message}"))
                        }
                    }
                } else {
                    println("[WebEditView] Skipping blank page: ${webViewState.lastLoadedUrl}")
                }
            }
            // Handle loading state
            webViewState.isLoading -> {
                println("[WebEditView] Page is loading...")
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
        json.decodeFromString<DOMElement>(jsonString)
    } catch (e: Exception) {
        println("[WebEditView] Failed to parse element: ${e.message}")
        null
    }
}

private val json = Json { ignoreUnknownKeys = true }

private fun parseD2SnapElement(jsonString: String): D2SnapElement? {
    return try {
        json.decodeFromString<D2SnapElement>(jsonString)
    } catch (_: Exception) {
        null
    }
}

private fun parseAccessibilityNode(jsonString: String): AccessibilityNode? {
    return try {
        json.decodeFromString<AccessibilityNode>(jsonString)
    } catch (_: Exception) {
        null
    }
}

private fun parseAccessibilityNodeList(jsonString: String): List<AccessibilityNode>? {
    return try {
        json.decodeFromString<List<AccessibilityNode>>(jsonString)
    } catch (_: Exception) {
        null
    }
}

private fun parseActionResult(jsonString: String): WebEditMessage.ActionResult? {
    return try {
        json.decodeFromString<WebEditMessage.ActionResult>(jsonString)
    } catch (_: Exception) {
        null
    }
}

private fun parseScreenshotCaptured(jsonString: String): WebEditMessage.ScreenshotCaptured? {
    return try {
        json.decodeFromString<WebEditMessage.ScreenshotCaptured>(jsonString)
    } catch (_: Exception) {
        null
    }
}

