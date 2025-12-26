package cc.unitmesh.devins.ui.compose.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.agent.Platform
import cc.unitmesh.agent.mcp.McpClientManager
import cc.unitmesh.agent.mcp.McpConfig
import cc.unitmesh.agent.mcp.McpServerConfig
import cc.unitmesh.devins.completion.CompletionItem
import cc.unitmesh.devins.completion.CompletionManager
import cc.unitmesh.devins.completion.CompletionTriggerType
import cc.unitmesh.devins.editor.EditorCallbacks
import cc.unitmesh.devins.editor.FileContext
import cc.unitmesh.devins.ui.compose.config.ToolConfigDialog
import cc.unitmesh.devins.ui.compose.editor.changes.FileChangeSummary
import cc.unitmesh.devins.ui.compose.editor.completion.CompletionPopup
import cc.unitmesh.devins.ui.compose.editor.plan.PlanSummaryBar
import cc.unitmesh.devins.ui.compose.editor.completion.CompletionTrigger
import cc.unitmesh.devins.ui.compose.editor.context.FileSearchProvider
import cc.unitmesh.devins.ui.compose.editor.context.SelectedFileItem
import cc.unitmesh.devins.ui.compose.editor.context.TopToolbar
import cc.unitmesh.devins.ui.compose.editor.context.WorkspaceFileSearchProvider
import cc.unitmesh.devins.ui.compose.editor.highlighting.DevInSyntaxHighlighter
import cc.unitmesh.devins.ui.compose.editor.multimodal.AttachedImage
import cc.unitmesh.devins.ui.compose.editor.multimodal.ImageAttachmentBar
import cc.unitmesh.devins.ui.compose.editor.multimodal.ImageGenerationModel
import cc.unitmesh.devins.ui.compose.editor.multimodal.ImagePreviewDialog
import cc.unitmesh.devins.ui.compose.editor.multimodal.ImageUploadManager
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devins.ui.platform.createClipboardImageReader
import cc.unitmesh.devins.ui.platform.createFileChooser
import cc.unitmesh.devins.workspace.WorkspaceManager
import cc.unitmesh.llm.LLMService
import cc.unitmesh.llm.ModelConfig
import cc.unitmesh.llm.NamedModelConfig
import cc.unitmesh.llm.PromptEnhancer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * DevIn 编辑器输入组件
 * 完整的输入界面，包含底部工具栏
 *
 * Model configuration is now managed internally by ModelSelector via ConfigManager.
 *
 * Mobile-friendly improvements:
 * - No auto-focus on mobile (user taps to show keyboard)
 * - IME-aware keyboard handling (ImeAction.Send on mobile)
 * - Dismisses keyboard after sending message
 * - Better height constraints for touch ergonomics
 *
 * @param autoFocusOnMount Whether to automatically focus the input on mount (desktop only, default: false)
 * @param dismissKeyboardOnSend Whether to dismiss keyboard after sending message (default: true)
 */
