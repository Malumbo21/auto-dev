package cc.unitmesh.devins.idea.editor

import cc.unitmesh.nanodsl.language.NanoDSLFileType
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

/**
 * Split editor provider for NanoDSL files with Compose-based preview.
 * 
 * This provider creates a split editor with:
 * - Left: Text editor for editing NanoDSL code
 * - Right: Compose preview using IdeaNanoDSLBlockRenderer
 */
class NanoDSLComposeSplitEditorProvider : WeighedFileEditorProvider() {
    private val mainProvider = TextEditorProvider.getInstance()

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return FileTypeRegistry.getInstance().isFileOfType(file, NanoDSLFileType)
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val editor = TextEditorProvider.getInstance().createEditor(project, file)
        if (editor.file is LightVirtualFile) {
            return editor
        }

        val mainEditor = mainProvider.createEditor(project, file) as TextEditor
        val preview = NanoDSLComposePreviewEditor(project, file)
        
        return NanoDSLComposeFileEditorWithPreview(mainEditor, preview, project)
    }

    override fun getEditorTypeId(): String = "nanodsl-compose-split-editor"

    override fun getPolicy() = FileEditorPolicy.HIDE_OTHER_EDITORS
}

