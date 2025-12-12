package cc.unitmesh.viewer.web.webedit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * WebEdit WebView component for browsing and selecting DOM elements
 * 
 * This is the common interface - platform-specific implementations provide the actual WebView.
 * 
 * @param bridge The WebEditBridge for communication
 * @param modifier The modifier for layout
 * @param onPageLoaded Callback when page finishes loading
 * @param onElementSelected Callback when an element is selected
 * @param onDOMTreeUpdated Callback when DOM tree is updated
 */
@Composable
expect fun WebEditView(
    bridge: WebEditBridge,
    modifier: Modifier = Modifier,
    onPageLoaded: (url: String, title: String) -> Unit = { _, _ -> },
    onElementSelected: (DOMElement) -> Unit = {},
    onDOMTreeUpdated: (DOMElement) -> Unit = {}
)

