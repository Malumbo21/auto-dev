package cc.unitmesh.viewer.web.webedit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * JS stub implementation of WebEditView
 * WebView is not supported in JS/Node.js CLI environment
 */
@Composable
actual fun WebEditView(
    bridge: WebEditBridge,
    modifier: Modifier,
    onPageLoaded: (url: String, title: String) -> Unit,
    onElementSelected: (DOMElement) -> Unit,
    onDOMTreeUpdated: (DOMElement) -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("WebView not supported in JS/CLI environment")
    }
}
