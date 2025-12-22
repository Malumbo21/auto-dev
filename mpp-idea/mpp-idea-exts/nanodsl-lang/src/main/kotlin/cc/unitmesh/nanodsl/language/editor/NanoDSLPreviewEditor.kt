package cc.unitmesh.nanodsl.language.editor

import cc.unitmesh.devti.sketch.ui.code.CodeHighlightSketch
import cc.unitmesh.devti.sketch.ui.patch.readText
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.flow.MutableStateFlow
import org.intellij.lang.annotations.Language
import java.beans.PropertyChangeListener
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import java.awt.BorderLayout

/**
 * Preview editor for NanoDSL files.
 * Shows the DSL source with syntax highlighting and a preview of the component structure.
 */
open class NanoDSLPreviewEditor(
    val project: Project,
    val virtualFile: VirtualFile,
) : UserDataHolder by UserDataHolderBase(), FileEditor {
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
    private var mainEditor = MutableStateFlow<Editor?>(null)
    private val mainPanel = JPanel(BorderLayout())
    private val visualPanel: JBScrollPane = JBScrollPane(
        mainPanel,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    )

    private var highlightSketch: CodeHighlightSketch? = null

    init {
        createUI()
    }

    private fun createUI() {
        val corePanel = panel {
            row {
                val label = JBLabel("NanoDSL Preview").apply {
                    fontColor = UIUtil.FontColor.BRIGHTER
                    background = JBColor(0xF5F5F5, 0x2B2D30)
                    font = JBUI.Fonts.label(16.0f).asBold()
                    border = JBUI.Borders.empty(0, 16)
                    isOpaque = true
                }
                cell(label).align(Align.FILL).resizableColumn()

                button("", object : AnAction() {
                    override fun actionPerformed(event: AnActionEvent) {
                        updateDisplayedContent()
                    }
                }).also {
                    it.component.icon = AllIcons.Actions.Refresh
                    it.component.preferredSize = JBUI.size(24, 24)
                }
            }

            row {
                cell(JBLabel("Component Structure").apply {
                    fontColor = UIUtil.FontColor.BRIGHTER
                    background = JBColor(0xF5F5F5, 0x2B2D30)
                    font = JBUI.Fonts.label(14.0f).asBold()
                    border = JBUI.Borders.empty(0, 16)
                    isOpaque = true
                }).align(Align.FILL).resizableColumn()
            }

            row {
                highlightSketch = CodeHighlightSketch(project, "", cc.unitmesh.nanodsl.language.NanoDSLLanguage, 18).apply {
                    initEditor("Click refresh to see the NanoDSL content")
                }
                highlightSketch?.editorFragment?.setCollapsed(false)
                highlightSketch?.editorFragment?.updateExpandCollapseLabel()

                val panel = JPanel(BorderLayout())
                panel.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(12, 12, 12, 12),
                    RoundedLineBorder(JBColor.border(), 8, 1)
                )
                highlightSketch?.let { panel.add(it, BorderLayout.CENTER) }

                cell(panel).align(Align.FILL)
            }
        }

        this.mainPanel.add(corePanel, BorderLayout.CENTER)
        updateDisplayedContent()
    }

    fun updateDisplayedContent() {
        try {
            val content = runReadAction { virtualFile.readText() }
            highlightSketch?.updateViewText(content, true)
            highlightSketch?.repaint()
            mainPanel.revalidate()
            mainPanel.repaint()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setMainEditor(editor: Editor) {
        check(mainEditor.value == null)
        mainEditor.value = editor
    }

    override fun getComponent(): JComponent = visualPanel
    override fun getName(): String = "NanoDSL Preview"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun getFile(): VirtualFile = virtualFile
    override fun getPreferredFocusedComponent(): JComponent? = null
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun dispose() {}
}

