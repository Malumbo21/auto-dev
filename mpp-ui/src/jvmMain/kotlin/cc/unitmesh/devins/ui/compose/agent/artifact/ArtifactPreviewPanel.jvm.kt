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
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors
import com.multiplatform.webview.jsbridge.IJsMessageHandler
import com.multiplatform.webview.jsbridge.JsMessage
import com.multiplatform.webview.jsbridge.rememberWebViewJsBridge
import com.multiplatform.webview.web.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File

/**
 * JVM implementation of ArtifactPreviewPanel using compose-webview-multiplatform
 * 
 * This implementation uses WebView to render HTML artifacts with console.log capture.
 */
@Composable
actual fun ArtifactPreviewPanel(
    artifact: ArtifactAgent.Artifact,
    onConsoleLog: (String, String) -> Unit,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    var showSource by remember { mutableStateOf(false) }
    var tempFile by remember { mutableStateOf<File?>(null) }

    // Inject console capturing script into the HTML
    val htmlWithConsoleCapture = remember(artifact.content) {
        injectConsoleCapture(artifact.content)
    }

    // Create temp file for "Open in Browser" fallback
    LaunchedEffect(artifact.content) {
        try {
            val file = File.createTempFile("artifact-${artifact.identifier}-", ".html")
            file.writeText(artifact.content)
            file.deleteOnExit()
            tempFile = file
        } catch (e: Exception) {
            onConsoleLog("error", "Failed to create temp file: ${e.message}")
        }
    }

    // WebView state with HTML content
    val webViewState = rememberWebViewStateWithHTMLData(
        data = htmlWithConsoleCapture,
        baseUrl = "about:blank"
    )

    val webViewNavigator = rememberWebViewNavigator()
    val jsBridge = rememberWebViewJsBridge()

    // Register JS bridge for console log capture
    LaunchedEffect(Unit) {
        jsBridge.register(object : IJsMessageHandler {
            override fun methodName(): String = "consoleLog"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit
            ) {
                val params = message.params
                try {
                    val level = params.substringBefore("|")
                    val msg = params.substringAfter("|")
                    onConsoleLog(level, msg)
                } catch (e: Exception) {
                    onConsoleLog("log", params)
                }
                callback("")
            }
        })
        
        onConsoleLog("info", "Artifact loaded: ${artifact.title}")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AutoDevColors.Void.surface1)
    ) {
        // Header with title and actions
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AutoDevColors.Void.surface2,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = AutoDevColors.Energy.xiu
                    )
                    Text(
                        text = artifact.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = AutoDevColors.Text.primary
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = AutoDevColors.Energy.xiuDim
                    ) {
                        Text(
                            text = artifact.type.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = AutoDevColors.Energy.xiu,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Toggle source view
                    FilledTonalIconButton(
                        onClick = { showSource = !showSource },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (showSource) Icons.Default.Preview else Icons.Default.Code,
                            contentDescription = if (showSource) "Show Preview" else "Show Source",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Open in browser button
                    FilledTonalIconButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    tempFile?.let { file ->
                                        if (Desktop.isDesktopSupported()) {
                                            Desktop.getDesktop().browse(file.toURI())
                                            onConsoleLog("info", "Opened in browser: ${file.name}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    onConsoleLog("error", "Failed to open browser: ${e.message}")
                                }
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

                    // Save file button
                    FilledTonalIconButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val saveFile = File("${artifact.identifier}.html")
                                    saveFile.writeText(artifact.content)
                                    onConsoleLog("info", "Saved to: ${saveFile.absolutePath}")
                                } catch (e: Exception) {
                                    onConsoleLog("error", "Failed to save: ${e.message}")
                                }
                            }
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

        // Main content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (showSource) {
                // Source code viewer
                SourceCodeViewer(
                    content = artifact.content,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // WebView preview
                WebView(
                    state = webViewState,
                    navigator = webViewNavigator,
                    webViewJsBridge = jsBridge,
                    modifier = Modifier.fillMaxSize()
                )

                // Loading indicator
                val loadingState = webViewState.loadingState
                if (loadingState is LoadingState.Loading) {
                    LinearProgressIndicator(
                        progress = { loadingState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    )
                }
            }
        }

        // Stats footer
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AutoDevColors.Void.surface2
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ID: ${artifact.identifier}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AutoDevColors.Text.tertiary
                )
                Text(
                    text = "${artifact.content.length} chars | ${artifact.content.lines().size} lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = AutoDevColors.Text.tertiary
                )
            }
        }
    }
}

/**
 * Inject console capture script into HTML
 * This captures console.log/warn/error and sends them to Kotlin via JS bridge
 */
private fun injectConsoleCapture(html: String): String {
    val consoleScript = """
        <script>
        (function() {
            const originalConsole = {
                log: console.log.bind(console),
                warn: console.warn.bind(console),
                error: console.error.bind(console),
                info: console.info.bind(console)
            };
            
            function sendToKotlin(level, args) {
                try {
                    const message = Array.from(args).map(arg => {
                        if (typeof arg === 'object') {
                            try { return JSON.stringify(arg); } catch(e) { return String(arg); }
                        }
                        return String(arg);
                    }).join(' ');
                    
                    if (window.kmpJsBridge) {
                        window.kmpJsBridge.callNative('consoleLog', level + '|' + message, function(r){});
                    }
                } catch(e) {}
            }
            
            console.log = function() {
                sendToKotlin('log', arguments);
                originalConsole.log.apply(console, arguments);
            };
            console.warn = function() {
                sendToKotlin('warn', arguments);
                originalConsole.warn.apply(console, arguments);
            };
            console.error = function() {
                sendToKotlin('error', arguments);
                originalConsole.error.apply(console, arguments);
            };
            console.info = function() {
                sendToKotlin('info', arguments);
                originalConsole.info.apply(console, arguments);
            };
            
            // Capture uncaught errors
            window.onerror = function(msg, url, line, col, error) {
                sendToKotlin('error', ['Uncaught: ' + msg + ' at ' + line + ':' + col]);
                return false;
            };
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
                html.substring(0, match.range.last + 1) + 
                    "\n$consoleScript\n" + 
                    html.substring(match.range.last + 1)
            } else {
                "$consoleScript\n$html"
            }
        }
        html.contains("<html", ignoreCase = true) -> {
            val htmlRegex = Regex("<html[^>]*>", RegexOption.IGNORE_CASE)
            val match = htmlRegex.find(html)
            if (match != null) {
                html.substring(0, match.range.last + 1) + 
                    "\n$consoleScript\n" + 
                    html.substring(match.range.last + 1)
            } else {
                "$consoleScript\n$html"
            }
        }
        else -> {
            "$consoleScript\n$html"
        }
    }
}

/**
 * Source code viewer with syntax highlighting colors
 */
@Composable
private fun SourceCodeViewer(
    content: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(8.dp)
            .background(AutoDevColors.Void.surface2, RoundedCornerShape(8.dp))
    ) {
        val verticalScroll = rememberScrollState()
        val horizontalScroll = rememberScrollState()

        SelectionContainer {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                ),
                color = AutoDevColors.Text.primary,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScroll)
                    .horizontalScroll(horizontalScroll)
                    .padding(12.dp)
            )
        }
    }
}
