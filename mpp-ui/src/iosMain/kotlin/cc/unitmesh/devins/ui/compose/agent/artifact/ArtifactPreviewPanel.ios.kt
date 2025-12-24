package cc.unitmesh.devins.ui.compose.agent.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cc.unitmesh.agent.ArtifactAgent
import com.multiplatform.webview.jsbridge.IJsMessageHandler
import com.multiplatform.webview.jsbridge.JsMessage
import com.multiplatform.webview.jsbridge.rememberWebViewJsBridge
import com.multiplatform.webview.web.*

/**
 * iOS implementation of ArtifactPreviewPanel using compose-webview-multiplatform
 */
@Composable
actual fun ArtifactPreviewPanel(
    artifact: ArtifactAgent.Artifact,
    onConsoleLog: (String, String) -> Unit,
    modifier: Modifier
) {
    // Inject console capturing script
    val htmlWithConsoleCapture = remember(artifact.content) {
        injectConsoleCapture(artifact.content)
    }

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
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = artifact.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = artifact.type.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // WebView
        WebView(
            state = webViewState,
            navigator = webViewNavigator,
            webViewJsBridge = jsBridge,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}

/**
 * Inject console capture script into HTML
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
            
            window.onerror = function(msg, url, line, col, error) {
                sendToKotlin('error', ['Uncaught: ' + msg + ' at ' + line + ':' + col]);
                return false;
            };
        })();
        </script>
    """.trimIndent()

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
        else -> {
            "$consoleScript\n$html"
        }
    }
}
