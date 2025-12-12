package cc.unitmesh.devins.ui.compose.agent.webedit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.llm.KoogLLMService
import cc.unitmesh.viewer.web.webedit.*
import kotlinx.coroutines.launch

/**
 * WebEdit Page - Browse, select DOM elements, and interact with web pages
 *
 * Layout:
 * - Top: URL bar with navigation controls
 * - Center: WebView with selection overlay
 * - Right: DOM tree sidebar (toggleable)
 * - Bottom: Chat/Q&A input area
 */
@Composable
fun WebEditPage(
    llmService: KoogLLMService?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNotification: (String, String) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()

    // Create the WebEdit bridge
    val bridge = remember { createWebEditBridge() }

    // Collect state from bridge
    val currentUrl by bridge.currentUrl.collectAsState()
    val pageTitle by bridge.pageTitle.collectAsState()
    val isLoading by bridge.isLoading.collectAsState()
    val isSelectionMode by bridge.isSelectionMode.collectAsState()
    val selectedElement by bridge.selectedElement.collectAsState()
    val domTree by bridge.domTree.collectAsState()

    // Local UI state
    var inputUrl by remember { mutableStateOf("") }
    var showDOMSidebar by remember { mutableStateOf(true) }
    var chatInput by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar with URL input and controls
        WebEditToolbar(
            currentUrl = currentUrl,
            inputUrl = inputUrl,
            isLoading = isLoading,
            isSelectionMode = isSelectionMode,
            showDOMSidebar = showDOMSidebar,
            onUrlChange = { inputUrl = it },
            onNavigate = { url ->
                val normalizedUrl = if (!url.startsWith("https://") && !url.startsWith("https://")) {
                    "https://$url"
                } else url
                inputUrl = normalizedUrl
                scope.launch {
                    bridge.navigateTo(normalizedUrl)
                }
            },
            onBack = onBack,
            onReload = {
                scope.launch { bridge.reload() }
            },
            onToggleSelectionMode = {
                scope.launch { bridge.setSelectionMode(!isSelectionMode) }
            },
            onToggleDOMSidebar = { showDOMSidebar = !showDOMSidebar }
        )

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                WebEditView(
                    bridge = bridge,
                    modifier = Modifier.fillMaxSize(),
                    onPageLoaded = { url, title ->
                        inputUrl = url
                        println("[WebEditPage] Page loaded: $title ($url)")
                    },
                    onElementSelected = { element ->
                        println("[WebEditPage] Element selected: ${element.getDisplayName()}")
                        onNotification("Element Selected", element.getDisplayName())
                    },
                    onDOMTreeUpdated = { root ->
                        println("[WebEditPage] DOM tree updated with ${root.children.size} children")
                    }
                )

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    )
                }

                if (isSelectionMode) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Selection Mode",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                selectedElement?.let { element ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Selected: ${element.getDisplayName()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (showDOMSidebar) {
                DOMTreeSidebar(
                    domTree = domTree,
                    selectedElement = selectedElement,
                    onElementClick = { selector ->
                        scope.launch {
                            bridge.highlightElement(selector)
                            bridge.scrollToElement(selector)
                        }
                    },
                    onElementHover = { selector ->
                        if (selector != null) {
                            scope.launch { bridge.highlightElement(selector) }
                        } else {
                            scope.launch { bridge.clearHighlights() }
                        }
                    },
                    modifier = Modifier.width(280.dp).fillMaxHeight()
                )
            }
        }

        WebEditChatInput(
            input = chatInput,
            onInputChange = { chatInput = it },
            onSend = { message ->
                scope.launch {
                    // TODO: Handle chat message with LLM
                    // Could use selectedElement context for more targeted Q&A
                    chatInput = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

