package cc.unitmesh.devins.idea.editor

import cc.unitmesh.devins.idea.toolwindow.IdeaEngine
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
 * 
 * Features:
 * - Engine selector (AutoDev / ACP) - NEW!
 * - Model/Agent selector (shows LLM configs for AutoDev, ACP agents for ACP)
 * - Send/Stop buttons with multimodal support
 * - Token display, image attachment, prompt optimization
 */
class SwingBottomToolbar(
    private val project: Project?,
    private val onSendClick: () -> Unit,
    private val onStopClick: () -> Unit,
    private val onPromptOptimizationClick: () -> Unit,
    private val onImageClick: (() -> Unit)? = null
) : JPanel(BorderLayout()) {

    // Engine selector (AutoDev / ACP)
    private val engineComboBox = ComboBox<String>()
    
    // Model/Agent selector (content changes based on engine)
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
    
    private val imageCountLabel = JBLabel().apply {
        isVisible = false
        foreground = JBColor(Color(0, 120, 212), Color(100, 180, 255))
    }

    private var availableConfigs: List<NamedModelConfig> = emptyList()
    private var acpAgents: Map<String, cc.unitmesh.config.AcpAgentConfig> = emptyMap()
    private var currentEngine: IdeaEngine = IdeaEngine.AUTODEV
    
    // Callbacks
    private var onConfigSelect: (NamedModelConfig) -> Unit = {}
    private var onConfigureClick: () -> Unit = {}
    private var onAddNewConfig: () -> Unit = {}
    private var onRefreshCopilot: () -> Unit = {}
    private var onAcpAgentSelect: (String) -> Unit = {}
    private var onConfigureAcp: () -> Unit = {}
    private var onSwitchToAutodev: () -> Unit = {}
    private var onSwitchToAcp: () -> Unit = {}
    
    private var isProcessing = false
    private var isEnhancing = false
    private var isRefreshingCopilot = false
    private var imageCount = 0

    // Track ACP agent keys (parallel to combo box items)
    private var acpAgentKeys: List<String> = emptyList()
    private val ACP_CONFIGURE = "Configure ACP..."
    private var isUpdating = false
    
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

        // Left side: Engine + Model selectors + token
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false

            // Engine selector (AutoDev / ACP)
            engineComboBox.preferredSize = Dimension(90, 28)
            engineComboBox.addItem("AutoDev")
            engineComboBox.addItem("ACP")
            engineComboBox.addActionListener {
                if (isUpdating) return@addActionListener
                val selected = engineComboBox.selectedItem as? String
                println("Engine selector changed to: $selected")
                when (selected) {
                    "AutoDev" -> {
                        currentEngine = IdeaEngine.AUTODEV
                        onSwitchToAutodev()
                        rebuildModelComboBox()
                    }
                    "ACP" -> {
                        currentEngine = IdeaEngine.ACP
                        onSwitchToAcp()
                        rebuildModelComboBox()
                    }
                }
            }
            add(engineComboBox)

            // Model/Agent selector
            modelComboBox.preferredSize = Dimension(180, 28)
            modelComboBox.addActionListener {
                if (isUpdating) return@addActionListener
                val selectedIndex = modelComboBox.selectedIndex
                val selectedItem = modelComboBox.selectedItem as? String

                when (currentEngine) {
                    IdeaEngine.AUTODEV -> {
                        if (selectedIndex in availableConfigs.indices) {
                            onConfigSelect(availableConfigs[selectedIndex])
                        }
                    }
                    IdeaEngine.ACP -> {
                        when (selectedItem) {
                            ACP_CONFIGURE -> onConfigureAcp()
                            else -> {
                                if (selectedIndex in acpAgentKeys.indices) {
                                    val agentKey = acpAgentKeys[selectedIndex]
                                    onAcpAgentSelect(agentKey)
                                }
                            }
                        }
                    }
                }
            }
            add(modelComboBox)

            // Add button (Config+ for AutoDev, ACP+ for ACP)
            val addConfigButton = JButton(AllIcons.General.Add).apply {
                preferredSize = Dimension(28, 28)
                isBorderPainted = false
                isContentAreaFilled = false
                addActionListener { 
                    if (currentEngine == IdeaEngine.AUTODEV) {
                        onAddNewConfig()
                    } else {
                        onConfigureAcp()
                    }
                }
            }
            
            // Update tooltip dynamically
            engineComboBox.addItemListener {
                addConfigButton.toolTipText = if (currentEngine == IdeaEngine.AUTODEV) {
                    "Add New LLM Config"
                } else {
                    "Configure ACP Agents"
                }
            }
            addConfigButton.toolTipText = "Add New LLM Config"
            add(addConfigButton)
            
            add(refreshCopilotButton)

            tokenLabel.foreground = JBUI.CurrentTheme.Label.disabledForeground()
            add(tokenLabel)
        }
        add(leftPanel, BorderLayout.WEST)

        // Right side: Action buttons
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false

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
        println("SwingBottomToolbar.setAvailableConfigs: ${configs.size} configs")
        availableConfigs = configs
        if (currentEngine == IdeaEngine.AUTODEV) {
            rebuildModelComboBox()
        }
    }

    fun setAcpAgents(agents: Map<String, cc.unitmesh.config.AcpAgentConfig>) {
        println("SwingBottomToolbar.setAcpAgents: ${agents.size} agents")
        acpAgents = agents
        if (currentEngine == IdeaEngine.ACP) {
            rebuildModelComboBox()
        }
    }

    fun setCurrentEngine(engine: IdeaEngine) {
        isUpdating = true
        try {
            currentEngine = engine
            engineComboBox.selectedIndex = when (engine) {
                IdeaEngine.AUTODEV -> 0
                IdeaEngine.ACP -> 1
            }
            rebuildModelComboBox()
        } finally {
            isUpdating = false
        }
    }

    private fun rebuildModelComboBox() {
        isUpdating = true
        try {
            modelComboBox.removeAllItems()
            acpAgentKeys = emptyList()

            when (currentEngine) {
                IdeaEngine.AUTODEV -> {
                    availableConfigs.forEach { modelComboBox.addItem(it.name) }
                    println("Rebuilt model combo: AutoDev mode, ${availableConfigs.size} items")
                }
                IdeaEngine.ACP -> {
                    val keys = mutableListOf<String>()
                    acpAgents.forEach { (key, config) ->
                        val displayName = config.name.ifBlank { key }
                        modelComboBox.addItem(displayName)
                        keys.add(key)
                    }
                    acpAgentKeys = keys
                    modelComboBox.addItem(ACP_CONFIGURE)
                    println("Rebuilt model combo: ACP mode, ${acpAgents.size} agents + configure")
                }
            }
        } finally {
            isUpdating = false
        }
    }

    fun setCurrentConfigName(name: String?) {
        if (name == null || currentEngine != IdeaEngine.AUTODEV) return
        isUpdating = true
        try {
            val index = availableConfigs.indexOfFirst { it.name == name }
            if (index >= 0) {
                modelComboBox.selectedIndex = index
            }
        } finally {
            isUpdating = false
        }
    }

    fun setCurrentAcpAgent(agentKey: String?) {
        if (agentKey == null || currentEngine != IdeaEngine.ACP) return
        isUpdating = true
        try {
            val index = acpAgentKeys.indexOf(agentKey)
            if (index >= 0) {
                modelComboBox.selectedIndex = index
            }
        } finally {
            isUpdating = false
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
        
        sendButton.isEnabled = state.canSend && !isProcessing
    }
    
    fun setImageEnabled(enabled: Boolean) {
        imageButton.isEnabled = enabled
    }

    fun setOnAcpAgentSelect(callback: (String) -> Unit) {
        onAcpAgentSelect = callback
    }

    fun setOnConfigureAcp(callback: () -> Unit) {
        onConfigureAcp = callback
    }

    fun setOnSwitchToAutodev(callback: () -> Unit) {
        onSwitchToAutodev = callback
    }

    fun setOnSwitchToAcp(callback: () -> Unit) {
        onSwitchToAcp = callback
    }
}
