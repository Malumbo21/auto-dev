package cc.unitmesh.nanodsl.language.editor

import cc.unitmesh.nanodsl.language.NanoDSLFileType
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

class NanoDSLSplitEditorProvider : WeighedFileEditorProvider() {
    override fun getEditorTypeId() = "nanodsl-split-editor"
    private val mainProvider: TextEditorProvider = TextEditorProvider.getInstance()
    private val previewProvider: FileEditorProvider = NanoDSLPreviewEditorProvider()

    override fun accept(project: Project, file: VirtualFile) =
        FileTypeRegistry.getInstance().isFileOfType(file, NanoDSLFileType)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val editor = TextEditorProvider.getInstance().createEditor(project, file)
        if (editor.file is LightVirtualFile) {
            return editor
        }

        val mainEditor = mainProvider.createEditor(project, file) as TextEditor
        val preview = previewProvider.createEditor(project, file) as NanoDSLPreviewEditor
        return NanoDSLFileEditorWithPreview(mainEditor, preview, project)
    }

    override fun getPolicy() = FileEditorPolicy.HIDE_OTHER_EDITORS
}

