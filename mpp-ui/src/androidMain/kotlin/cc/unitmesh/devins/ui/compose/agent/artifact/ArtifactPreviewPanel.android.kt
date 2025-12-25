package cc.unitmesh.devins.ui.compose.agent.artifact

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import cc.unitmesh.agent.ArtifactAgent

/**
 * Android implementation of ArtifactPreviewPanel using native WebView.
 */
@Composable
actual fun ArtifactPreviewPanel(
    artifact: ArtifactAgent.Artifact,
    onConsoleLog: (String, String) -> Unit,
    modifier: Modifier
) {
    val htmlContent = remember(artifact.content) {
        artifact.content
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.let { msg ->
                            val level = when (msg.messageLevel()) {
                                ConsoleMessage.MessageLevel.ERROR -> "error"
                                ConsoleMessage.MessageLevel.WARNING -> "warn"
                                ConsoleMessage.MessageLevel.TIP -> "info"
                                else -> "log"
                            }
                            onConsoleLog(level, msg.message())
                        }
                        return true
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                null,
                htmlContent,
                "text/html",
                "UTF-8",
                null
            )
        },
        modifier = modifier
    )
}

/**
 * Export artifact implementation for Android
 * TODO: Implement using Android's share intent or file picker
 */
actual fun exportArtifact(
    artifact: ArtifactAgent.Artifact,
    onNotification: (String, String) -> Unit
) {
    // TODO: Implement Android export using share intent or SAF
    onNotification("info", "Export not yet implemented for Android")
}

/**
 * Export artifact bundle implementation for Android
 * TODO: Implement using Android's SAF (Storage Access Framework)
 */
actual fun exportArtifactBundle(
    bundle: cc.unitmesh.agent.artifact.ArtifactBundle,
    onNotification: (String, String) -> Unit
) {
    // TODO: Implement Android bundle export using SAF
    onNotification("info", "Bundle export not yet implemented for Android")
}
