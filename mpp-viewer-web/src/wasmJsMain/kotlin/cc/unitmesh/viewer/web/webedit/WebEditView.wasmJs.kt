package cc.unitmesh.viewer.web.webedit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * WASM implementation of WebEditView
 * 
 * Note: WebView is not available in WASM, so we show a placeholder message.
 * In a real implementation, you might use an iframe or redirect to a browser.
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
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "WebView is not available in WASM. Please use the desktop version.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

