package cc.unitmesh.devins.idea.toolwindow.codereview

import cc.unitmesh.devins.idea.renderer.JewelRenderer
import cc.unitmesh.devins.workspace.DefaultWorkspace
import cc.unitmesh.devins.workspace.Workspace
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for Code Review in IntelliJ IDEA plugin.
 *
 * Manages the state and business logic for code review functionality:
 * - Git operations (fetch commits, diffs)
 * - AI-powered analysis
 * - Plan generation and fix generation
 *
 * This implementation is specific to IntelliJ IDEA and uses:
 * - JewelRenderer for native IntelliJ theme integration
 * - IntelliJ Project for workspace context
 * - Disposable for proper resource cleanup
 */
class IdeaCodeReviewViewModel(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) : Disposable {

    private val logger = Logger.getInstance(IdeaCodeReviewViewModel::class.java)

    // State management
    private val _state = MutableStateFlow(CodeReviewState())
    val state: StateFlow<CodeReviewState> = _state.asStateFlow()

    // JewelRenderer for IntelliJ native theme
    val jewelRenderer = JewelRenderer()

    // Workspace
    val workspace: Workspace = createWorkspaceFromProject(project)

    // Public methods for UI interactions
    fun selectCommit(index: Int) {
        val currentIndices = _state.value.selectedCommitIndices.toMutableSet()
        if (currentIndices.contains(index)) {
            currentIndices.remove(index)
        } else {
            currentIndices.add(index)
        }
        _state.value = _state.value.copy(selectedCommitIndices = currentIndices)
    }

    fun startAnalysis() {
        logger.info("Starting code review analysis")
        _state.value = _state.value.copy(
            stage = AnalysisStage.RUNNING_LINT,
            isLoading = true
        )
    }

    fun cancelAnalysis() {
        logger.info("Cancelling code review analysis")
        _state.value = _state.value.copy(
            stage = AnalysisStage.IDLE,
            isLoading = false
        )
    }

    fun proceedToGenerateFixes() {
        logger.info("Proceeding to generate fixes")
        _state.value = _state.value.copy(
            stage = AnalysisStage.GENERATING_FIX
        )
    }

    fun refreshIssueForCommit(commitIndex: Int) {
        logger.info("Refreshing issue for commit at index: $commitIndex")
        // TODO: Implement issue refresh logic
    }

    companion object {
        /**
         * Create a Workspace from an IntelliJ Project
         */
        private fun createWorkspaceFromProject(project: Project): Workspace {
            val projectPath = project.basePath
            val projectName = project.name

            return if (projectPath != null) {
                DefaultWorkspace.create(projectName, projectPath)
            } else {
                DefaultWorkspace.createEmpty(projectName)
            }
        }
    }

    /**
     * Open a file in the IDE editor
     */
    fun openFileViewer(path: String) {
        val basePath = project.basePath ?: run {
            logger.warn("Cannot open file: project basePath is null")
            return
        }
        val file = java.io.File(basePath, path)
        if (!file.exists()) {
            logger.warn("File not found in openFileViewer: ${file.path}")
            return
        }

        val localFileSystem = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            val virtualFile = localFileSystem.refreshAndFindFileByIoFile(file)
            if (virtualFile != null) {
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(virtualFile, true)
            } else {
                logger.warn("VirtualFile not found for file: ${file.path}")
            }
        }
    }

    /**
     * Dispose resources when the ViewModel is no longer needed
     */
    override fun dispose() {
        logger.info("Disposing IdeaCodeReviewViewModel")
        // The parent class cleanup will happen when the scope is cancelled
    }
}
