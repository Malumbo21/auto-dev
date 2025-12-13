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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Chat message for WebEdit Q&A
 */
data class ChatMessage(
    val role: String, // "user" or "assistant"
    val content: String
)

/**
 * Handle chat message with LLM integration
 */
private suspend fun handleChatMessage(
    message: String,
    llmService: KoogLLMService?,
    currentUrl: String,
    pageTitle: String,
    selectedElement: DOMElement?,
    onResponse: (String) -> Unit,
    onError: (String) -> Unit,
    onProcessingChange: (Boolean) -> Unit
) {
    if (llmService == null) {
        onError("LLM service is not available")
        return
    }

    onProcessingChange(true)

    try {
        // Build context prompt
        val contextBuilder = StringBuilder()
        contextBuilder.append("You are helping analyze a web page.\\n\\n")
        contextBuilder.append("Current page: $pageTitle ($currentUrl)\\n\\n")

        if (selectedElement != null) {
            contextBuilder.append("Selected element:\\n")
            contextBuilder.append("- Tag: ${selectedElement.tagName}\\n")
            contextBuilder.append("- Selector: ${selectedElement.selector}\\n")
            if (selectedElement.textContent?.isNotEmpty() == true) {
                contextBuilder.append("- Text: ${selectedElement.textContent}\\n")
            }
            if (selectedElement.attributes.isNotEmpty()) {
                contextBuilder.append("- Attributes: ${selectedElement.attributes}\\n")
            }
            contextBuilder.append("\\n")
        }

        contextBuilder.append("User question: $message\\n\\n")
        contextBuilder.append("Please provide a helpful and concise answer.")

        // Call LLM service using sendPrompt
        val prompt = contextBuilder.toString()
        val response = llmService.sendPrompt(prompt)

        onResponse(response)
    } catch (e: Exception) {
        onError("Failed to process query: ${e.message}")
    } finally {
        onProcessingChange(false)
    }
}

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
    val errorMessage by bridge.errorMessage.collectAsState()
    val isReady by bridge.isReady.collectAsState()

    // Local UI state
    var inputUrl by remember { mutableStateOf("https://ide.unitmesh.cc") }
    var showDOMSidebar by remember { mutableStateOf(true) }
    var chatInput by remember { mutableStateOf("") }
    var isProcessingQuery by remember { mutableStateOf(false) }
    var chatHistory by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }

    // Auto-load initial URL
    LaunchedEffect(Unit) {
        if (currentUrl.isEmpty() && inputUrl.isNotEmpty()) {
            scope.launch {
                delay(1000)
                bridge.navigateTo(inputUrl)
            }
        }
    }

    // Request initial DOM tree once the bridge is ready for the loaded page.
    LaunchedEffect(isReady, currentUrl) {
        if (!isReady) return@LaunchedEffect
        if (currentUrl.isBlank() || currentUrl == "about:blank") return@LaunchedEffect
        delay(400)
        bridge.refreshDOMTree()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar with URL input and controls
        WebEditToolbar(
            currentUrl = currentUrl,
            inputUrl = inputUrl,
            isLoading = isLoading,
            isSelectionMode = isSelectionMode,
            showDOMSidebar = showDOMSidebar,
            onUrlChange = { inputUrl = it },
            onGoBack = {
                scope.launch { bridge.goBack() }
            },
            onGoForward = {
                scope.launch { bridge.goForward() }
            },
            onNavigate = { url ->
                val trimmed = url.trim()
                if (trimmed.isEmpty()) return@WebEditToolbar

                // Validate URL format
                val normalizedUrl = when {
                    trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                    trimmed.matches(Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$")) -> "https://$trimmed"
                    else -> {
                        onNotification("Invalid URL", "Please enter a valid URL")
                        return@WebEditToolbar
                    }
                }

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

                // Error message display
                if (errorMessage != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(8.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
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
                    handleChatMessage(
                        message = message,
                        llmService = llmService,
                        currentUrl = currentUrl,
                        pageTitle = pageTitle,
                        selectedElement = selectedElement,
                        onResponse = { response ->
                            chatHistory = chatHistory + ChatMessage(role = "assistant", content = response)
                        },
                        onError = { error ->
                            onNotification("Error", error)
                        },
                        onProcessingChange = { processing ->
                            isProcessingQuery = processing
                        }
                    )
                    chatInput = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isProcessingQuery && llmService != null,
            isProcessing = isProcessingQuery
        )
    }
}