@Composable
fun DevInEditorInput(
    initialText: String = "",
    placeholder: String = "Type your message...",
    callbacks: EditorCallbacks? = null,
    completionManager: CompletionManager? = null,
    isCompactMode: Boolean = false,
    isExecuting: Boolean = false,
    onStopClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    onModelConfigChange: (ModelConfig) -> Unit = {},
    dismissKeyboardOnSend: Boolean = true,
    renderer: cc.unitmesh.devins.ui.compose.agent.ComposeRenderer? = null,
    fileSearchProvider: FileSearchProvider? = null,
    // Multimodal callbacks
    /**
     * Called when an image needs to be uploaded to cloud storage.
     * @param imagePath Path to the local image file
     * @param imageId ID of the AttachedImage for status updates
     * @param onProgress Callback for upload progress updates (0-100)
     * @return ImageUploadResult with URL, sizes, and status
     */
    onImageUpload: (suspend (imagePath: String, imageId: String, onProgress: (Int) -> Unit) -> cc.unitmesh.devins.ui.compose.editor.multimodal.ImageUploadResult)? = null,
    /**
     * Called when an image from bytes (e.g., pasted from clipboard) needs to be uploaded.
     * @param imageBytes Image data as bytes
     * @param fileName Suggested file name
     * @param mimeType MIME type of the image
     * @param imageId ID of the AttachedImage for status updates
     * @param onProgress Callback for upload progress updates (0-100)
     * @return ImageUploadResult with URL, sizes, and status
     */
    onImageUploadBytes: (suspend (imageBytes: ByteArray, fileName: String, mimeType: String, imageId: String, onProgress: (Int) -> Unit) -> cc.unitmesh.devins.ui.compose.editor.multimodal.ImageUploadResult)? = null,
    /**
     * Called to perform vision analysis on uploaded images.
     * @param imageUrls List of uploaded image URLs
     * @param prompt User's prompt text
     * @param onChunk Callback for streaming response chunks (for real-time progress)
     * @return Analysis result string
     */
    onMultimodalAnalysis: (suspend (imageUrls: List<String>, prompt: String, onChunk: (String) -> Unit) -> String?)? = null,
    /**
     * Called when user selects a different vision model.
     * @param config The selected vision model configuration
     */
    onVisionModelChange: ((NamedModelConfig) -> Unit)? = null,
    /**
     * Called when user selects a different image generation model (GLM only).
     * @param model The selected image generation model
     */
    onImageGenerationModelChange: ((ImageGenerationModel) -> Unit)? = null
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(initialText)) }
    var highlightedText by remember { mutableStateOf(initialText) }

    // 补全相关状态
    var showCompletion by remember { mutableStateOf(false) }
    var completionItems by remember { mutableStateOf<List<CompletionItem>>(emptyList()) }
    var selectedCompletionIndex by remember { mutableStateOf(0) }
    var currentTriggerType by remember { mutableStateOf(CompletionTriggerType.NONE) }

    // 提示词增强相关状态
    var isEnhancing by remember { mutableStateOf(false) }
    var enhancer by remember { mutableStateOf<Any?>(null) }

    // Tool Configuration 对话框状态
    var showToolConfig by remember { mutableStateOf(false) }
    var mcpServers by remember { mutableStateOf<Map<String, McpServerConfig>>(emptyMap()) }
    val mcpClientManager = remember { McpClientManager() }

    // File context state (for TopToolbar)
    var selectedFiles by remember { mutableStateOf<List<SelectedFileItem>>(emptyList()) }
    var autoAddCurrentFile by remember { mutableStateOf(true) }

    // Image generation model state (GLM only)
    var imageGenerationModel by remember { mutableStateOf(ImageGenerationModel.COGVIEW_3_FLASH) }

    // Current model config state for detecting GLM provider
    var currentModelConfig by remember { mutableStateOf<ModelConfig?>(null) }

    // File search provider - use WorkspaceFileSearchProvider as default if not provided
    val effectiveSearchProvider = remember { fileSearchProvider ?: WorkspaceFileSearchProvider() }

    // Need scope early for ImageUploadManager
    val scope = rememberCoroutineScope()

    // Image upload manager - handles all image upload state and operations
    val imageUploadManager = remember(scope, onImageUpload, onImageUploadBytes) {
        ImageUploadManager(
            scope = scope,
            uploadCallback = onImageUpload,
            uploadBytesCallback = onImageUploadBytes,
            onError = { error -> renderer?.renderError(error) }
        )
    }

    // Collect multimodal state from the manager
    val multimodalState by imageUploadManager.state.collectAsState()
    var previewingImage by remember { mutableStateOf<AttachedImage?>(null) }

    // Helper function to convert SelectedFileItem to FileContext
    fun getFileContexts(): List<FileContext> = selectedFiles.map { file ->
        FileContext(
            name = file.name,
            path = file.path,
            relativePath = file.relativePath,
            isDirectory = file.isDirectory
        )
    }

    /**
     * Build and send message with file references (like IDEA's buildAndSendMessage).
     * Appends DevIns commands for selected files to the message.
     *
     * If images are attached and all uploaded, performs multimodal analysis first,
     * then sends the combined result.
     */
    fun buildAndSendMessage(text: String) {
        val currentState = multimodalState
        if (text.isBlank() && !currentState.hasImages) return

        // Don't allow sending if images are still uploading
        if (currentState.isUploading) {
            renderer?.renderError("Please wait for image upload to complete")
            return
        }

        // Don't allow sending if any upload failed
        if (currentState.hasUploadError) {
            renderer?.renderError("Some images failed to upload. Please remove or retry them.")
            return
        }

        // Generate DevIns commands for selected files
        val filesText = selectedFiles.joinToString("\n") { it.toDevInsCommand() }
        val fullText = if (filesText.isNotEmpty()) "$text\n$filesText" else text

        // If we have uploaded images, process them with multimodal analysis
        if (currentState.allImagesUploaded && onMultimodalAnalysis != null) {
            val imageUrls = currentState.images.mapNotNull { it.uploadedUrl }
            val originalText = fullText

            // Update state to show analysis in progress
            imageUploadManager.setAnalyzing(
                true,
                "Analyzing ${imageUrls.size} image(s) with ${currentState.visionModel}..."
            )

            // Show initial progress in renderer
            renderer?.renderInfo("🔍 Analyzing image(s) with ${currentState.visionModel}...")

            scope.launch {
                try {
                    val streamingContent = StringBuilder()
                    val analysisResult = onMultimodalAnalysis!!(imageUrls, originalText) { chunk ->
                        streamingContent.append(chunk)
                        // Also update the progress bar with a preview
//                        val preview = if (streamingContent.length > 100) {
//                            streamingContent.substring(0, 100) + "..."
//                        } else {
//                            streamingContent.toString()
//                        }
                        imageUploadManager.updateAnalysisProgress(streamingContent.toString())
                    }

                    imageUploadManager.setAnalysisResult(analysisResult)
                    callbacks?.onSubmitWithMultimodal(originalText, getFileContexts(), analysisResult)

                    textFieldValue = TextFieldValue("")
                    selectedFiles = emptyList()
                    imageUploadManager.clearImages()
                    showCompletion = false

                } catch (e: Exception) {
                    imageUploadManager.setAnalysisResult(null, e.message ?: "Analysis failed")
                    renderer?.renderError("Multimodal analysis failed: ${e.message}")
                }
            }
        } else {
            // No images - send directly
            callbacks?.onSubmit(fullText, getFileContexts())

            // Clear input and files
            textFieldValue = TextFieldValue("")
            selectedFiles = emptyList()
            showCompletion = false
        }
    }

    val highlighter = remember { DevInSyntaxHighlighter() }
    val manager = completionManager ?: remember { CompletionManager() }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val isMobile = Platform.isAndroid || Platform.isIOS
    val isAndroid = Platform.isAndroid

    // Style constants based on mode
    val inputShape = if (isAndroid && isCompactMode) 12.dp else 4.dp
    val inputFontSize = if (isAndroid && isCompactMode) 16.sp else 15.sp
    val inputLineHeight = if (isAndroid && isCompactMode) 24.sp else 22.sp
    val maxLines = if (isAndroid && isCompactMode) 5 else 8

    // iOS: Use smaller, fixed height to avoid keyboard issues
    // Android/Desktop: Use minHeight for touch targets + maxHeight for bounds
    val minHeight = when {
        Platform.isIOS -> 44.dp // iOS: standard touch target height
        isCompactMode && isAndroid -> 52.dp
        isCompactMode -> 56.dp
        else -> 80.dp
    }

    val maxHeight = when {
        Platform.isIOS && isCompactMode -> 80.dp // iOS compact: smaller max
        Platform.isIOS -> 100.dp // iOS: reduced max height
        isCompactMode && isAndroid -> 120.dp
        isCompactMode -> 96.dp
        else -> 160.dp
    }

    val padding = when {
        Platform.isIOS -> 10.dp // iOS: smaller padding
        isCompactMode -> 12.dp
        else -> 20.dp
    }

    // Initialize MCP client manager with config
    LaunchedEffect(Unit) {
        val configWrapper = ConfigManager.load()
        mcpServers = configWrapper.getMcpServers()
        if (mcpServers.isNotEmpty()) {
            mcpClientManager.initialize(McpConfig(mcpServers = mcpServers))
        }
    }

    var llmService by remember { mutableStateOf<LLMService?>(null) }

    LaunchedEffect(Unit) {
        try {
            val workspace = WorkspaceManager.currentWorkspace
            val projectPath = workspace?.rootPath
            if (projectPath != null) {
                val configWrapper = ConfigManager.load()
                val activeConfig = configWrapper.getActiveModelConfig()
                if (activeConfig != null && activeConfig.isValid()) {
                    llmService = LLMService.create(activeConfig)

                    // Use workspace file system
                    val fileSystem = workspace.fileSystem

                    // Create domain dict service
                    val domainDictService = cc.unitmesh.indexer.DomainDictService(fileSystem)

                    // Create prompt enhancer
                    enhancer = PromptEnhancer(llmService!!, fileSystem, domainDictService)
                }
            }
        } catch (e: Exception) {
            println("Failed to initialize prompt enhancer: ${e.message}")
        }
    }

    // 延迟高亮以避免频繁解析
    LaunchedEffect(textFieldValue.text) {
        delay(50) // 50ms 防抖
        highlightedText = textFieldValue.text
        callbacks?.onTextChanged(textFieldValue.text)
    }

    // 处理文本变化和补全触发
    fun handleTextChange(newValue: TextFieldValue) {
        val oldText = textFieldValue.text
        textFieldValue = newValue

        // 检查是否应该触发补全
        if (newValue.text.length > oldText.length) {
            val addedChar = newValue.text.getOrNull(newValue.selection.start - 1)
            if (addedChar != null && CompletionTrigger.shouldTrigger(addedChar)) {
                val triggerType = CompletionTrigger.getTriggerType(addedChar)
                val context =
                    CompletionTrigger.buildContext(
                        newValue.text,
                        newValue.selection.start,
                        triggerType
                    )

                if (context != null) {
                    currentTriggerType = triggerType

                    // 使用增强的过滤补全功能
                    completionItems = manager.getFilteredCompletions(context)

                    selectedCompletionIndex = 0
                    showCompletion = completionItems.isNotEmpty()
                    println("[Completion] Triggered: char='$addedChar', type=$triggerType, items=${completionItems.size}")
                }
            } else if (showCompletion) {
                // 更新补全列表
                val context =
                    CompletionTrigger.buildContext(
                        newValue.text,
                        newValue.selection.start,
                        currentTriggerType
                    )
                if (context != null) {
                    // 使用增强的过滤补全功能，支持边输入边补全
                    completionItems = manager.getFilteredCompletions(context)
                    selectedCompletionIndex = 0
                    if (completionItems.isEmpty()) {
                        showCompletion = false
                    }
                } else {
                    showCompletion = false
                }
            }
        } else {
            // 文本减少，关闭补全
            if (showCompletion) {
                val context =
                    CompletionTrigger.buildContext(
                        newValue.text,
                        newValue.selection.start,
                        currentTriggerType
                    )
                if (context == null) {
                    showCompletion = false
                }
            }
        }
    }

    fun applyCompletion(item: CompletionItem) {
        val insertHandler = item.insertHandler
        val result =
            if (insertHandler != null) {
                insertHandler(textFieldValue.text, textFieldValue.selection.start)
            } else {
                item.defaultInsert(textFieldValue.text, textFieldValue.selection.start)
            }

        textFieldValue =
            TextFieldValue(
                text = result.newText,
                selection = androidx.compose.ui.text.TextRange(result.newCursorPosition)
            )

        // Check if this is a built-in command that should be auto-executed
        val trimmedText = result.newText.trim()
        if (currentTriggerType == CompletionTriggerType.COMMAND &&
            (trimmedText == "/init" || trimmedText == "/clear" || trimmedText == "/help")
        ) {
            scope.launch {
                delay(100) // Small delay to ensure UI updates
                buildAndSendMessage(trimmedText)
            }
            return
        }

        if (result.shouldTriggerNextCompletion) {
            // 延迟触发下一个补全
            scope.launch {
                kotlinx.coroutines.delay(50)
                val lastChar = result.newText.getOrNull(result.newCursorPosition - 1)
                val triggerType =
                    when (lastChar) {
                        ':' -> CompletionTriggerType.COMMAND_VALUE
                        '/' -> CompletionTriggerType.COMMAND
                        else -> null
                    }

                if (triggerType != null) {
                    val context =
                        CompletionTrigger.buildContext(
                            result.newText,
                            result.newCursorPosition,
                            triggerType
                        )
                    if (context != null) {
                        currentTriggerType = triggerType
                        completionItems = manager.getFilteredCompletions(context)
                        selectedCompletionIndex = 0
                        showCompletion = completionItems.isNotEmpty()
                    }
                } else {
                    showCompletion = false
                }
            }
        } else {
            showCompletion = false
        }

        // Don't force focus on mobile after completion
        if (!isMobile) {
            focusRequester.requestFocus()
        }
    }

    // 增强当前输入的函数
    fun enhanceCurrentInput() {
        val currentEnhancer = enhancer
        if (currentEnhancer == null || textFieldValue.text.isBlank() || isEnhancing) {
            return
        }

        scope.launch {
            try {
                isEnhancing = true
                val enhanced = (currentEnhancer as PromptEnhancer).enhance(textFieldValue.text.trim(), "zh")

                if (enhanced.isNotEmpty() && enhanced != textFieldValue.text.trim() && enhanced.length > textFieldValue.text.trim().length) {
                    textFieldValue =
                        TextFieldValue(
                            text = enhanced,
                            selection = androidx.compose.ui.text.TextRange(enhanced.length)
                        )
                }
            } catch (e: Exception) {
                println("❌ Enhancement failed: ${e.message}")
            } finally {
                isEnhancing = false
            }
        }
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        // 移动端：不拦截 Enter 键，让输入法和虚拟键盘正常工作
        // 桌面端：Enter 发送，Shift+Enter 换行

        return when {
            // 补全弹窗显示时的特殊处理
            showCompletion -> {
                when (event.key) {
                    Key.Enter -> {
                        // 应用选中的补全
                        if (completionItems.isNotEmpty()) {
                            applyCompletion(completionItems[selectedCompletionIndex])
                        }
                        true
                    }

                    Key.DirectionDown -> {
                        selectedCompletionIndex = (selectedCompletionIndex + 1) % completionItems.size
                        true
                    }

                    Key.DirectionUp -> {
                        selectedCompletionIndex =
                            if (selectedCompletionIndex > 0) {
                                selectedCompletionIndex - 1
                            } else {
                                completionItems.size - 1
                            }
                        true
                    }

                    Key.Tab -> {
                        if (completionItems.isNotEmpty()) {
                            applyCompletion(completionItems[selectedCompletionIndex])
                        }
                        true
                    }

                    Key.Escape -> {
                        showCompletion = false
                        true
                    }

                    else -> false
                }
            }

            // 桌面端：Enter 发送消息（但不在移动端拦截）
            !isAndroid && !Platform.isIOS && event.key == Key.Enter && !event.isShiftPressed -> {
                if (textFieldValue.text.isNotBlank()) {
                    buildAndSendMessage(textFieldValue.text)
                    if (dismissKeyboardOnSend) {
                        focusManager.clearFocus()
                    }
                }
                true
            }

            // Ctrl+P 增强提示词
            event.key == Key.P && event.isCtrlPressed -> {
                enhanceCurrentInput()
                true
            }

            // Ctrl+V / Cmd+V 粘贴图片
            event.key == Key.V && (event.isCtrlPressed || event.isMetaPressed) -> {
                // Check for image in clipboard
                if (onImageUploadBytes != null) {
                    val clipboardReader = createClipboardImageReader()
                    val clipboardImage = clipboardReader.readImage()
                    if (clipboardImage != null) {
                        // Add image from clipboard bytes
                        imageUploadManager.addImageFromBytes(
                            bytes = clipboardImage.bytes,
                            mimeType = clipboardImage.mimeType,
                            suggestedName = clipboardImage.suggestedName
                        )
                        // Return true to consume the event only if we handled an image
                        true
                    } else {
                        // No image in clipboard, let normal text paste continue
                        false
                    }
                } else {
                    // Image upload not configured, let normal paste continue
                    false
                }
            }

            // 其他键不处理，让系统和输入法处理
            else -> false
        }
    }

    Column(
        modifier = modifier
            .then(
                if (isMobile) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                        focusManager.clearFocus()
                    }
                } else {
                    Modifier
                }
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Plan Summary Bar - shown above file changes when a plan is active
        PlanSummaryBar(
            plan = renderer?.currentPlan,
            modifier = Modifier.fillMaxWidth()
        )

        // File Change Summary - shown above the editor
        FileChangeSummary()

        Box(
            contentAlignment = if (isAndroid && isCompactMode) Alignment.Center else Alignment.TopStart
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(inputShape),
                border =
                    androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    ),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp, // 无叠影
                shadowElevation = 0.dp // 无阴影
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Top toolbar with file context management (desktop only)
                    if (!isMobile) {
                        TopToolbar(
                            selectedFiles = selectedFiles,
                            onAddFile = { file -> selectedFiles = selectedFiles + file },
                            onRemoveFile = { file ->
                                selectedFiles = selectedFiles.filter { it.path != file.path }
                            },
                            onClearFiles = { selectedFiles = emptyList() },
                            autoAddCurrentFile = autoAddCurrentFile,
                            onToggleAutoAdd = { autoAddCurrentFile = !autoAddCurrentFile },
                            searchProvider = effectiveSearchProvider
                        )
                    }

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = minHeight, max = maxHeight)
                                .padding(padding)
                    ) {
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { handleTextChange(it) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight() // 允许高度自动撑开
                                    .then(
                                        if (!isMobile) {
                                            Modifier.focusRequester(focusRequester)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .onPreviewKeyEvent { handleKeyEvent(it) },
                            textStyle =
                                TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = inputFontSize,
                                    // 使用透明颜色，避免与高亮文本重叠产生重影
                                    color = Color.Transparent,
                                    lineHeight = inputLineHeight
                                ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = maxLines,
                            // 移除 KeyboardOptions 和 KeyboardActions，使用系统默认行为
                            // 避免在某些平台上导致键盘弹出异常
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .wrapContentHeight()
                                ) {
                                    // 显示带高亮的文本
                                    if (highlightedText.isNotEmpty()) {
                                        Text(
                                            text = highlighter.highlight(highlightedText),
                                            style =
                                                TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = inputFontSize,
                                                    lineHeight = inputLineHeight
                                                ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // 占位符
                                    if (textFieldValue.text.isEmpty()) {
                                        Text(
                                            text = placeholder,
                                            style =
                                                TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = inputFontSize,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    lineHeight = inputLineHeight
                                                )
                                        )
                                    }

                                    // 实际的输入框（透明文本，只保留光标和选择）
                                    innerTextField()
                                }
                            }
                        )
                    }

                    // 提示文本
                    if (!isAndroid) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = if (isEnhancing) "Enhancing..." else "Ctrl+P to enhance prompt",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Image attachment bar - shown when images are attached
                    if (multimodalState.hasImages) {
                        ImageAttachmentBar(
                            images = multimodalState.images,
                            onRemoveImage = { image -> imageUploadManager.removeImage(image.id) },
                            onImageClick = { image -> previewingImage = image },
                            onRetryUpload = { image -> imageUploadManager.retryUpload(image) },
                            isAnalyzing = multimodalState.isAnalyzing,
                            isUploading = multimodalState.isUploading,
                            uploadedCount = multimodalState.uploadedCount,
                            analysisProgress = multimodalState.analysisProgress,
                            visionModel = multimodalState.visionModel,
                            onVisionModelChange = onVisionModelChange?.let { callback ->
                                { config ->
                                    imageUploadManager.setVisionModel(config.name)
                                    callback(config)
                                }
                            }
                        )
                    }

                    val currentWorkspace by WorkspaceManager.workspaceFlow.collectAsState()

                    BottomToolbar(
                        onSendClick = {
                            if (multimodalState.canSend && (textFieldValue.text.isNotBlank() || multimodalState.allImagesUploaded)) {
                                buildAndSendMessage(textFieldValue.text)
                                // Force dismiss keyboard on mobile
                                if (isMobile) {
                                    focusManager.clearFocus()
                                }
                            }
                        },
                        // Send enabled only when: has text OR all images uploaded, AND not uploading, AND not analyzing
                        sendEnabled = multimodalState.canSend && (textFieldValue.text.isNotBlank() || multimodalState.allImagesUploaded),
                        isExecuting = isExecuting || multimodalState.isAnalyzing || multimodalState.isUploading,
                        onStopClick = onStopClick,
                        workspacePath = currentWorkspace?.rootPath,
                        onAtClick = {
                            // 插入 @ 并触发 Agent 补全
                            val current = textFieldValue
                            val newText = current.text + "@"
                            val newPosition = current.text.length + 1

                            textFieldValue =
                                TextFieldValue(
                                    text = newText,
                                    selection = androidx.compose.ui.text.TextRange(newPosition)
                                )

                            // 立即触发补全
                            scope.launch {
                                delay(50) // 等待状态更新
                                val context =
                                    CompletionTrigger.buildContext(
                                        newText,
                                        newPosition,
                                        CompletionTriggerType.AGENT
                                    )
                                if (context != null && manager != null) {
                                    currentTriggerType = CompletionTriggerType.AGENT
                                    completionItems = manager.getFilteredCompletions(context)
                                    selectedCompletionIndex = 0
                                    showCompletion = completionItems.isNotEmpty()
                                }
                            }
                        },
                        onEnhanceClick = { enhanceCurrentInput() },
                        isEnhancing = isEnhancing,
                        onSettingsClick = {
                            showToolConfig = true
                        },
                        totalTokenInfo = renderer?.totalTokenInfo,
                        onModelConfigChange = { config ->
                            currentModelConfig = config
                            onModelConfigChange(config)
                        },
                        // Multimodal support
                        onImageClick = {
                            // Trigger image picker (only if upload callback is available)
                            if (onImageUpload != null) {
                                scope.launch {
                                    val fileChooser = createFileChooser()
                                    val selectedPath = fileChooser.chooseFile(
                                        title = "Select Image",
                                        initialDirectory = null,
                                        fileExtensions = AttachedImage.SUPPORTED_EXTENSIONS
                                    )
                                    if (selectedPath != null) {
                                        val image = AttachedImage.fromPath(selectedPath)
                                        imageUploadManager.addImageAndUpload(image)
                                    }
                                }
                            } else {
                                renderer?.renderError("Image upload is not configured")
                            }
                        },
                        hasImages = multimodalState.hasImages,
                        imageCount = multimodalState.imageCount,
                        visionModel = if (multimodalState.hasImages) multimodalState.visionModel else null,
                        // Image generation model support (GLM only)
                        currentModelConfig = currentModelConfig,
                        imageGenerationModel = imageGenerationModel,
                        onImageGenerationModelChange = { model ->
                            imageGenerationModel = model
                            onImageGenerationModelChange?.invoke(model)
                        }
                    )
                }
            }

            if (showToolConfig) {
                ToolConfigDialog(
                    onDismiss = { showToolConfig = false },
                    onSave = { toolConfigFile ->
                        scope.launch {
                            mcpServers = toolConfigFile.mcpServers
                        }
                    },
                    llmService = llmService
                )
            }

            // Image preview dialog
            if (previewingImage != null) {
                ImagePreviewDialog(
                    image = previewingImage!!,
                    onDismiss = { previewingImage = null },
                    onRemove = {
                        imageUploadManager.removeImage(previewingImage!!.id)
                        previewingImage = null
                    }
                )
            }

            // Only show completion popup on desktop, not on mobile
            if (!isMobile && showCompletion && completionItems.isNotEmpty()) {
                CompletionPopup(
                    items = completionItems,
                    selectedIndex = selectedCompletionIndex,
                    offset = IntOffset(12, if (isCompactMode) 60 else 120),
                    onItemSelected = { item ->
                        applyCompletion(item)
                    },
                    onSelectedIndexChanged = { index ->
                        selectedCompletionIndex = index
                    },
                    onDismiss = {
                        showCompletion = false
                    }
                )
            }
        }
    }

    // No auto-focus on any platform - user must tap to show keyboard
    // This provides consistent behavior across mobile and desktop
}
