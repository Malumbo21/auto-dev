package cc.unitmesh.devins.idea.editor

import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm

/**
 * Split editor for NanoDSL files with Compose-based preview.
 * 
 * Features:
 * - Automatic preview update on document changes
 * - Debounced updates to avoid excessive recomposition
 */
class NanoDSLComposeFileEditorWithPreview(
    private val ourEditor: TextEditor,
    @JvmField var preview: NanoDSLComposePreviewEditor,
    private val project: Project,
) : TextEditorWithPreview(
    ourEditor, preview,
    "NanoDSL Split Editor",
    Layout.SHOW_EDITOR_AND_PREVIEW,
) {
    val virtualFile: VirtualFile = ourEditor.file
    private val updateAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val UPDATE_DELAY_MS = 300

    init {
        Disposer.register(this, preview)
        
        // Listen for document changes
        ourEditor.editor.document.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                schedulePreviewUpdate()
            }
        }, this)
    }

    private fun schedulePreviewUpdate() {
        updateAlarm.cancelAllRequests()
        updateAlarm.addRequest({
            preview.updateDisplayedContent()
        }, UPDATE_DELAY_MS)
    }

    override fun dispose() {
        updateAlarm.cancelAllRequests()
        super.dispose()
    }
}

