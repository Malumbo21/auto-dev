package cc.unitmesh.viewer.web

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cc.unitmesh.viewer.ViewerHost
import cc.unitmesh.viewer.ViewerRequest

/**
 * Create a WebViewerHost instance for JS platform
 */
@Composable
actual fun createWebViewerHost(): ViewerHost {
    console.log("[JS] createWebViewerHost() called - returning stub ViewerHost")
    return object : ViewerHost {
        override suspend fun showContent(request: ViewerRequest) {
            console.log("[JS ViewerHost] showContent called: type=${request.type}")
        }

        override suspend fun clearContent() {
            console.log("[JS ViewerHost] clearContent called")
        }

        override fun isReady(): Boolean = true

        override fun onReady(callback: () -> Unit) {
            callback()
        }

        override fun getCurrentRequest(): ViewerRequest? = null
    }
}

/**
 * Composable WebView for JS platform (stub implementation)
 */
@Composable
actual fun ViewerWebView(
    initialRequest: ViewerRequest?,
    modifier: Modifier,
    onHostCreated: (ViewerHost) -> Unit
) {
    console.log("[JS] ViewerWebView() called - rendering stub message")
    Box(modifier) {
        Text("WebView not supported in JS/CLI environment")
    }
}

actual fun getViewerHtml(): String = "<html><body>Viewer not supported on JS</body></html>"
