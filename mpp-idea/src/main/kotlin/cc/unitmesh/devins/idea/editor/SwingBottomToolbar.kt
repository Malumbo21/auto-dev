package cc.unitmesh.devins.idea.editor

import cc.unitmesh.devti.llm2.GithubCopilotDetector
import cc.unitmesh.devins.idea.editor.multimodal.IdeaMultimodalState
import cc.unitmesh.llm.NamedModelConfig
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*

/**
 * Swing-based bottom toolbar for the input section.
 * Provides send/stop buttons, model selector, token info, and image attachment button.
 */
class SwingBottomToolbar(
    private val project: Project?,
    private val onSendClick: () -> Unit,
    private val onStopClick: () -> Unit,
    private val onPromptOptimizationClick: () -> Unit,
    private val onImageClick: (() -> Unit)? = null // Optional image attachment callback
) : JPanel(BorderLayout()) {

    private val modelComboBox = ComboBox<String>()
    private val tokenLabel = JBLabel()
    private val sendButton = JButton("Send", AllIcons.Actions.Execute)
    private val stopButton = JButton("Stop", AllIcons.Actions.Suspend)
    private val optimizeButton = JButton(AllIcons.Actions.Lightning)
    private val settingsButton = JButton(AllIcons.General.Settings)
    
    // Image attachment button
    private val imageButton = JButton(AllIcons.FileTypes.Image).apply {
        toolTipText = "Attach Image (Ctrl+V to paste)"
        preferredSize = Dimension(32, 28)
        isBorderPainted = false
        isContentAreaFilled = false
        isVisible = onImageClick != null
    }
    
    // Image count badge label
    private val imageCountLabel = JBLabel().apply {
        isVisible = false
        foreground = JBColor(Color(0, 120, 212), Color(100, 180, 255))
    }

    private var availableConfigs: List<NamedModelConfig> = emptyList()
    private var onConfigSelect: (NamedModelConfig) -> Unit = {}
    private var onConfigureClick: () -> Unit = {}
    private var onAddNewConfig: () -> Unit = {}
    private var onRefreshCopilot: () -> Unit = {}
    private var isProcessing = false
    private var isEnhancing = false
    private var isRefreshingCopilot = false
    private var imageCount = 0
    
    // Refresh GitHub Copilot button (only shown when Copilot is configured)
    private val refreshCopilotButton = JButton(AllIcons.Actions.Refresh).apply {
        toolTipText = "Refresh GitHub Copilot Models"
        preferredSize = Dimension(28, 28)
        isBorderPainted = false
        isContentAreaFilled = false
        isVisible = GithubCopilotDetector.isGithubCopilotConfigured()
        addActionListener { onRefreshCopilot() }
    }

    init {
        border = JBUI.Borders.empty(4)
        isOpaque = false

        // Left side: Model selector and token info
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false

            modelComboBox.preferredSize = Dimension(150, 28)
            modelComboBox.addActionListener {
                val selectedIndex = modelComboBox.selectedIndex
                if (selectedIndex >= 0 && selectedIndex < availableConfigs.size) {
                    onConfigSelect(availableConfigs[selectedIndex])
                }
            }
            add(modelComboBox)

            // Add New Config button
            val addConfigButton = JButton(AllIcons.General.Add).apply {
                toolTipText = "Add New Config"
                preferredSize = Dimension(28, 28)
                isBorderPainted = false
                isContentAreaFilled = false
                addActionListener { onAddNewConfig() }
            }
            add(addConfigButton)
            
            // Refresh GitHub Copilot button
            add(refreshCopilotButton)

            tokenLabel.foreground = JBUI.CurrentTheme.Label.disabledForeground()
            add(tokenLabel)
        }
        add(leftPanel, BorderLayout.WEST)

        // Right side: Action buttons
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false

            // Image attachment button (only if callback is provided)
            if (onImageClick != null) {
                imageButton.addActionListener { onImageClick.invoke() }
                add(imageButton)
                add(imageCountLabel)
            }

            settingsButton.apply {
                toolTipText = "MCP Configuration"
                preferredSize = Dimension(32, 28)
                isBorderPainted = false
                isContentAreaFilled = false
                addActionListener { IdeaMcpConfigDialogWrapper.show(project) }
            }
            add(settingsButton)

            optimizeButton.apply {
                toolTipText = "Enhance prompt with AI"
                preferredSize = Dimension(32, 28)
                isBorderPainted = false
                isContentAreaFilled = false
                addActionListener { onPromptOptimizationClick() }
            }
            add(optimizeButton)

            sendButton.apply {
                preferredSize = Dimension(80, 28)
                addActionListener { onSendClick() }
            }
            add(sendButton)

            stopButton.apply {
                preferredSize = Dimension(80, 28)
                isVisible = false
                addActionListener { onStopClick() }
            }
            add(stopButton)
        }
        add(rightPanel, BorderLayout.EAST)
    }

    fun setProcessing(processing: Boolean) {
        isProcessing = processing
        sendButton.isVisible = !processing
        stopButton.isVisible = processing
        optimizeButton.isEnabled = !processing && !isEnhancing
    }

    fun setSendEnabled(enabled: Boolean) {
        sendButton.isEnabled = enabled
    }

    fun setEnhancing(enhancing: Boolean) {
        isEnhancing = enhancing
        optimizeButton.isEnabled = !enhancing && !isProcessing
        optimizeButton.toolTipText = if (enhancing) "Enhancing prompt..." else "Enhance prompt with AI"
    }

    fun setTotalTokens(tokens: Int?) {
        tokenLabel.text = if (tokens != null && tokens > 0) "${tokens}t" else ""
    }

    fun setAvailableConfigs(configs: List<NamedModelConfig>) {
        availableConfigs = configs
        modelComboBox.removeAllItems()
        configs.forEach { modelComboBox.addItem(it.name) }
    }

    fun setCurrentConfigName(name: String?) {
        if (name != null) {
            val index = availableConfigs.indexOfFirst { it.name == name }
            if (index >= 0) {
                modelComboBox.selectedIndex = index
            }
        }
    }

    fun setOnConfigSelect(callback: (NamedModelConfig) -> Unit) {
        onConfigSelect = callback
    }

    fun setOnConfigureClick(callback: () -> Unit) {
        onConfigureClick = callback
    }

    fun setOnAddNewConfig(callback: () -> Unit) {
        onAddNewConfig = callback
    }
    
    fun setOnRefreshCopilot(callback: () -> Unit) {
        onRefreshCopilot = callback
    }
    
    fun setRefreshingCopilot(refreshing: Boolean) {
        isRefreshingCopilot = refreshing
        refreshCopilotButton.isEnabled = !refreshing
        refreshCopilotButton.toolTipText = if (refreshing) "Refreshing Copilot..." else "Refresh GitHub Copilot Models"
    }
    
    fun updateCopilotAvailability() {
        refreshCopilotButton.isVisible = GithubCopilotDetector.isGithubCopilotConfigured()
    }
    
    /**
     * Update the toolbar based on multimodal state.
     * Shows image count badge when images are attached.
     */
    fun updateMultimodalState(state: IdeaMultimodalState) {
        imageCount = state.imageCount
        
        if (state.hasImages) {
            imageCountLabel.text = "${state.imageCount}"
            imageCountLabel.isVisible = true
            imageButton.toolTipText = when {
                state.isUploading -> "Uploading ${state.uploadingCount} image(s)..."
                state.allImagesUploaded -> "${state.imageCount} image(s) ready"
                state.hasUploadError -> "Some uploads failed - click to manage"
                else -> "Attach Image (${state.imageCount} attached)"
            }
            
            // Change icon color based on state
            imageButton.foreground = when {
                state.hasUploadError -> JBColor.RED
                state.allImagesUploaded -> JBColor(Color(0, 120, 212), Color(100, 180, 255))
                else -> null
            }
        } else {
            imageCountLabel.isVisible = false
            imageButton.toolTipText = "Attach Image (Ctrl+V to paste)"
            imageButton.foreground = null
        }
        
        // Update send button state based on multimodal state
        sendButton.isEnabled = state.canSend && !isProcessing
    }
    
    /**
     * Enable or disable the image button.
     */
    fun setImageEnabled(enabled: Boolean) {
        imageButton.isEnabled = enabled
    }
}

