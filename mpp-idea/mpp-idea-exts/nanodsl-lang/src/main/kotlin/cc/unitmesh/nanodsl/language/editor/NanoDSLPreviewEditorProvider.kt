package cc.unitmesh.nanodsl.language.editor

import cc.unitmesh.nanodsl.language.NanoDSLFileType
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class NanoDSLPreviewEditorProvider : WeighedFileEditorProvider() {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return FileTypeRegistry.getInstance().isFileOfType(file, NanoDSLFileType)
    }

    override fun createEditor(project: Project, virtualFile: VirtualFile): FileEditor {
        return NanoDSLPreviewEditor(project, virtualFile)
    }

    override fun getEditorTypeId(): String = "nanodsl-preview-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}

