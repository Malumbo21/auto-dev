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
import cc.unitmesh.devins.ui.compose.editor.multimodal.ImagePreviewDialog
import cc.unitmesh.devins.ui.compose.editor.multimodal.MultimodalState
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devins.ui.platform.createFileChooser
import cc.unitmesh.devins.workspace.WorkspaceManager
import cc.unitmesh.llm.KoogLLMService
import cc.unitmesh.llm.ModelConfig
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
     * Called to perform vision analysis on uploaded images.
     * @param imageUrls List of uploaded image URLs
     * @param prompt User's prompt text
     * @return Analysis result string
     */
    onMultimodalAnalysis: (suspend (imageUrls: List<String>, prompt: String) -> String?)? = null
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

    // File search provider - use WorkspaceFileSearchProvider as default if not provided
    val effectiveSearchProvider = remember { fileSearchProvider ?: WorkspaceFileSearchProvider() }
    
    // Multimodal state - use explicit .value access to ensure proper state updates in closures/coroutines
    val _multimodalState = remember { mutableStateOf(MultimodalState()) }
    var previewingImage by remember { mutableStateOf<AttachedImage?>(null) }
    
    // Need scope early for buildAndSendMessage
    val scope = rememberCoroutineScope()
    
    // Helper functions to read/write multimodal state (ensures proper state access in closures)
    fun getMultimodalState(): MultimodalState = _multimodalState.value
    fun setMultimodalState(newState: MultimodalState) { _multimodalState.value = newState }
    
    // For simple reads in composable scope (reads directly from state holder)
    // Note: In coroutines/closures, use getMultimodalState() instead

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
        val currentState = getMultimodalState()
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
            setMultimodalState(currentState.copy(
                isAnalyzing = true,
                analysisProgress = "Analyzing ${imageUrls.size} image(s) with ${currentState.visionModel}..."
            ))
            
            // Show progress in renderer
            renderer?.renderInfo("Analyzing image(s) with ${currentState.visionModel}...")
            
            scope.launch {
                try {
                    // Perform multimodal analysis with uploaded URLs
                    val analysisResult = onMultimodalAnalysis!!(imageUrls, originalText)
                    
                    // Update state with result
                    val afterAnalysis = getMultimodalState()
                    setMultimodalState(afterAnalysis.copy(
                        isAnalyzing = false,
                        analysisProgress = null,
                        analysisResult = analysisResult
                    ))
                    
                    // Send with multimodal result
                    callbacks?.onSubmitWithMultimodal(originalText, getFileContexts(), analysisResult)
                    
                    // Clear input and images
                    textFieldValue = TextFieldValue("")
                    selectedFiles = emptyList()
                    setMultimodalState(MultimodalState())
                    showCompletion = false
                    
                } catch (e: Exception) {
                    val afterError = getMultimodalState()
                    setMultimodalState(afterError.copy(
                        isAnalyzing = false,
                        analysisProgress = null,
                        analysisError = e.message ?: "Analysis failed"
                    ))
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
    
    /**
     * Update the upload status of an image
     */
    fun updateImageStatus(imageId: String, status: cc.unitmesh.devins.ui.compose.editor.multimodal.ImageUploadStatus) {
        val current = getMultimodalState()
        setMultimodalState(current.copy(
            images = current.images.map { img ->
                if (img.id == imageId) img.copy(uploadStatus = status) else img
            }
        ))
    }
    
    /**
     * Update the upload progress of an image
     */
    fun updateImageProgress(imageId: String, progress: Int) {
        val current = getMultimodalState()
        setMultimodalState(current.copy(
            images = current.images.map { img ->
                if (img.id == imageId) img.copy(uploadProgress = progress) else img
            }
        ))
    }
    
    /**
     * Remove an image from the multimodal state
     */
    fun removeImage(imageId: String) {
        val current = getMultimodalState()
        setMultimodalState(current.copy(
            images = current.images.filter { it.id != imageId }
        ))
    }
    
    /**
     * Upload a single image to cloud storage.
     * Uses getMultimodalState()/setMultimodalState() for proper state access in coroutines.
     */
    suspend fun uploadImage(image: AttachedImage) {
        if (onImageUpload == null || image.path == null) return
        
        val imageId = image.id
        println("🚀 Starting upload for image: $imageId (${image.name})")
        
        // Update status to compressing
        updateImageStatus(imageId, cc.unitmesh.devins.ui.compose.editor.multimodal.ImageUploadStatus.COMPRESSING)
        
        try {
            // Update status to uploading
            updateImageStatus(imageId, cc.unitmesh.devins.ui.compose.editor.multimodal.ImageUploadStatus.UPLOADING)
            
            // Perform upload with progress callback - returns ImageUploadResult
            val result = onImageUpload!!(image.path!!, imageId) { progress ->
                updateImageProgress(imageId, progress)
            }
            
            println("📦 Upload result: success=${result.success}, url=${result.url}, originalSize=${result.originalSize}, compressedSize=${result.compressedSize}")
            
            if (result.success && result.url != null) {
                // Update status to completed with URL and sizes
                println("✅ Updating state for image $imageId to COMPLETED")
                
                val current = getMultimodalState()
                println("   Current state images: ${current.images.map { "${it.id}:${it.uploadStatus}" }}")
                
                val updatedImages = current.images.map { img ->
                    if (img.id == imageId) {
                        println("   Found matching image, updating...")
                        img.copy(
                            uploadStatus = cc.unitmesh.devins.ui.compose.editor.multimodal.ImageUploadStatus.COMPLETED,
                            uploadedUrl = result.url,
                            uploadProgress = 100,
                            originalSize = result.originalSize,
                            compressedSize = result.compressedSize
                        )
                    } else img
                }
                
                val newState = current.copy(images = updatedImages)
                println("   New state images: ${newState.images.map { "${it.id}:${it.uploadStatus}:${it.uploadedUrl}" }}")
                
                setMultimodalState(newState)
                
                // Verify the update
                val verifyState = getMultimodalState()
                println("   Verified state: ${verifyState.images.map { "${it.id}:${it.uploadStatus}:${it.uploadedUrl}" }}")
                
                println("✅ Image uploaded: ${result.url}")
            } else {
                throw Exception(result.error ?: "Upload failed")
            }
            
        } catch (e: Exception) {
            println("❌ Upload exception: ${e.message}")
            
            // Update status to failed
            val current = getMultimodalState()
            val updatedImages = current.images.map { img ->
                if (img.id == imageId) {
                    img.copy(
                        uploadStatus = cc.unitmesh.devins.ui.compose.editor.multimodal.ImageUploadStatus.FAILED,
                        uploadError = e.message ?: "Upload failed"
                    )
                } else img
            }
            setMultimodalState(current.copy(images = updatedImages))
            
            println("❌ Image upload failed: ${e.message}")
            renderer?.renderError("Image upload failed: ${e.message}")
        }
    }
    
    /**
     * Add an image and start uploading it immediately
     */
    fun addImageAndUpload(image: AttachedImage) {
        val current = getMultimodalState()
        if (!current.canAddMoreImages) return
        
        // Add image with PENDING status
        val newImage = image.copy(uploadStatus = cc.unitmesh.devins.ui.compose.editor.multimodal.ImageUploadStatus.PENDING)
        setMultimodalState(current.copy(
            images = current.images + newImage
        ))
        
        // Start upload if callback is available
        if (onImageUpload != null && image.path != null) {
            scope.launch {
                uploadImage(newImage)
            }
        }
    }
    
    /**
     * Retry uploading a failed image
     */
    fun retryImageUpload(image: AttachedImage) {
        if (image.path != null) {
            // Reset status and retry
            updateImageStatus(image.id, cc.unitmesh.devins.ui.compose.editor.multimodal.ImageUploadStatus.PENDING)
            scope.launch {
                uploadImage(image)
            }
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

    var llmService by remember { mutableStateOf<KoogLLMService?>(null) }

    LaunchedEffect(Unit) {
        try {
            val workspace = WorkspaceManager.currentWorkspace
            val projectPath = workspace?.rootPath
            if (projectPath != null) {
                val configWrapper = ConfigManager.load()
                val activeConfig = configWrapper.getActiveModelConfig()
                if (activeConfig != null && activeConfig.isValid()) {
                    llmService = KoogLLMService.create(activeConfig)

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
                println("[Enhancement] Enhancing current input...")

                val enhanced = (currentEnhancer as PromptEnhancer).enhance(textFieldValue.text.trim(), "zh")

                if (enhanced.isNotEmpty() && enhanced != textFieldValue.text.trim() && enhanced.length > textFieldValue.text.trim().length) {
                    textFieldValue =
                        TextFieldValue(
                            text = enhanced,
                            selection = androidx.compose.ui.text.TextRange(enhanced.length)
                        )
                    println("✨ Enhanced: \"${textFieldValue.text.trim()}\" -> \"$enhanced\"")
                } else {
                    println("ℹ️ No enhancement needed or failed")
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

            // 其他键不处理，让系统和输入法处理
            else -> false
        }
    }

    Column(
        modifier = modifier
            .then(
                if (isMobile) {
                    Modifier.clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
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
                    if (_multimodalState.value.hasImages) {
                        ImageAttachmentBar(
                            images = _multimodalState.value.images,
                            onRemoveImage = { image -> removeImage(image.id) },
                            onImageClick = { image -> previewingImage = image },
                            onRetryUpload = { image -> retryImageUpload(image) },
                            isAnalyzing = _multimodalState.value.isAnalyzing,
                            isUploading = _multimodalState.value.isUploading,
                            uploadedCount = _multimodalState.value.uploadedCount,
                            analysisProgress = _multimodalState.value.analysisProgress,
                            visionModel = _multimodalState.value.visionModel
                        )
                    }
                    
                    val currentWorkspace by WorkspaceManager.workspaceFlow.collectAsState()

                    BottomToolbar(
                        onSendClick = {
                            if (_multimodalState.value.canSend && (textFieldValue.text.isNotBlank() || _multimodalState.value.allImagesUploaded)) {
                                buildAndSendMessage(textFieldValue.text)
                                // Force dismiss keyboard on mobile
                                if (isMobile) {
                                    focusManager.clearFocus()
                                }
                            }
                        },
                        // Send enabled only when: has text OR all images uploaded, AND not uploading, AND not analyzing
                        sendEnabled = _multimodalState.value.canSend && (textFieldValue.text.isNotBlank() || _multimodalState.value.allImagesUploaded),
                        isExecuting = isExecuting || _multimodalState.value.isAnalyzing || _multimodalState.value.isUploading,
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
                                    println("[Completion] @ trigger: items=${completionItems.size}")
                                }
                            }
                        },
                        onEnhanceClick = { enhanceCurrentInput() },
                        isEnhancing = isEnhancing,
                        onSettingsClick = {
                            showToolConfig = true
                        },
                        totalTokenInfo = renderer?.totalTokenInfo,
                        onModelConfigChange = onModelConfigChange,
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
                                        addImageAndUpload(image)
                                    }
                                }
                            } else {
                                renderer?.renderError("Image upload is not configured")
                            }
                        },
                        hasImages = _multimodalState.value.hasImages,
                        imageCount = _multimodalState.value.imageCount,
                        visionModel = if (_multimodalState.value.hasImages) _multimodalState.value.visionModel else null
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
                        removeImage(previewingImage!!.id)
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
