package cc.unitmesh.devins.ui.compose.agent.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.agent.ArtifactAgent
import cc.unitmesh.viewer.web.KcefInitState
import cc.unitmesh.viewer.web.KcefManager
import com.multiplatform.webview.jsbridge.IJsMessageHandler
import com.multiplatform.webview.jsbridge.JsMessage
import com.multiplatform.webview.jsbridge.rememberWebViewJsBridge
import com.multiplatform.webview.util.KLogSeverity
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * JVM implementation of ArtifactPreviewPanel using compose-webview-multiplatform.
 * This uses KCEF (initialized by KcefManager) to render HTML artifacts.
 */
@Composable
actual fun ArtifactPreviewPanel(
    artifact: ArtifactAgent.Artifact,
    onConsoleLog: (String, String) -> Unit,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()

    // Check KCEF initialization state
    val kcefInitState by KcefManager.initState.collectAsState()

    // Toggle between preview and source view
    var showSource by remember { mutableStateOf(false) }

    // Prepare HTML with console.log interception script
    val htmlWithConsole = remember(artifact.content) {
        injectConsoleCapture(artifact.content)
    }

    Column(modifier = modifier) {
        // Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title
                Text(
                    text = artifact.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Toggle source/preview
                    IconButton(
                        onClick = { showSource = !showSource },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (showSource) Icons.Default.Visibility else Icons.Default.Code,
                            contentDescription = if (showSource) "Show Preview" else "Show Source",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Open in browser
                    IconButton(
                        onClick = {
                            scope.launch {
                                openInBrowser(artifact.content, artifact.title)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Open in Browser",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Save file
                    IconButton(
                        onClick = {
                            saveArtifactFile(artifact)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Content area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                showSource -> {
                    // Show source code
                    SourceCodeView(
                        content = artifact.content,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                kcefInitState !is KcefInitState.Initialized -> {
                    // KCEF not ready - show loading or fallback
                    KcefStatusView(
                        state = kcefInitState,
                        onShowSource = { showSource = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    // KCEF ready - show WebView
                    ArtifactWebView(
                        html = htmlWithConsole,
                        onConsoleLog = onConsoleLog,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * WebView component using compose-webview-multiplatform
 */
@Composable
private fun ArtifactWebView(
    html: String,
    onConsoleLog: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    // IMPORTANT:
    // Start from about:blank so that JS bridge handlers are registered BEFORE we inject HTML.
    // Otherwise, early console.log calls during page parse can run before kmpJsBridge is available,
    // causing logs to be dropped (same class of issue as WebEditView.jvm.kt).
    val webViewState = rememberWebViewState(url = "about:blank")
    val webViewNavigator = rememberWebViewNavigator()
    val jsBridge = rememberWebViewJsBridge()

    // Register JS message handler for console.log capture
    LaunchedEffect(Unit) {
        jsBridge.register(object : IJsMessageHandler {
            override fun methodName(): String = "artifactConsole"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit
            ) {
                try {
                    // Params can be:
                    // - a JSON object string: {"level":"log","message":"..."}
                    // - a JSON string containing a JSON object (double-encoded)
                    // - a plain string
                    val params = message.params
                    val level: String
                    val msg: String

                    val element = runCatching { Json.parseToJsonElement(params) }.getOrNull()
                    if (element == null) {
                        level = "log"
                        msg = params
                    } else if (element is kotlinx.serialization.json.JsonObject) {
                        level = element["level"]?.jsonPrimitive?.content ?: "log"
                        msg = element["message"]?.jsonPrimitive?.content ?: ""
                    } else {
                        // Could be JsonPrimitive string containing JSON
                        val primitiveContent: String? = runCatching { element.jsonPrimitive.content }.getOrNull()
                        val nested = primitiveContent?.let { s: String ->
                            runCatching { Json.parseToJsonElement(s) }.getOrNull()
                        }
                        if (nested is kotlinx.serialization.json.JsonObject) {
                            level = nested["level"]?.jsonPrimitive?.content ?: "log"
                            msg = nested["message"]?.jsonPrimitive?.content ?: (primitiveContent ?: "")
                        } else {
                            level = "log"
                            msg = primitiveContent ?: params
                        }
                    }
                    if (msg.isNotBlank()) onConsoleLog(level, msg)
                } catch (e: Exception) {
                    onConsoleLog("warn", "Error handling console message: ${e.message}")
                }
                callback("{}")
            }
        })
    }

    // Configure WebView settings early (helps debugging + parity with WebEditView)
    LaunchedEffect(Unit) {
        webViewState.webSettings.apply {
            // Avoid noisy CEF console output (e.g. internal bridge callback logs)
            logSeverity = KLogSeverity.Error
            allowUniversalAccessFromFileURLs = true
        }
    }

    // Inject/refresh HTML only after the initial about:blank load is finished.
    // This ensures kmpJsBridge is available when the page's scripts (console.log) run.
    LaunchedEffect(webViewState.isLoading, html) {
        val finished = !webViewState.isLoading && webViewState.loadingState is LoadingState.Finished
        if (finished) {
            // Small delay to give the bridge time to attach in some environments.
            delay(50)
            val escaped = escapeForTemplateLiteral(html)
            val js = """
                try {
                  document.open();
                  document.write(`$escaped`);
                  document.close();
                } catch (e) {
                  console.error('ArtifactWebView document.write failed:', e);
                }
            """.trimIndent()
            webViewNavigator.evaluateJavaScript(js)
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
 * KCEF status view - shown when KCEF is not initialized
 */
@Composable
private fun KcefStatusView(
    state: KcefInitState,
    onShowSource: () -> Unit,
    modifier: Modifier = Modifier
) {
    val downloadProgress by KcefManager.downloadProgress.collectAsState()

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (state) {
            is KcefInitState.Idle -> {
                Icon(
                    imageVector = Icons.Default.Web,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "WebView not initialized",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Click 'Show Source' to view the generated code",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onShowSource) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Show Source")
                }
            }

            is KcefInitState.Initializing -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Initializing WebView...",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (downloadProgress > 0f && downloadProgress < 100f) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.width(200.dp)
                    )
                    Text(
                        text = "${downloadProgress.toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            is KcefInitState.RestartRequired -> {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Restart Required",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Please restart the application to enable WebView",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is KcefInitState.Error -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "WebView Error",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = state.exception.message ?: "Unknown error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onShowSource) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Show Source Instead")
                }
            }

            is KcefInitState.Initialized -> {
                // Should not reach here
            }
        }
    }
}

/**
 * Source code view
 */
@Composable
private fun SourceCodeView(
    content: String,
    modifier: Modifier = Modifier
) {
    SelectionContainer {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Inject console.log capture script into HTML
 */
private fun injectConsoleCapture(html: String): String {
    val consoleScript = """
        <script>
        (function() {
            const originalConsole = {
                log: console.log.bind(console),
                info: console.info.bind(console),
                warn: console.warn.bind(console),
                error: console.error.bind(console)
            };
            
            const __autodevPendingLogs = [];
            let __autodevFlushTimer = null;
            
            function bridgeReady() {
                return (window.kmpJsBridge && typeof window.kmpJsBridge.callNative === 'function');
            }
            
            function sendPayload(payload) {
                try {
                    // Fire-and-forget: passing a callback can trigger verbose internal logs
                    // like "[INFO:CONSOLE] add callback: ...", and we don't need a JS callback
                    // for console forwarding.
                    window.kmpJsBridge.callNative('artifactConsole', JSON.stringify(payload));
                    return true;
                } catch (e) {
                    return false;
                }
            }
            
            function flushPending() {
                if (!bridgeReady()) return;
                while (__autodevPendingLogs.length > 0) {
                    const payload = __autodevPendingLogs.shift();
                    if (!sendPayload(payload)) break;
                }
                if (__autodevPendingLogs.length === 0 && __autodevFlushTimer) {
                    clearInterval(__autodevFlushTimer);
                    __autodevFlushTimer = null;
                }
            }
            
            function sendToKotlin(level, args) {
                const message = Array.from(args).map(arg => {
                    if (typeof arg === 'object') {
                        try { return JSON.stringify(arg); } 
                        catch { return String(arg); }
                    }
                    return String(arg);
                }).join(' ');
                const payload = {level: level, message: message};
                if (bridgeReady()) {
                    if (!sendPayload(payload)) {
                        __autodevPendingLogs.push(payload);
                    }
                } else {
                    __autodevPendingLogs.push(payload);
                    if (!__autodevFlushTimer) {
                        __autodevFlushTimer = setInterval(flushPending, 100);
                    }
                }
            }
            
            console.log = function() { sendToKotlin('log', arguments); originalConsole.log.apply(console, arguments); };
            console.info = function() { sendToKotlin('info', arguments); originalConsole.info.apply(console, arguments); };
            console.warn = function() { sendToKotlin('warn', arguments); originalConsole.warn.apply(console, arguments); };
            console.error = function() { sendToKotlin('error', arguments); originalConsole.error.apply(console, arguments); };
            
            window.onerror = function(msg, url, line, col, error) {
                sendToKotlin('error', ['Uncaught error: ' + msg + ' at ' + url + ':' + line]);
            };
            
            // Try to flush as soon as possible (in case bridge is already ready)
            flushPending();
        })();
        </script>
    """.trimIndent()

    // Insert script right after <head> or at the beginning of <body>
    return when {
        html.contains("<head>", ignoreCase = true) -> {
            html.replaceFirst(
                Regex("<head>", RegexOption.IGNORE_CASE),
                "<head>\n$consoleScript\n"
            )
        }
        html.contains("<body", ignoreCase = true) -> {
            val bodyRegex = Regex("<body[^>]*>", RegexOption.IGNORE_CASE)
            val match = bodyRegex.find(html)
            if (match != null) {
                html.replaceFirst(bodyRegex, "${match.value}\n$consoleScript\n")
            } else {
                "$consoleScript\n$html"
            }
        }
        html.contains("<html", ignoreCase = true) -> {
            val htmlRegex = Regex("<html[^>]*>", RegexOption.IGNORE_CASE)
            val match = htmlRegex.find(html)
            if (match != null) {
                html.replaceFirst(htmlRegex, "${match.value}\n$consoleScript\n")
            } else {
                "$consoleScript\n$html"
            }
        }
        else -> {
            "$consoleScript\n$html"
        }
    }
}

private fun escapeForTemplateLiteral(input: String): String {
    return input
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("$", "\\$")
}

/**
 * Open HTML content in system browser
 */
private suspend fun openInBrowser(html: String, title: String) {
    withContext(Dispatchers.IO) {
        try {
            val tempFile = File.createTempFile("artifact_${title.replace(" ", "_")}_", ".html")
            tempFile.writeText(html)
            tempFile.deleteOnExit()

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(tempFile.toURI())
            }
        } catch (e: Exception) {
            println("Failed to open in browser: ${e.message}")
        }
    }
}

/**
 * Save artifact to file
 */
private fun saveArtifactFile(artifact: ArtifactAgent.Artifact) {
    try {
        val fileChooser = JFileChooser().apply {
            dialogTitle = "Save Artifact"
            selectedFile = File("${artifact.title.replace(" ", "_")}.html")
            fileFilter = FileNameExtensionFilter("HTML Files", "html", "htm")
        }

        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            var file = fileChooser.selectedFile
            if (!file.name.endsWith(".html") && !file.name.endsWith(".htm")) {
                file = File(file.absolutePath + ".html")
            }
            file.writeText(artifact.content)
        }
    } catch (e: Exception) {
        println("Failed to save file: ${e.message}")
    }
}

/**
 * Export artifact implementation for JVM
 * Supports both .html (raw) and .unit (bundle with metadata) formats
 */
actual fun exportArtifact(
    artifact: ArtifactAgent.Artifact,
    onNotification: (String, String) -> Unit
) {
    try {
        val sanitizedName = artifact.title.replace(Regex("[^a-zA-Z0-9\\-_ ]"), "").replace(" ", "_")

        val fileChooser = JFileChooser().apply {
            dialogTitle = "Export Artifact"
            selectedFile = File("$sanitizedName.unit")

            // Add filter for .unit bundle format (recommended)
            val unitFilter = FileNameExtensionFilter("AutoDev Unit Bundle (*.unit)", "unit")
            addChoosableFileFilter(unitFilter)
            // Add filter for raw HTML
            addChoosableFileFilter(FileNameExtensionFilter("HTML Files (*.html)", "html", "htm"))

            fileFilter = unitFilter // Default to .unit
        }

        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            var file = fileChooser.selectedFile
            val selectedFilter = fileChooser.fileFilter

            // Determine format based on filter or extension
            val isUnitFormat = when {
                file.name.endsWith(".unit") -> true
                file.name.endsWith(".html") || file.name.endsWith(".htm") -> false
                selectedFilter.description.contains("unit", ignoreCase = true) -> {
                    file = File(file.absolutePath + ".unit")
                    true
                }
                else -> {
                    file = File(file.absolutePath + ".html")
                    false
                }
            }

            if (isUnitFormat) {
                // Export as .unit bundle (without conversation history - use exportArtifactBundle for full bundle)
                val bundle = cc.unitmesh.agent.artifact.ArtifactBundle.fromArtifact(
                    artifact = artifact,
                    conversationHistory = emptyList(),
                    modelInfo = null
                )
                exportAsUnitBundle(bundle, file, onNotification)
            } else {
                // Export as raw HTML
                file.writeText(artifact.content)
                onNotification("success", "Artifact exported to ${file.absolutePath}")
            }
        }
    } catch (e: Exception) {
        onNotification("error", "Failed to export: ${e.message}")
    }
}

/**
 * Export artifact as .unit bundle format
 */
private fun exportAsUnitBundle(
    bundle: cc.unitmesh.agent.artifact.ArtifactBundle,
    outputFile: File,
    onNotification: (String, String) -> Unit
) {
    try {
        // Pack bundle using coroutines
        kotlinx.coroutines.runBlocking {
            val packer = cc.unitmesh.agent.artifact.ArtifactBundlePacker()
            when (val result = packer.pack(bundle, outputFile.absolutePath)) {
                is cc.unitmesh.agent.artifact.PackResult.Success -> {
                    onNotification("success", "Artifact bundle exported to ${result.outputPath}")
                }
                is cc.unitmesh.agent.artifact.PackResult.Error -> {
                    onNotification("error", "Failed to export bundle: ${result.message}")
                }
            }
        }
    } catch (e: Exception) {
        onNotification("error", "Failed to create bundle: ${e.message}")
    }
}

/**
 * Export artifact bundle implementation for JVM
 * This is called from ArtifactPage with full conversation history
 */
actual fun exportArtifactBundle(
    bundle: cc.unitmesh.agent.artifact.ArtifactBundle,
    onNotification: (String, String) -> Unit
) {
    try {
        val sanitizedName = bundle.name.replace(Regex("[^a-zA-Z0-9\\-_ ]"), "").replace(" ", "_")

        val fileChooser = JFileChooser().apply {
            dialogTitle = "Export Artifact Bundle"
            selectedFile = File("$sanitizedName.unit")

            // Only .unit format for bundle export
            val unitFilter = FileNameExtensionFilter("AutoDev Unit Bundle (*.unit)", "unit")
            addChoosableFileFilter(unitFilter)
            fileFilter = unitFilter
        }

        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            var file = fileChooser.selectedFile
            if (!file.name.endsWith(".unit")) {
                file = File(file.absolutePath + ".unit")
            }
            exportAsUnitBundle(bundle, file, onNotification)
        }
    } catch (e: Exception) {
        onNotification("error", "Failed to export: ${e.message}")
    }
}
