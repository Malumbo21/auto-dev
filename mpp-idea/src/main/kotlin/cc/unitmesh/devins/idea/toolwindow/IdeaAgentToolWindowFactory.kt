package cc.unitmesh.devins.idea.toolwindow

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import kotlinx.coroutines.Job
import org.jetbrains.jewel.bridge.addComposeTab

/**
 * Factory for creating the Agent ToolWindow with tab-based navigation.
 *
 * Features:
 * - Tab-based agent type switching (similar to TopBarMenuDesktop from mpp-ui)
 * - Agentic: Full coding agent with file operations
 * - Review: Code review and analysis
 * - Knowledge: Document reading and Q&A
 * - Remote: Connect to remote mpp-server
 *
 * Uses Jewel theme for native IntelliJ IDEA integration (2025.2+).
 */
class IdeaAgentToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().info("IdeaAgentToolWindowFactory initialized - Agent Tabs UI for IntelliJ IDEA 252+")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        thisLogger().warn("createToolWindowContent called - project: ${project.name}")

        // Enable custom popup rendering to use JBPopup instead of default Compose implementation
        // This fixes z-index issues when Compose Popup is used with SwingPanel (e.g., EditorTextField)
        // See: JewelFlags.useCustomPopupRenderer in Jewel foundation
        System.setProperty("jewel.customPopupRender", "true")
        createAgentPanel(project, toolWindow)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true

    private fun createAgentPanel(project: Project, toolWindow: ToolWindow) {
        val contentManager = toolWindow.contentManager

        // Check if Agent content already exists to prevent duplicate creation
        // This can happen when the tool window is hidden and restored, or when squeezed by other windows
        val existingContent = contentManager.findContent("Agent")
        if (existingContent != null) {
            contentManager.setSelectedContent(existingContent)
            return
        }

        val toolWindowDisposable = toolWindow.disposable

        // Create ViewModel OUTSIDE of Compose to prevent recreation when Compose tree is rebuilt
        // Jewel's addComposeTab may rebuild the Compose tree multiple times during initialization
        val coroutineScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main
        )
        val viewModel = IdeaAgentViewModel(project, coroutineScope)
        Disposer.register(toolWindowDisposable, viewModel)

        Disposer.register(toolWindowDisposable) {
            coroutineScope.cancel()
        }

        toolWindow.addComposeTab("Agent") {
            IdeaAgentApp(viewModel, project, coroutineScope)
        }
    }

    private fun kotlinx.coroutines.CoroutineScope.cancel() {
        (coroutineContext[Job] as? Job)?.cancel()
    }
}
