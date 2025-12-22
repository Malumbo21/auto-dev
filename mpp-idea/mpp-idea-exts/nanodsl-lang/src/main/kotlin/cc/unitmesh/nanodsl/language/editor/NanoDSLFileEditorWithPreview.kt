package cc.unitmesh.nanodsl.language.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class NanoDSLFileEditorWithPreview(
    private val ourEditor: TextEditor,
    @JvmField var preview: NanoDSLPreviewEditor,
    private val project: Project,
) : TextEditorWithPreview(
    ourEditor, preview,
    "NanoDSL Split Editor",
    Layout.SHOW_EDITOR_AND_PREVIEW,
) {
    val virtualFile: VirtualFile = ourEditor.file

    init {
        preview.setMainEditor(ourEditor.editor)
        ourEditor.editor.scrollingModel.addVisibleAreaListener(MyVisibleAreaListener(), this)
    }

    override fun dispose() {
        TextEditorProvider.getInstance().disposeEditor(ourEditor)
    }

    override fun createToolbar(): ActionToolbar {
        return ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, createActionGroup(project), true)
            .also {
                it.targetComponent = editor.contentComponent
            }
    }

    private fun createActionGroup(project: Project): ActionGroup {
        return DefaultActionGroup(
            object : AnAction(
                "Refresh Preview",
                "Refresh the NanoDSL preview",
                AllIcons.Actions.Preview
            ) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = !DumbService.isDumb(project)
                }

                override fun actionPerformed(e: AnActionEvent) {
                    DumbService.getInstance(project).runWhenSmart {
                        preview.component.isVisible = true
                        preview.updateDisplayedContent()
                    }
                }
            },
        )
    }

    private inner class MyVisibleAreaListener : VisibleAreaListener {
        private var previousLine = 0

        override fun visibleAreaChanged(e: VisibleAreaEvent) {
            val editor = e.editor
            val offset = editor.scrollingModel.visibleArea.y
            val line = editor.document.getLineNumber(
                editor.logicalPositionToOffset(editor.xyToLogicalPosition(java.awt.Point(0, offset)))
            )

            if (line != previousLine) {
                previousLine = line
            }
        }
    }
}

