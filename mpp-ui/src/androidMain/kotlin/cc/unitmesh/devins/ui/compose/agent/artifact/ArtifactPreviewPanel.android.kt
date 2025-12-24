package cc.unitmesh.devins.ui.compose.agent.artifact

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import cc.unitmesh.agent.ArtifactAgent

/**
 * Android implementation of ArtifactPreviewPanel using WebView
 */
@Composable
actual fun ArtifactPreviewPanel(
    artifact: ArtifactAgent.Artifact,
    onConsoleLog: (String, String) -> Unit,
    modifier: Modifier
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                
                webViewClient = WebViewClient()
                
                // Capture console.log
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            val level = when (it.messageLevel()) {
                                ConsoleMessage.MessageLevel.ERROR -> "error"
                                ConsoleMessage.MessageLevel.WARNING -> "warn"
                                ConsoleMessage.MessageLevel.LOG -> "log"
                                ConsoleMessage.MessageLevel.TIP -> "info"
                                ConsoleMessage.MessageLevel.DEBUG -> "log"
                                else -> "log"
                            }
                            onConsoleLog(level, it.message())
                        }
                        return true
                    }
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                null,
                artifact.content,
                "text/html",
                "UTF-8",
                null
            )
        }
    )
}

