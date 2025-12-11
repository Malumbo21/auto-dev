package cc.unitmesh.devins.idea.toolwindow

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import cc.unitmesh.agent.plan.AgentPlan
import cc.unitmesh.devins.idea.compose.rememberIdeaCoroutineScope
import cc.unitmesh.devins.idea.editor.*
import cc.unitmesh.devins.idea.editor.multimodal.*
import cc.unitmesh.llm.NamedModelConfig
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JPanel

/**
 * Advanced chat input area with full DevIn language support.
 *
 * Uses IdeaDevInInput (EditorTextField-based) embedded via SwingPanel for:
 * - DevIn language syntax highlighting and completion
 * - IntelliJ's native completion popup integration
 * - Enter to submit, Shift+Enter for newline
 * - @ trigger for agent completion
 * - Token usage display
 * - Settings access
 * - Stop/Send button based on execution state
 * - Model selector for switching between LLM configurations
 *
 * Layout: Unified border around the entire input area for a cohesive look.
 */
private val inputAreaLogger = Logger.getInstance("IdeaDevInInputArea")

/**
 * Helper function to build and send message with file references.
 * Extracts common logic from onSubmit and onSendClick.
 */
private fun buildAndSendMessage(
    text: String,
    selectedFiles: List<SelectedFileItem>,
    onSend: (String) -> Unit,
    clearInput: () -> Unit,
    clearFiles: () -> Unit
) {
    if (text.isBlank()) return

    val filesText = selectedFiles.joinToString("\n") { it.toDevInsCommand() }
    val fullText = if (filesText.isNotEmpty()) "$text\n$filesText" else text
    onSend(fullText)
    clearInput()
    clearFiles()
}

/**
 * Composable wrapper for SwingDevInInputArea.
 * Uses SwingPanel to embed the Swing-based input area in Compose.
 * This approach avoids z-index issues by keeping EditorTextField as native Swing
 * and using JewelComposePanel for Compose toolbars.
 */
@Composable
fun IdeaDevInInputArea(
    project: Project,
    parentDisposable: Disposable,
    isProcessing: Boolean,
    onSend: (String) -> Unit,
    onAbort: () -> Unit,
    workspacePath: String? = null,
    totalTokens: Int? = null,
    onAtClick: () -> Unit = {},
    availableConfigs: List<NamedModelConfig> = emptyList(),
    currentConfigName: String? = null,
    onConfigSelect: (NamedModelConfig) -> Unit = {},
    onConfigureClick: () -> Unit = {},
    onAddNewConfig: () -> Unit = {},
    onRefreshCopilot: (() -> Unit)? = null,
    isRefreshingCopilot: Boolean = false,
    currentPlan: AgentPlan? = null,
    // Multimodal analysis streaming callbacks for renderer
    onMultimodalAnalysisStart: ((Int, String) -> Unit)? = null,
    onMultimodalAnalysisChunk: ((String) -> Unit)? = null,
    onMultimodalAnalysisComplete: ((String?, String?) -> Unit)? = null
) {
    val scope = rememberIdeaCoroutineScope(project)
    var swingInputArea by remember { mutableStateOf<SwingDevInInputArea?>(null) }

    // Update SwingDevInInputArea properties when they change
    DisposableEffect(isProcessing) {
        swingInputArea?.setProcessing(isProcessing)
        onDispose { }
    }

    DisposableEffect(totalTokens) {
        swingInputArea?.setTotalTokens(totalTokens)
        onDispose { }
    }

    DisposableEffect(availableConfigs) {
        swingInputArea?.setAvailableConfigs(availableConfigs)
        onDispose { }
    }

    DisposableEffect(currentConfigName) {
        swingInputArea?.setCurrentConfigName(currentConfigName)
        onDispose { }
    }

    DisposableEffect(onConfigSelect) {
        swingInputArea?.setOnConfigSelect(onConfigSelect)
        onDispose { }
    }

    DisposableEffect(onConfigureClick) {
        swingInputArea?.setOnConfigureClick(onConfigureClick)
        onDispose { }
    }

    DisposableEffect(onAddNewConfig) {
        swingInputArea?.setOnAddNewConfig(onAddNewConfig)
        onDispose { }
    }
    
    DisposableEffect(onRefreshCopilot) {
        if (onRefreshCopilot != null) {
            swingInputArea?.setOnRefreshCopilot(onRefreshCopilot)
        }
        onDispose { }
    }
    
    DisposableEffect(isRefreshingCopilot) {
        swingInputArea?.setRefreshingCopilot(isRefreshingCopilot)
        onDispose { }
    }

    DisposableEffect(currentPlan) {
        swingInputArea?.setCurrentPlan(currentPlan)
        onDispose { }
    }
    
    DisposableEffect(onMultimodalAnalysisStart) {
        onMultimodalAnalysisStart?.let { swingInputArea?.setOnMultimodalAnalysisStart(it) }
        onDispose { }
    }
    
    DisposableEffect(onMultimodalAnalysisChunk) {
        onMultimodalAnalysisChunk?.let { swingInputArea?.setOnMultimodalAnalysisChunk(it) }
        onDispose { }
    }
    
    DisposableEffect(onMultimodalAnalysisComplete) {
        onMultimodalAnalysisComplete?.let { swingInputArea?.setOnMultimodalAnalysisComplete(it) }
        onDispose { }
    }

    // Embed SwingDevInInputArea using SwingPanel
    SwingPanel(
        modifier = Modifier.fillMaxSize(),
        factory = {
            SwingDevInInputArea(
                project = project,
                parentDisposable = parentDisposable,
                onSend = onSend,
                onAbort = onAbort,
                scope = scope
            ).also {
                swingInputArea = it
                // Apply initial values
                it.setProcessing(isProcessing)
                it.setTotalTokens(totalTokens)
                it.setAvailableConfigs(availableConfigs)
                it.setCurrentConfigName(currentConfigName)
                it.setOnConfigSelect(onConfigSelect)
                it.setOnConfigureClick(onConfigureClick)
                it.setOnAddNewConfig(onAddNewConfig)
                it.setCurrentPlan(currentPlan)
            }
        },
        update = { panel ->
            // Panel updates are handled via DisposableEffect above
        }
    )
}

