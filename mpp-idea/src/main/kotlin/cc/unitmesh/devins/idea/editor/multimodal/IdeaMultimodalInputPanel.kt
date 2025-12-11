package cc.unitmesh.devins.idea.editor.multimodal

import cc.unitmesh.devins.idea.editor.IdeaDevInInput
import cc.unitmesh.devins.idea.editor.IdeaInputListener
import cc.unitmesh.devins.idea.editor.IdeaInputTrigger
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import javax.swing.*

/**
 * Complete input panel with multimodal support.
 * Combines IdeaDevInInput with image attachment bar and action buttons.
 *
 * Usage example:
 * ```kotlin
 * val panel = IdeaMultimodalInputPanel(
 *     project = project,
 *     parentDisposable = disposable,
 *     onImageUpload = { path, id, onProgress ->
 *         // Upload to cloud storage
 *         IdeaImageUploadResult(success = true, url = "https://...")
 *     },
 *     onImageUploadBytes = { bytes, name, mime, id, onProgress ->
 *         // Upload bytes to cloud storage  
 *         IdeaImageUploadResult(success = true, url = "https://...")
 *     },
 *     onMultimodalAnalysis = { urls, prompt, onChunk ->
 *         // Call vision model API
 *         "Analysis result..."
 *     }
 * )
 *
 * panel.addInputListener(object : IdeaInputListener {
 *     override fun onSubmit(text: String, trigger: IdeaInputTrigger) {
 *         // Handle text-only submission
 *     }
 *     override fun onSubmitWithMultimodal(text, trigger, state, analysisResult) {
 *         // Handle multimodal submission
 *     }
 * })
 * ```
 */
class IdeaMultimodalInputPanel(
    private val project: Project,
    private val parentDisposable: Disposable,
    // Multimodal callbacks
    onImageUpload: ImageUploadCallback? = null,
    onImageUploadBytes: ImageUploadBytesCallback? = null,
    onMultimodalAnalysis: MultimodalAnalysisCallback? = null,
    onError: ((String) -> Unit)? = null,
    // Input configuration
    private val placeholder: String = "Type your message or /help for commands...",
    private val showImageButton: Boolean = true
) : JPanel(BorderLayout()), Disposable {

    /** The DevIn input field */
    val inputField: IdeaDevInInput
    
    /** The image attachment panel (visible when images are attached) */
    val imagePanel: IdeaImageAttachmentPanel?
    
    /** Send button */
    private val sendButton: JButton
    
    /** Image attachment button */
    private val imageButton: JButton?
    
    /** Stop button (shown during execution) */
    private val stopButton: JButton
    
    private var isExecuting = false

    init {
        Disposer.register(parentDisposable, this)
        
        border = JBUI.Borders.empty(4)
        background = UIUtil.getPanelBackground()
        
        // Create input field with multimodal support
        inputField = IdeaDevInInput(
            project = project,
            disposable = parentDisposable,
            onImageUpload = onImageUpload,
            onImageUploadBytes = onImageUploadBytes,
            onMultimodalAnalysis = onMultimodalAnalysis,
            onError = onError
        )
        inputField.setPlaceholder(placeholder)
        
        // Create image attachment panel
        imagePanel = inputField.getImageUploadManager()?.let { manager ->
            IdeaImageAttachmentPanel(project, manager, parentDisposable)
        }
        
        // Main content panel
        val contentPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createLineBorder(JBColor.border(), 1, true)
            isOpaque = false
        }
        
        // Image panel at top (hidden by default)
        imagePanel?.let { panel ->
            contentPanel.add(panel, BorderLayout.NORTH)
        }
        
        // Input field in center
        val inputScrollPane = JScrollPane(inputField).apply {
            border = null
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            preferredSize = Dimension(400, 80)
        }
        contentPanel.add(inputScrollPane, BorderLayout.CENTER)
        
        // Button panel at bottom
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 4)).apply {
            isOpaque = false
        }
        
        // Image button
        imageButton = if (showImageButton && inputField.isMultimodalEnabled) {
            JButton(AllIcons.FileTypes.Image).apply {
                toolTipText = "Attach Image (or paste with Ctrl+V)"
                isFocusPainted = false
                addActionListener {
                    imagePanel?.selectImageFile()
                }
            }
        } else null
        
        imageButton?.let { buttonPanel.add(it) }
        
        // Stop button (hidden by default)
        stopButton = JButton("Stop", AllIcons.Actions.Suspend).apply {
            isVisible = false
            addActionListener {
                inputField.triggerStop()
            }
        }
        buttonPanel.add(stopButton)
        
        // Send button
        sendButton = JButton("Send", AllIcons.Actions.Execute).apply {
            addActionListener {
                inputField.triggerSubmit(IdeaInputTrigger.Button)
            }
        }
        buttonPanel.add(sendButton)
        
        contentPanel.add(buttonPanel, BorderLayout.SOUTH)
        
        add(contentPanel, BorderLayout.CENTER)
        
        // Listen for multimodal state changes to update UI
        inputField.getImageUploadManager()?.addListener(object : IdeaMultimodalStateListener {
            override fun onStateChanged(state: IdeaMultimodalState) {
                SwingUtilities.invokeLater {
                    updateButtonState(state)
                }
            }
        })
    }
    
    private fun updateButtonState(state: IdeaMultimodalState) {
        // Update send button
        val canSend = state.canSend && (inputField.text.isNotBlank() || state.allImagesUploaded)
        sendButton.isEnabled = canSend && !isExecuting
        
        // Update image button badge
        if (imageButton != null && state.hasImages) {
            imageButton.text = "${state.imageCount}"
        } else {
            imageButton?.text = null
        }
    }
    
    /**
     * Set executing state (shows stop button, disables send).
     */
    fun setExecuting(executing: Boolean) {
        isExecuting = executing
        sendButton.isVisible = !executing
        stopButton.isVisible = executing
        updateButtonState(inputField.multimodalState)
    }
    
    /**
     * Add an input listener.
     */
    fun addInputListener(listener: IdeaInputListener) {
        inputField.addInputListener(listener)
    }
    
    /**
     * Remove an input listener.
     */
    fun removeInputListener(listener: IdeaInputListener) {
        inputField.removeInputListener(listener)
    }
    
    /**
     * Clear input and attached images.
     */
    fun clearInput() {
        inputField.clearInput()
        inputField.clearImages()
    }
    
    /**
     * Request focus to the input field.
     */
    override fun requestFocus() {
        inputField.requestFocus()
    }
    
    override fun dispose() {
        // Resources cleaned up by Disposer
    }
}

