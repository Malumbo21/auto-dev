package cc.unitmesh.nanodsl.language.sketch

import cc.unitmesh.devti.sketch.ui.ExtensionLangSketch
import cc.unitmesh.devti.sketch.ui.code.CodeHighlightSketch
import cc.unitmesh.nanodsl.language.NanoDSLLanguage
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * NanoDSL Language Sketch for rendering NanoDSL code blocks in SketchToolWindow.
 * 
 * This sketch provides:
 * - Syntax highlighted code view using NanoDSL language
 * - Header with NanoDSL label
 * - Consistent styling with other language sketches
 */
class NanoDSLLangSketch(
    private val project: Project,
    private var content: String
) : ExtensionLangSketch {
    
    private val mainPanel = JPanel(VerticalLayout(2))
    private var codeSketch: CodeHighlightSketch? = null
    
    init {
        initializeUI()
    }
    
    private fun initializeUI() {
        mainPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(4, 4, 4, 4),
            RoundedLineBorder(JBColor.border(), 8, 1)
        )
        
        // Header panel
        val headerPanel = JPanel(BorderLayout()).apply {
            background = JBColor(0xF5F5F5, 0x2B2D30)
            border = JBUI.Borders.empty(8, 12)
            
            val label = JBLabel("NanoDSL").apply {
                fontColor = UIUtil.FontColor.BRIGHTER
                font = JBUI.Fonts.label(12.0f).asBold()
            }
            add(label, BorderLayout.WEST)
        }
        mainPanel.add(headerPanel)
        
        // Code highlight sketch
        codeSketch = CodeHighlightSketch(
            project = project,
            text = content,
            ideaLanguage = NanoDSLLanguage,
            editorLineThreshold = 20,
            fileName = "preview.nanodsl",
            withLeftRightBorder = false,
            showToolbar = true
        )
        codeSketch?.let { mainPanel.add(it) }
    }
    
    override fun getExtensionName(): String = "NanoDSL"
    
    override fun getViewText(): String = content
    
    override fun updateViewText(text: String, complete: Boolean) {
        this.content = text
        codeSketch?.updateViewText(text, complete)
    }
    
    override fun updateLanguage(language: Language?, originLanguage: String?) {
        // NanoDSL language is fixed
    }
    
    override fun getComponent(): JComponent = mainPanel
    
    override fun onDoneStream(allText: String) {
        codeSketch?.onDoneStream(allText)
    }
    
    override fun onComplete(code: String) {
        // Nothing special needed on complete
    }
    
    override fun dispose() {
        codeSketch?.dispose()
    }
}

