package cc.unitmesh.devins.ui.compose.agent.webedit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.agent.CodingAgent
import cc.unitmesh.agent.AgentTask
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
    elementTags: ElementTagCollection,
    actionableElements: List<AccessibilityNode>,
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
        val prompt = buildWebEditLLMPrompt(
            message = message,
            currentUrl = currentUrl,
            pageTitle = pageTitle,
            selectedElement = selectedElement,
            elementTags = elementTags,
            actionableElements = actionableElements
        )
        val response = llmService.sendPrompt(prompt)

        onResponse(response)
    } catch (e: Exception) {
        onError("Failed to process query: ${e.message}")
    } finally {
        onProcessingChange(false)
    }
}

/**
 * Handle chat message with CodingAgent for source code analysis and modification.
 * This provides deeper integration with the codebase to locate and modify source files.
 */
private suspend fun handleChatWithCodingAgent(
    message: String,
    elementTags: ElementTagCollection,
    codingAgent: CodingAgent?,
    projectPath: String,
    currentUrl: String,
    pageTitle: String,
    onResponse: (String) -> Unit,
    onError: (String) -> Unit,
    onProcessingChange: (Boolean) -> Unit
) {
    if (codingAgent == null) {
        onError("CodingAgent is not available")
        return
    }

    onProcessingChange(true)

    try {
        // Build a comprehensive requirement for the CodingAgent
        val requirementBuilder = StringBuilder()
        requirementBuilder.appendLine("## Web Element Analysis Request")
        requirementBuilder.appendLine()
        requirementBuilder.appendLine("**Page Context:**")
        requirementBuilder.appendLine("- URL: $currentUrl")
        requirementBuilder.appendLine("- Title: $pageTitle")
        requirementBuilder.appendLine()

        // Add element context
        if (elementTags.isNotEmpty()) {
            requirementBuilder.appendLine("**Selected DOM Elements:**")
            requirementBuilder.appendLine()
            elementTags.tags.forEach { tag ->
                requirementBuilder.appendLine(tag.toSourceMappingPrompt())
                requirementBuilder.appendLine()
            }
        }

        // Add user request
        requirementBuilder.appendLine("**User Request:**")
        requirementBuilder.appendLine(message)
        requirementBuilder.appendLine()
        requirementBuilder.appendLine("**Instructions:**")
        requirementBuilder.appendLine("1. Analyze the DOM element information provided")
        requirementBuilder.appendLine("2. Search the project for corresponding source files")
        requirementBuilder.appendLine("3. Identify components, templates, or code that renders these elements")
        requirementBuilder.appendLine("4. If modifications are requested, suggest or apply the changes")

        val task = AgentTask(
            requirement = requirementBuilder.toString(),
            projectPath = projectPath
        )

        val result = codingAgent.executeTask(task)

        if (result.success) {
            onResponse(result.message)
        } else {
            onError("Agent task failed: ${result.message}")
        }
    } catch (e: Exception) {
        onError("Failed to process with CodingAgent: ${e.message}")
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
 * - Bottom: Chat/Q&A input area with element tags
 *
 * Features:
 * - DOM element inspection and selection
 * - Automatic tag creation for selected elements
 * - LLM-powered analysis with element context
 * - CodingAgent integration for source code mapping
 */
@Composable
fun WebEditPage(
    llmService: KoogLLMService?,
    bridge: WebEditBridge? = null,
    modifier: Modifier = Modifier,
    codingAgent: CodingAgent? = null,
    projectPath: String = "",
    onBack: () -> Unit = {},
    onNotification: (String, String) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()

    // Create the WebEdit bridge
    val internalBridge = remember { bridge ?: createWebEditBridge() }

    // Collect state from bridge
    val currentUrl by internalBridge.currentUrl.collectAsState()
    val pageTitle by internalBridge.pageTitle.collectAsState()
    val isLoading by internalBridge.isLoading.collectAsState()
    val isSelectionMode by internalBridge.isSelectionMode.collectAsState()
    val selectedElement by internalBridge.selectedElement.collectAsState()
    val domTree by internalBridge.domTree.collectAsState()
    val actionableElements by internalBridge.actionableElements.collectAsState()
    val errorMessage by internalBridge.errorMessage.collectAsState()
    val isReady by internalBridge.isReady.collectAsState()

    // Local UI state
    var inputUrl by remember { mutableStateOf("https://ide.unitmesh.cc") }
    var showDOMSidebar by remember { mutableStateOf(true) }
    var chatInput by remember { mutableStateOf("") }
    var isProcessingQuery by remember { mutableStateOf(false) }
    var chatHistory by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var showChatHistory by remember { mutableStateOf(false) }
    
    // Element tags state - stores selected elements as tags
    var elementTags by remember { mutableStateOf(ElementTagCollection()) }

    // Auto-add selected element as a tag when element is selected
    LaunchedEffect(selectedElement) {
        selectedElement?.let { element ->
            val tag = ElementTag.fromDOMElement(element)
            elementTags = elementTags.add(tag)
        }
    }

    // Auto-load initial URL
    LaunchedEffect(Unit) {
        if (currentUrl.isEmpty() && inputUrl.isNotEmpty()) {
            scope.launch {
                delay(1000)
                internalBridge.navigateTo(inputUrl)
            }
        }
    }

    // Request initial DOM tree once the bridge is ready for the loaded page.
    LaunchedEffect(isReady, currentUrl) {
        if (!isReady) return@LaunchedEffect
        if (currentUrl.isBlank() || currentUrl == "about:blank") return@LaunchedEffect
        delay(400)
        internalBridge.refreshDOMTree()
        // Provide LLM-friendly "eyes"
        internalBridge.refreshActionableElements()
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
                scope.launch { internalBridge.goBack() }
            },
            onGoForward = {
                scope.launch { internalBridge.goForward() }
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
                    internalBridge.navigateTo(normalizedUrl)
                }
            },
            onBack = onBack,
            onReload = {
                scope.launch { internalBridge.reload() }
            },
            onToggleSelectionMode = {
                scope.launch { internalBridge.setSelectionMode(!isSelectionMode) }
            },
            onToggleDOMSidebar = { showDOMSidebar = !showDOMSidebar }
        )

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Chat history panel (left side, toggleable)
            if (showChatHistory && chatHistory.isNotEmpty()) {
                WebEditChatHistory(
                    messages = chatHistory,
                    modifier = Modifier.width(400.dp).fillMaxHeight(),
                    onClose = { showChatHistory = false }
                )
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                WebEditView(
                    bridge = internalBridge,
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
                            internalBridge.highlightElement(selector)
                            internalBridge.scrollToElement(selector)
                        }
                    },
                    onElementHover = { selector ->
                        if (selector != null) {
                            scope.launch { internalBridge.highlightElement(selector) }
                        } else {
                            scope.launch { internalBridge.clearHighlights() }
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
                    // Build user message with element context
                    val userMessage = buildString {
                        append(message)
                        if (elementTags.isNotEmpty()) {
                            append("\n\n**选中的元素：**\n")
                            elementTags.tags.forEach { tag ->
                                append("- ${tag.displayName}\n")
                            }
                        }
                    }
                    
                    // Add user message to history
                    chatHistory = chatHistory + ChatMessage(role = "user", content = userMessage)
                    showChatHistory = true
                    
                    handleChatMessage(
                        message = message,
                        llmService = llmService,
                        currentUrl = currentUrl,
                        pageTitle = pageTitle,
                        selectedElement = selectedElement,
                        elementTags = elementTags,
                        actionableElements = actionableElements,
                        onResponse = { response ->
                            chatHistory = chatHistory + ChatMessage(role = "assistant", content = response)
                        },
                        onError = { error ->
                            onNotification("Error", error)
                            chatHistory = chatHistory + ChatMessage(role = "assistant", content = "❌ Error: $error")
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
            isProcessing = isProcessingQuery,
            elementTags = elementTags,
            onRemoveTag = { tagId ->
                elementTags = elementTags.remove(tagId)
            },
            onClearTags = {
                elementTags = elementTags.clear()
            },
            onSendWithContext = { message, tags ->
                scope.launch {
                    // Build user message with element context
                    val userMessage = buildString {
                        append(message)
                        if (tags.isNotEmpty()) {
                            append("\n\n**选中的元素：**\n")
                            tags.tags.forEach { tag ->
                                append("- ${tag.displayName}")
                                val elementId = tag.attributes["id"]
                                val className = tag.attributes["class"]
                                if (elementId != null) append(" #$elementId")
                                if (className != null) append(" .$className")
                                append("\n")
                            }
                        }
                    }
                    
                    // Add user message to history
                    chatHistory = chatHistory + ChatMessage(role = "user", content = userMessage)
                    showChatHistory = true
                    
                    // Prefer CodingAgent for source code mapping if available
                    if (codingAgent != null && projectPath.isNotEmpty()) {
                        handleChatWithCodingAgent(
                            message = message,
                            elementTags = tags,
                            codingAgent = codingAgent,
                            projectPath = projectPath,
                            currentUrl = currentUrl,
                            pageTitle = pageTitle,
                            onResponse = { response ->
                                chatHistory = chatHistory + ChatMessage(role = "assistant", content = response)
                            },
                            onError = { error ->
                                onNotification("Error", error)
                                chatHistory = chatHistory + ChatMessage(role = "assistant", content = "❌ Error: $error")
                            },
                            onProcessingChange = { processing ->
                                isProcessingQuery = processing
                            }
                        )
                    } else {
                        // Fallback to simple LLM chat with element context
                        handleChatMessage(
                            message = message,
                            llmService = llmService,
                            currentUrl = currentUrl,
                            pageTitle = pageTitle,
                            selectedElement = selectedElement,
                            elementTags = tags,
                            actionableElements = actionableElements,
                            onResponse = { response ->
                                chatHistory = chatHistory + ChatMessage(role = "assistant", content = response)
                            },
                            onError = { error ->
                                onNotification("Error", error)
                                chatHistory = chatHistory + ChatMessage(role = "assistant", content = "❌ Error: $error")
                            },
                            onProcessingChange = { processing ->
                                isProcessingQuery = processing
                            }
                        )
                    }
                    chatInput = ""
                    // Clear tags after sending with context
                    elementTags = elementTags.clear()
                }
            },
            onViewElementDetails = { tag ->
                // Add element details to chat history
                chatHistory = chatHistory + ChatMessage(
                    role = "assistant",
                    content = tag.toDetailedMarkdown()
                )
                showChatHistory = true
            }
        )
    }
}

