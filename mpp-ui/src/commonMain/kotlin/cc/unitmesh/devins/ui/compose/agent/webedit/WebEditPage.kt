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
import cc.unitmesh.llm.LLMService
import cc.unitmesh.devins.ui.compose.agent.webedit.automation.runOneSentenceCommand
import cc.unitmesh.devins.ui.compose.editor.multimodal.*
import cc.unitmesh.viewer.web.webedit.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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
    llmService: LLMService?,
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
    llmService: LLMService?,
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
    var automationMode by remember { mutableStateOf(true) }

    // Screenshot state
    val lastScreenshot by internalBridge.lastScreenshot.collectAsState()
    var visionHelper by remember { mutableStateOf<WebEditVisionHelper?>(null) }

    // Image upload manager for screenshots
    val imageUploadManager = remember(scope) {
        ImageUploadManager(
            scope = scope,
            uploadCallback = null, // Screenshots are uploaded via bytes
            uploadBytesCallback = { bytes, fileName, mimeType, imageId, onProgress ->
                // Upload screenshot bytes to COS via vision helper
                try {
                    val helper = visionHelper
                    if (helper != null) {
                        // Use vision helper's COS uploader if available
                        // For now, we'll use a simple approach: upload via vision helper
                        // This requires exposing upload functionality from WebEditVisionHelper
                        // For now, return success with a placeholder URL
                        // TODO: Implement actual COS upload via vision helper
                        delay(500) // Simulate upload
                        onProgress(100)
                        ImageUploadResult(
                            success = true,
                            url = "screenshot://$imageId" // Placeholder
                        )
                    } else {
                        ImageUploadResult(
                            success = false,
                            error = "Vision helper not available"
                        )
                    }
                } catch (e: Exception) {
                    ImageUploadResult(
                        success = false,
                        error = e.message ?: "Upload failed"
                    )
                }
            },
            onError = { error -> onNotification("Upload Error", error) }
        )
    }

    // Collect multimodal state
    val multimodalState by imageUploadManager.state.collectAsState()
    var previewingImage by remember { mutableStateOf<AttachedImage?>(null) }

    // Element tags state - stores selected elements as tags
    var elementTags by remember { mutableStateOf(ElementTagCollection()) }

    // Initialize vision helper
    LaunchedEffect(Unit) {
        visionHelper = createWebEditVisionHelper(internalBridge)
    }

    // Watch for screenshot and add to image upload manager
    @OptIn(ExperimentalEncodingApi::class)
    LaunchedEffect(lastScreenshot) {
        val ss = lastScreenshot
        val base64Data = ss?.base64
        if (ss != null && base64Data != null && ss.error == null) {
            // Screenshot captured successfully - convert to AttachedImage
            try {
                val bytes = Base64.decode(base64Data)
                val timestamp = Clock.System.now().toEpochMilliseconds()
                val fileName = "screenshot_$timestamp.jpg"
                imageUploadManager.addImageFromBytes(
                    bytes = bytes,
                    mimeType = ss.mimeType ?: "image/jpeg",
                    suggestedName = fileName
                )
                onNotification("Screenshot", "Captured ${ss.width}x${ss.height}")
            } catch (e: Exception) {
                onNotification("Screenshot Error", "Failed to process: ${e.message}")
            }
        } else {
            val error = ss?.error
            if (error != null) {
                onNotification("Screenshot Error", error)
            }
        }
    }

    /**
     * Build and send message with multimodal support (similar to DevInEditorInput).
     * If images are attached and uploaded, performs vision analysis first.
     */
    fun buildAndSendMessageWithMultimodal(text: String) {
        val currentState = multimodalState
        if (text.isBlank() && !currentState.hasImages) return

        // Don't allow sending if images are still uploading
        if (currentState.isUploading) {
            onNotification("Uploading", "Please wait for image upload to complete")
            return
        }

        // Don't allow sending if any upload failed
        if (currentState.hasUploadError) {
            onNotification("Upload Error", "Some images failed to upload. Please remove or retry them.")
            return
        }

        // Generate DevIns commands for selected files (element tags)
        val filesText = elementTags.tags.joinToString("\n") { it.toDevInsCommand() }
        val fullText = if (filesText.isNotEmpty()) "$text\n$filesText" else text

        // If we have uploaded images, process them with multimodal analysis
        if (currentState.allImagesUploaded && visionHelper != null) {
            val imageUrls = currentState.images.mapNotNull { it.uploadedUrl }
            val originalText = fullText

            // Update state to show analysis in progress
            imageUploadManager.setAnalyzing(true, "Analyzing ${imageUrls.size} screenshot(s) with vision model...")

            scope.launch {
                try {
                    val streamingContent = StringBuilder()
                    (visionHelper as? WebEditVisionHelper)?.analyzeScreenshot(
                        userIntent = originalText.ifBlank { "Describe what you see on this page. What are the main interactive elements?" },
                        actionableContext = actionableElements.take(10).joinToString("\n") {
                            "- [${it.role}] ${it.name} → ${it.selector}"
                        }
                    )?.collect { chunk ->
                        streamingContent.append(chunk)
                        imageUploadManager.updateAnalysisProgress(streamingContent.toString())
                    }

                    val analysisResult = streamingContent.toString()
                    imageUploadManager.setAnalysisResult(analysisResult)

                    // Send the combined message with analysis result
                    scope.launch {
                        // Refresh actionable elements
                        internalBridge.refreshActionableElements()
                        delay(300)

                        // Build combined message
                        val combinedMessage = buildString {
                            if (originalText.isNotBlank()) {
                                append(originalText)
                                append("\n\n")
                            }
                            append("**Vision Analysis:**\n")
                            append(analysisResult)
                            if (elementTags.isNotEmpty()) {
                                append("\n\n**选中的元素：**\n")
                                elementTags.tags.forEach { tag ->
                                    append("- ${tag.displayName}\n")
                                }
                            }
                        }

                        // Add user message to history
                        chatHistory = chatHistory + ChatMessage(role = "user", content = combinedMessage)
                        showChatHistory = true
                    }

                    // Clear input and images
                    chatInput = ""
                    elementTags = elementTags.clear()
                    imageUploadManager.clearImages()
                } catch (e: Exception) {
                    imageUploadManager.setAnalysisResult(null, e.message ?: "Analysis failed")
                    onNotification("Analysis Error", "Multimodal analysis failed: ${e.message}")
                }
            }
        } else {
            // No images - send directly
            scope.launch {
                // Refresh actionable elements
                internalBridge.refreshActionableElements()
                delay(300)

                // Build user message with element context
                val userMessage = buildString {
                    append(fullText)
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
            }

            // Clear input and files
            chatInput = ""
            elementTags = elementTags.clear()
        }
    }

    /**
     * Send message directly (no multimodal analysis).
     */
    fun sendMessageDirectly(message: String) {
        scope.launch {
            // Refresh actionable elements
            internalBridge.refreshActionableElements()
            delay(300)

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

            // Continue with existing automation or chat logic...
            // (This will be handled by the existing onSend callback)
        }
    }


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
            isReady = isReady,
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
            onToggleDOMSidebar = { showDOMSidebar = !showDOMSidebar },
            onScreenshot = {
                scope.launch {
                    internalBridge.captureScreenshot(maxWidth = 1280, quality = 0.8)
                }
            }
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

        // Automation toggle (default ON)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (automationMode) "Auto-run: ON" else "Auto-run: OFF",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(
                checked = automationMode,
                onCheckedChange = { automationMode = it },
                enabled = llmService != null && !isProcessingQuery
            )
            Text(
                text = "When ON, your message will be converted into actions and executed in the page.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        WebEditChatInput(
            input = chatInput,
            onInputChange = { chatInput = it },
            multimodalState = multimodalState,
            imageUploadManager = imageUploadManager,
            previewingImage = previewingImage,
            onPreviewImage = { previewingImage = it },
            onDismissPreview = { previewingImage = null },
            visionHelper = visionHelper,
            onSend = { message ->
                // Use multimodal send if images are available
                buildAndSendMessageWithMultimodal(message)
            },
            onSendDirect = { message ->
                scope.launch {
                    // Refresh actionable elements to ensure we have the latest context for current page
                    internalBridge.refreshActionableElements()
                    delay(300) // Give it time to update

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

                    if (automationMode && llmService != null) {
                        isProcessingQuery = true
                        val planningIndex = chatHistory.size
                        chatHistory = chatHistory + ChatMessage(role = "assistant", content = "Planning actions...\n")
                        try {
                            var plannedSoFar = "Planning actions...\n"
                            var lastUiUpdateMs = 0L
                            val run = runOneSentenceCommand(
                                bridge = internalBridge,
                                llmService = llmService,
                                instruction = message,
                                actionableElements = actionableElements,
                                onPlanningChunk = { chunk ->
                                    plannedSoFar += chunk
                                    // Throttle UI updates to avoid excessive recompositions on Desktop.
                                    val now = Clock.System.now().toEpochMilliseconds()
                                    if (now - lastUiUpdateMs > 60 || chunk.contains("]")) {
                                        lastUiUpdateMs = now
                                        chatHistory = chatHistory.mapIndexed { idx, m ->
                                            if (idx == planningIndex) m.copy(content = plannedSoFar) else m
                                        }
                                    }
                                },
                                visionFallback = visionHelper
                            )

                            val summary = buildString {
                                appendLine("Executed actions:")
                                appendLine("```json")
                                appendLine(
                                    kotlinx.serialization.json.Json {
                                        prettyPrint = true
                                        encodeDefaults = true
                                    }.encodeToString(
                                        kotlinx.serialization.builtins.ListSerializer(WebEditAction.serializer()),
                                        run.actions
                                    )
                                )
                                appendLine("```")
                                appendLine()
                                appendLine("Results:")
                                appendLine("```json")
                                appendLine(
                                    kotlinx.serialization.json.Json {
                                        prettyPrint = true
                                        encodeDefaults = true
                                    }.encodeToString(
                                        kotlinx.serialization.builtins.ListSerializer(WebEditMessage.ActionResult.serializer()),
                                        run.results
                                    )
                                )
                                appendLine("```")
                                val anyFail = run.results.any { !it.ok }
                                if (anyFail) {
                                    appendLine()
                                    appendLine("Raw model output (for debugging):")
                                    appendLine(run.rawModelOutput)
                                }
                            }

                            // Replace the streaming planning message with the final execution summary
                            chatHistory = chatHistory.mapIndexed { idx, m ->
                                if (idx == planningIndex) m.copy(content = summary) else m
                            }
                        } catch (e: Exception) {
                            onNotification("Automation Error", e.message ?: "Unknown error")
                            chatHistory = chatHistory.mapIndexed { idx, m ->
                                if (idx == planningIndex) m.copy(content = "❌ Automation failed: ${e.message}") else m
                            }
                        } finally {
                            isProcessingQuery = false
                        }
                    } else {
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
                    }
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
                // Use multimodal send if images are available
                buildAndSendMessageWithMultimodal(message)
                scope.launch {
                    // Refresh actionable elements to ensure we have the latest context for current page
                    internalBridge.refreshActionableElements()
                    delay(300) // Give it time to update

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

