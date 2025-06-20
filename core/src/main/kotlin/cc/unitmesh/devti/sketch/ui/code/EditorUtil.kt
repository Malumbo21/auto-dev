package cc.unitmesh.devti.sketch.ui.code

import cc.unitmesh.devti.AutoDevSnippetFile
import cc.unitmesh.devti.util.parser.CodeFence
import cc.unitmesh.devti.util.whenDisposed
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import java.util.concurrent.atomic.AtomicBoolean

object EditorUtil {
    private val LINE_NO_REGEX = Regex("^\\d+:")

    fun createCodeViewerEditor(
        project: Project,
        text: String,
        ideaLanguage: Language?,
        fileName: String?,
        disposable: Disposable,
    ): EditorEx {
        // 处理可能的混合行分隔符问题，特别是Windows风格的\r\n
        // Handle potential mixed line separators, especially Windows-style \r\n
        var editorText = text
        if (text.contains("\r\n")) {
            editorText = text.replace("\r\n", "\n")
        }
        
        val language = ideaLanguage ?: PlainTextLanguage.INSTANCE
        val ext = if (language.displayName == PlainTextLanguage.INSTANCE.displayName) {
            CodeFence.lookupFileExt(language.displayName)
        } else {
            language.associatedFileType?.defaultExtension ?: "Unknown"
        }

        /// check text easy line starts with Lineno and : for example, 1:
        var isShowLineNo = true
        editorText.lines().forEach {
            if (!it.matches(LINE_NO_REGEX)) {
                isShowLineNo = false
                return@forEach
            }
        }

        if (isShowLineNo) {
            val newLines = editorText.lines().map { it.replace(LINE_NO_REGEX, "") }
            editorText = newLines.joinToString("\n")
        }

        val file: VirtualFile = if (fileName != null) {
            LightVirtualFile(fileName, language, editorText)
        } else {
            LightVirtualFile(AutoDevSnippetFile.naming(ext), language, editorText)
        }

        val document: Document = file.findDocument() ?: throw IllegalStateException("Document not found")
        return createCodeViewerEditor(project, file, document, disposable, isShowLineNo)
    }

     fun createCodeViewerEditor(
        project: Project,
        file: VirtualFile,
        document: Document,
        disposable: Disposable,
        isShowLineNo: Boolean? = false,
    ): EditorEx {
        val editor: EditorEx = ReadAction.compute<EditorEx, Throwable> {
            if (project.isDisposed) return@compute throw IllegalStateException("Project is disposed")

            try {
                EditorFactory.getInstance().createViewer(document, project, EditorKind.PREVIEW) as? EditorEx
            } catch (e: Throwable) {
                throw e
            }
        }

        val isReleased = AtomicBoolean(false)
        disposable.whenDisposed {
            if (isReleased.compareAndSet(false, true)) {
                try {
                    if (!editor.isDisposed) {
                        EditorFactory.getInstance().releaseEditor(editor)
                    }
                } catch (e: Exception) {
                    // Log the exception but don't let it propagate
                    // to avoid breaking the disposal chain
                }
            }
        }

        editor.setFile(file)
        return configEditor(editor, project, file, isShowLineNo)
    }

    fun configEditor(
        editor: EditorEx,
        project: Project,
        file: VirtualFile,
        isShowLineNo: Boolean?
    ): EditorEx {
        editor.setCaretEnabled(true)

        val highlighter = ApplicationManager.getApplication()
            .getService(EditorHighlighterFactory::class.java)
            .createEditorHighlighter(project, file)

        try {
            editor.highlighter = highlighter
        } catch (e: Throwable) {
            /// do nothing
        }

        val markupModel: MarkupModelEx = editor.markupModel
        (markupModel as EditorMarkupModel).isErrorStripeVisible = false

        val settings = editor.settings.also {
            it.isDndEnabled = false
            it.isLineNumbersShown = isShowLineNo == true
            it.additionalLinesCount = 0
            it.isLineMarkerAreaShown = false
            it.isFoldingOutlineShown = false
            it.isRightMarginShown = false
            it.isShowIntentionBulb = false
            it.isUseSoftWraps = true
            it.isRefrainFromScrolling = true
            it.isAdditionalPageAtBottom = false
            it.isCaretRowShown = false
        }

        editor.scrollPane.verticalScrollBar.isVisible = false
        editor.scrollPane.horizontalScrollBar.isVisible = false

        editor.scrollPane.background = editor.backgroundColor

        editor.addFocusListener(object : FocusChangeListener {
            override fun focusGained(focusEditor: Editor) {
                settings.isCaretRowShown = true
            }

            override fun focusLost(focusEditor: Editor) {
                if (focusEditor.isDisposed) return
                if (editor.isDisposed) return

                settings.isCaretRowShown = false
                editor.markupModel.removeAllHighlighters()
            }
        })

        return editor
    }
}