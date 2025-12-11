package cc.unitmesh.devins.idea.editor.multimodal

import cc.unitmesh.config.ConfigManager
import cc.unitmesh.llm.NamedModelConfig
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.runBlocking
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * Compact vision model selector for IntelliJ IDEA.
 * Shows current model and allows switching between available vision models.
 */
class IdeaVisionModelSelector(
    private val parentDisposable: Disposable,
    private val onModelChange: (NamedModelConfig) -> Unit
) : JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)), Disposable {

    private val iconLabel = JBLabel(AllIcons.General.InspectionsEye)
    private val modelLabel = JBLabel()
    private val dropdownIcon = JBLabel(AllIcons.General.ArrowDown)
    
    private var currentModel: String = "glm-4.6v"
    private var availableModels: List<NamedModelConfig> = emptyList()

    init {
        Disposer.register(parentDisposable, this)
        
        isOpaque = false
        border = JBUI.Borders.empty()
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        
        // Icon
        iconLabel.apply {
            foreground = UIUtil.getLabelInfoForeground()
        }
        add(iconLabel)
        
        // Model name label
        modelLabel.apply {
            text = "Vision: $currentModel"
            font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 11f)
            foreground = JBColor(Color(0, 120, 212), Color(100, 180, 255))
        }
        add(modelLabel)
        
        // Dropdown arrow (hidden if only one model)
        dropdownIcon.apply {
            isVisible = false
        }
        add(dropdownIcon)
        
        // Load available models
        loadAvailableModels()
        
        // Click handler
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (availableModels.size > 1) {
                    showModelPopup(e.point)
                }
            }
        })
    }
    
    private fun loadAvailableModels() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val configs = runBlocking { ConfigManager.load().getAllConfigs() }
                ApplicationManager.getApplication().invokeLater {
                    availableModels = configs
                    dropdownIcon.isVisible = configs.size > 1
                    
                    // Find and set the active vision model if available
                    configs.find { it.name == currentModel }?.let { model ->
                        updateCurrentModel(model.name)
                    }
                }
            } catch (e: Exception) {
                println("Failed to load vision models: ${e.message}")
            }
        }
    }
    
    private fun showModelPopup(point: Point) {
        val step = object : BaseListPopupStep<NamedModelConfig>("Select Vision Model", availableModels) {
            override fun getTextFor(value: NamedModelConfig): String = value.name
            
            override fun getIconFor(value: NamedModelConfig): Icon? {
                return if (value.name == currentModel) AllIcons.Actions.Checked else null
            }
            
            override fun onChosen(selectedValue: NamedModelConfig, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    ApplicationManager.getApplication().invokeLater {
                        updateCurrentModel(selectedValue.name)
                        onModelChange(selectedValue)
                    }
                }
                return PopupStep.FINAL_CHOICE
            }
        }
        
        val popup = JBPopupFactory.getInstance().createListPopup(step)
        popup.show(RelativePoint(this, point))
    }
    
    /**
     * Update the current model display.
     */
    fun updateCurrentModel(modelName: String) {
        currentModel = modelName
        modelLabel.text = "Vision: $modelName"
    }
    
    /**
     * Get the current model name.
     */
    fun getCurrentModel(): String = currentModel
    
    override fun dispose() {
        // Cleanup if needed
    }
}

