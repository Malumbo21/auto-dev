package cc.unitmesh.devins.idea.omnibar

import cc.unitmesh.devins.idea.omnibar.model.OmnibarActionResult
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.LocalFileSystem

/**
 * Action to open the Omnibar command palette.
 * 
 * Registered shortcuts:
 * - Cmd+K (macOS) / Ctrl+K (Windows/Linux)
 * - Cmd+Shift+P (macOS) / Ctrl+Shift+P (Windows/Linux)
 */
class IdeaOmnibarAction : AnAction(), DumbAware {
    
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        IdeaOmnibarDialogWrapper.show(project) { result ->
            handleActionResult(e, result)
        }
    }
    
    private fun handleActionResult(e: AnActionEvent, result: OmnibarActionResult) {
        val project = e.project ?: return
        
        when (result) {
            is OmnibarActionResult.InsertText -> {
                // Insert text into the current input area
                // This could be handled by the AutoDevInputService or similar
                insertTextToActiveInput(project, result.text)
            }
            is OmnibarActionResult.Navigate -> {
                // Navigate to file/symbol
                navigateToPath(project, result.path, result.line)
            }
            is OmnibarActionResult.LLMQuery -> {
                // Send query to LLM
                sendToLLM(project, result.query)
            }
            is OmnibarActionResult.Success -> {
                // Nothing special to do
            }
            is OmnibarActionResult.Error -> {
                // Could show notification
            }
            is OmnibarActionResult.ShowSubmenu -> {
                // Could re-show omnibar with filtered items
            }
        }
    }
    
    private fun insertTextToActiveInput(project: com.intellij.openapi.project.Project, text: String) {
        // Try to find and use AutoInputService or similar
        try {
            val serviceClass = Class.forName("cc.unitmesh.devti.sketch.AutoInputService")
            val getInstance = serviceClass.getMethod("getInstance", com.intellij.openapi.project.Project::class.java)
            val service = getInstance.invoke(null, project)
            val putText = serviceClass.getMethod("putText", String::class.java)
            putText.invoke(service, text)
        } catch (e: Exception) {
            // Fallback: Could show in a notification or editor
        }
    }
    
    private fun navigateToPath(project: com.intellij.openapi.project.Project, path: String, line: Int) {
        val file = LocalFileSystem.getInstance().findFileByPath(path) ?: return
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFile(file, true)
        
        if (line > 0) {
            val editor = fileEditorManager.selectedTextEditor
            editor?.caretModel?.moveToLogicalPosition(
                com.intellij.openapi.editor.LogicalPosition(line - 1, 0)
            )
        }
    }
    
    private fun sendToLLM(project: com.intellij.openapi.project.Project, query: String) {
        // Could integrate with AgentService or similar
        insertTextToActiveInput(project, query)
    }
}