/**
 * Pure Swing-based input area.
 * Uses native Swing toolbars to avoid z-index issues with Compose popups.
 * 
 * Multimodal support:
 * - Paste images with Ctrl/Cmd+V
 * - Click image button to select images
 * - Images are uploaded to cloud storage and analyzed with vision models
 */
class SwingDevInInputArea(
    private val project: Project,
    private val parentDisposable: Disposable,
    private val onSend: (String) -> Unit,
    private val onAbort: () -> Unit,
    private val scope: CoroutineScope,
    /** Enable multimodal image support */
    private val enableMultimodal: Boolean = true,
    /** Callback for multimodal analysis streaming to renderer */
    private var onMultimodalAnalysisStart: ((Int, String) -> Unit)? = null,
    private var onMultimodalAnalysisChunk: ((String) -> Unit)? = null,
    private var onMultimodalAnalysisComplete: ((String?, String?) -> Unit)? = null
) : JPanel(BorderLayout()), Disposable {

    private val logger = Logger.getInstance(SwingDevInInputArea::class.java)

    private var devInInput: IdeaDevInInput? = null
    private var inputText: String = ""
    private var isProcessing: Boolean = false
    private var isEnhancing: Boolean = false

    // Swing toolbars
    private lateinit var topToolbar: SwingTopToolbar
    private lateinit var bottomToolbar: SwingBottomToolbar
    
    // Multimodal support
    private var imageAttachmentPanel: IdeaImageAttachmentPanel? = null
    private val multimodalService: IdeaMultimodalService? = if (enableMultimodal) {
        IdeaMultimodalService.getInstance(project).also { it.initialize() }
    } else null

    // Callbacks for config selection
    private var currentPlan: AgentPlan? = null

    init {
        border = JBUI.Borders.empty(0)
        setupUI()
        Disposer.register(parentDisposable, this)
    }

    private fun setupUI() {
        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty()
        }

        // Top toolbar (pure Swing)
        topToolbar = SwingTopToolbar(project) { files ->
            // Files selected callback - already handled in SwingTopToolbar
        }.apply {
            maximumSize = Dimension(Int.MAX_VALUE, 40)
        }
        contentPanel.add(topToolbar)

        // Get multimodal callbacks if enabled
        val callbacks = multimodalService?.createCallbacks()
        logger.info("Multimodal callbacks created: ${callbacks != null}, analysis=${callbacks?.isAnalysisConfigured}")

        // DevIn Editor (native Swing) with multimodal support
        val editorPanel = JPanel(BorderLayout()).apply {
            val input = IdeaDevInInput(
                project = project,
                disposable = parentDisposable,
                showAgent = true,
                // Multimodal callbacks - non-nullable when multimodalService is available
                onImageUpload = callbacks?.uploadCallback,
                onImageUploadBytes = callbacks?.uploadBytesCallback,
                onMultimodalAnalysis = callbacks?.analysisCallback,
                onError = { error -> logger.warn("Multimodal error: $error") }
            ).apply {
                recreateDocument()

                addInputListener(object : IdeaInputListener {
                    override fun editorAdded(editor: EditorEx) {
                        // Editor is ready
                    }

                    override fun onSubmit(text: String, trigger: IdeaInputTrigger) {
                        if (text.isNotBlank() && !isProcessing) {
                            sendMessage(text)
                        }
                    }
                    
                    override fun onSubmitWithMultimodal(
                        text: String,
                        trigger: IdeaInputTrigger,
                        multimodalState: IdeaMultimodalState,
                        analysisResult: String?
                    ) {
                        if (!isProcessing) {
                            sendMessageWithMultimodal(text, multimodalState, analysisResult)
                        }
                    }

                    override fun onStop() {
                        onAbort()
                    }

                    override fun onTextChanged(text: String) {
                        inputText = text
                        updateSendButtonState()
                    }
                    
                    override fun onMultimodalStateChanged(state: IdeaMultimodalState) {
                        bottomToolbar.updateMultimodalState(state)
                        updateSendButtonState()
                    }
                    
                    override fun onMultimodalAnalysisStart(imageCount: Int, prompt: String) {
                        onMultimodalAnalysisStart?.invoke(imageCount, prompt)
                    }
                    
                    override fun onMultimodalAnalysisChunk(chunk: String) {
                        onMultimodalAnalysisChunk?.invoke(chunk)
                    }
                    
                    override fun onMultimodalAnalysisComplete(fullResult: String?, error: String?) {
                        onMultimodalAnalysisComplete?.invoke(fullResult, error)
                    }
                })
            }

            Disposer.register(parentDisposable, input)
            devInInput = input

            add(input, BorderLayout.CENTER)
            
            // Add image attachment panel below the input (if multimodal enabled)
            if (enableMultimodal && input.getImageUploadManager() != null) {
                imageAttachmentPanel = IdeaImageAttachmentPanel(
                    project = project,
                    uploadManager = input.getImageUploadManager()!!,
                    parentDisposable = parentDisposable
                ).apply {
                    maximumSize = Dimension(Int.MAX_VALUE, 100)
                }
                add(imageAttachmentPanel, BorderLayout.SOUTH)
            }
            
            minimumSize = Dimension(200, 60)
            preferredSize = Dimension(Int.MAX_VALUE, 100)
        }
        contentPanel.add(editorPanel)

        // Bottom toolbar (pure Swing) with image button
        bottomToolbar = SwingBottomToolbar(
            project = project,
            onSendClick = {
                devInInput?.triggerSubmit(IdeaInputTrigger.Button)
            },
            onStopClick = onAbort,
            onPromptOptimizationClick = { handlePromptOptimization() },
            onImageClick = if (enableMultimodal && callbacks?.isUploadConfigured == true) {
                { imageAttachmentPanel?.selectImageFile() }
            } else null
        ).apply {
            maximumSize = Dimension(Int.MAX_VALUE, 40)
        }
        contentPanel.add(bottomToolbar)

        add(contentPanel, BorderLayout.CENTER)
    }
    
    private fun updateSendButtonState() {
        val hasText = inputText.isNotBlank()
        val multimodalState = devInInput?.multimodalState ?: IdeaMultimodalState()
        val canSend = multimodalState.canSend && (hasText || multimodalState.allImagesUploaded)
        bottomToolbar.setSendEnabled(canSend && !isProcessing)
    }

    private fun sendMessage(text: String) {
        val selectedFiles = topToolbar.getSelectedFiles()
        val filesText = selectedFiles.joinToString("\n") { it.toDevInsCommand() }
        val fullText = if (filesText.isNotEmpty()) "$text\n$filesText" else text
        onSend(fullText)
        devInInput?.clearInput()
        inputText = ""
        topToolbar.clearFiles()
        bottomToolbar.setSendEnabled(false)
    }
    
    /**
     * Send message with multimodal content.
     * Appends image URLs and analysis result to the message.
     */
    private fun sendMessageWithMultimodal(
        text: String,
        state: IdeaMultimodalState,
        analysisResult: String?
    ) {
        val selectedFiles = topToolbar.getSelectedFiles()
        val filesText = selectedFiles.joinToString("\n") { it.toDevInsCommand() }
        
        // Build full message with images and analysis
        val messageBuilder = StringBuilder()
        if (text.isNotBlank()) {
            messageBuilder.append(text)
        }
        
        // Add file references
        if (filesText.isNotEmpty()) {
            messageBuilder.append("\n").append(filesText)
        }
        
        // Add image URLs as references
        if (state.hasImages) {
            val imageUrls = state.images.mapNotNull { it.uploadedUrl }
            if (imageUrls.isNotEmpty()) {
                messageBuilder.append("\n\n[Attached Images]\n")
                imageUrls.forEachIndexed { index, url ->
                    messageBuilder.append("- Image ${index + 1}: $url\n")
                }
            }
        }
        
        // Add vision analysis result if available
        if (analysisResult != null && analysisResult.isNotBlank()) {
            messageBuilder.append("\n\n[Image Analysis]\n")
            messageBuilder.append(analysisResult)
        }
        
        val fullText = messageBuilder.toString()
        onSend(fullText)
        
        // Clear input state
        devInInput?.clearInput()
        devInInput?.clearImages()
        inputText = ""
        topToolbar.clearFiles()
        bottomToolbar.setSendEnabled(false)
    }

    private fun handlePromptOptimization() {
        val currentText = devInInput?.text?.trim() ?: inputText.trim()
        logger.info("Prompt optimization clicked, text length: ${currentText.length}")

        if (currentText.isNotBlank() && !isEnhancing && !isProcessing) {
            isEnhancing = true
            bottomToolbar.setEnhancing(true)

            scope.launch(Dispatchers.IO) {
                try {
                    logger.info("Starting prompt enhancement...")
                    val enhancer = IdeaPromptEnhancer.getInstance(project)
                    val enhanced = enhancer.enhance(currentText)
                    logger.info("Enhancement completed, result length: ${enhanced.length}")

                    if (enhanced != currentText && enhanced.isNotBlank()) {
                        ApplicationManager.getApplication().invokeLater {
                            devInInput?.replaceText(enhanced)
                            inputText = enhanced
                            logger.info("Text updated in input field")
                        }
                    } else {
                        logger.info("No enhancement made (same text or empty result)")
                    }
                } catch (e: Exception) {
                    logger.error("Prompt enhancement failed: ${e.message}", e)
                } finally {
                    ApplicationManager.getApplication().invokeLater {
                        isEnhancing = false
                        bottomToolbar.setEnhancing(false)
                    }
                }
            }
        }
    }

    fun setProcessing(processing: Boolean) {
        isProcessing = processing
        bottomToolbar.setProcessing(processing)
        bottomToolbar.setSendEnabled(inputText.isNotBlank() && !processing)
    }

    fun setTotalTokens(tokens: Int?) {
        bottomToolbar.setTotalTokens(tokens)
    }

    fun setAvailableConfigs(configs: List<NamedModelConfig>) {
        bottomToolbar.setAvailableConfigs(configs)
    }

    fun setCurrentConfigName(name: String?) {
        bottomToolbar.setCurrentConfigName(name)
    }

    fun setOnConfigSelect(callback: (NamedModelConfig) -> Unit) {
        bottomToolbar.setOnConfigSelect(callback)
    }

    fun setOnConfigureClick(callback: () -> Unit) {
        bottomToolbar.setOnConfigureClick(callback)
    }

    fun setOnAddNewConfig(callback: () -> Unit) {
        bottomToolbar.setOnAddNewConfig(callback)
    }
    
    fun setOnRefreshCopilot(callback: () -> Unit) {
        bottomToolbar.setOnRefreshCopilot(callback)
    }
    
    fun setRefreshingCopilot(refreshing: Boolean) {
        bottomToolbar.setRefreshingCopilot(refreshing)
    }

    fun setCurrentPlan(plan: AgentPlan?) {
        currentPlan = plan
        // TODO: Add plan summary bar support
    }
    
    fun setOnMultimodalAnalysisStart(callback: (Int, String) -> Unit) {
        onMultimodalAnalysisStart = callback
    }
    
    fun setOnMultimodalAnalysisChunk(callback: (String) -> Unit) {
        onMultimodalAnalysisChunk = callback
    }
    
    fun setOnMultimodalAnalysisComplete(callback: (String?, String?) -> Unit) {
        onMultimodalAnalysisComplete = callback
    }

    override fun dispose() {
        devInInput = null
    }
}