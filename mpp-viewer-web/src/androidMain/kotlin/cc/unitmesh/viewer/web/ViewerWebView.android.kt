package cc.unitmesh.viewer.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import cc.unitmesh.viewer.ViewerHost
import cc.unitmesh.viewer.ViewerRequest
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import com.multiplatform.webview.web.rememberWebViewStateWithHTMLData

/**
 * Android implementation: Load viewer HTML from resources
 */
actual fun getViewerHtml(): String {
    return try {
        val resource = object {}.javaClass.classLoader?.getResource("viewer.html")
        
        if (resource != null) {
            val html = resource.readText()
            html
        } else {
            getFallbackHtml()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        getFallbackHtml()
    }
}

private fun getFallbackHtml(): String {
    return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AutoDev Viewer (Fallback)</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
            background: #1e1e1e;
            color: #d4d4d4;
            overflow: hidden;
        }
        #container {
            width: 100vw;
            height: 100vh;
            display: flex;
            flex-direction: column;
        }
    </style>
</head>
<body>
    <div id="container">
        <div style="display: flex; align-items: center; justify-content: center; height: 100%;">
            <div style="text-align: center;">
                <h2>AutoDev Viewer</h2>
                <p>Failed to load viewer.html from resources</p>
            </div>
        </div>
    </div>
</body>
</html>
    """.trimIndent()
}

/**
 * Create a WebViewerHost instance
 */
@Composable
actual fun createWebViewerHost(): ViewerHost {
    val webViewNavigator = rememberWebViewNavigator()
    return remember { WebViewerHost(webViewNavigator) }
}

/**
 * Composable WebView for displaying content
 *
 * @param initialRequest Optional initial request to display when ready
 * @param modifier The modifier for layout
 * @param onHostCreated Callback when the viewer host is created
 */
@Composable
actual fun ViewerWebView(
    initialRequest: ViewerRequest?,
    modifier: Modifier,
    onHostCreated: (ViewerHost) -> Unit
) {
    val webViewState = rememberWebViewStateWithHTMLData(
        data = getViewerHtml()
    )

    val webViewNavigator = rememberWebViewNavigator()
    val viewerHost = remember {
        WebViewerHost(webViewNavigator).also {
            onHostCreated(it)
        }
    }

    LaunchedEffect(webViewState.isLoading) {
        if (!webViewState.isLoading && webViewState.loadingState is com.multiplatform.webview.web.LoadingState.Finished) {
            viewerHost.markReady()
            initialRequest?.let { request ->
                viewerHost.showContent(request)
            } ?: println("[ViewerWebView] No initial request to show")
        }
    }

    WebView(
        state = webViewState,
        navigator = webViewNavigator,
        modifier = modifier.fillMaxSize(),
        captureBackPresses = false
    )
}
