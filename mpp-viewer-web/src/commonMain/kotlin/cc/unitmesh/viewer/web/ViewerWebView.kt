package cc.unitmesh.viewer.web

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cc.unitmesh.viewer.ViewerHost
import cc.unitmesh.viewer.ViewerRequest

/**
 * Create a WebViewerHost instance
 */
@Composable
expect fun createWebViewerHost(): ViewerHost

/**
 * Composable WebView for displaying content
 *
 * @param initialRequest Optional initial request to display when ready
 * @param modifier The modifier for layout
 * @param onHostCreated Callback when the viewer host is created
 */
@Composable
expect fun ViewerWebView(
    initialRequest: ViewerRequest? = null,
    modifier: Modifier = Modifier,
    onHostCreated: (ViewerHost) -> Unit = {}
)

expect fun getViewerHtml(): String

