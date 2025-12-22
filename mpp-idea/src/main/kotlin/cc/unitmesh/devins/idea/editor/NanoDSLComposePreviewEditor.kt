package cc.unitmesh.devins.idea.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.idea.renderer.nano.IdeaNanoRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.jewel.bridge.JewelComposePanel
import java.beans.PropertyChangeListener
import java.nio.charset.StandardCharsets
import javax.swing.JComponent

/**
 * Preview editor for NanoDSL files using Compose with IdeaNanoRenderer.
 *
 * This editor provides:
 * - Live UI preview of NanoDSL components using Jewel
 * - Parses NanoDSL source code and renders actual UI components
 * - Native IntelliJ look and feel
 */
open class NanoDSLComposePreviewEditor(
    val project: Project,
    val virtualFile: VirtualFile,
) : UserDataHolder by UserDataHolderBase(), FileEditor, Disposable {
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
    private var mainEditor = MutableStateFlow<Editor?>(null)

    private var currentContent = mutableStateOf("")

    private val composePanel = JewelComposePanel {
        val content by remember { currentContent }
        val scrollState = rememberScrollState()

        IdeaNanoRenderer.RenderFromSource(
            source = content,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        )
    }

    init {
        updateDisplayedContent()
    }

    fun updateDisplayedContent() {
        try {
            val content = runReadAction {
                String(virtualFile.contentsToByteArray(), StandardCharsets.UTF_8)
            }
            currentContent.value = content
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setMainEditor(editor: Editor) {
        check(mainEditor.value == null)
        mainEditor.value = editor
    }

    override fun getComponent(): JComponent = composePanel
    override fun getName(): String = "NanoDSL Preview"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun getFile(): VirtualFile = virtualFile
    override fun getPreferredFocusedComponent(): JComponent? = null
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    override fun dispose() {
        // Compose panel cleanup is handled automatically
    }
}

