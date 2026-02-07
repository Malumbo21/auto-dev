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
        thisLogger().warn("=== createToolWindowContent START === Project: ${project.name}")

        // Enable custom popup rendering to use JBPopup instead of default Compose implementation
        // This fixes z-index issues when Compose Popup is used with SwingPanel (e.g., EditorTextField)
        // See: JewelFlags.useCustomPopupRenderer in Jewel foundation
        System.setProperty("jewel.customPopupRender", "true")
        thisLogger().warn("jewel.customPopupRender property set")

        createAgentPanel(project, toolWindow)
        thisLogger().warn("=== createToolWindowContent END ===")
    }

    override fun shouldBeAvailable(project: Project): Boolean = true

    private fun createAgentPanel(project: Project, toolWindow: ToolWindow) {
        thisLogger().warn("createAgentPanel() called")
        val contentManager = toolWindow.contentManager

        // Check if Agent content already exists to prevent duplicate creation
        // This can happen when the tool window is hidden and restored, or when squeezed by other windows
        val existingContent = contentManager.findContent("Agent")
        if (existingContent != null) {
            thisLogger().warn("Agent content already exists - reusing existing content")
            contentManager.setSelectedContent(existingContent)
            return
        }

        thisLogger().warn("Creating new Agent content")
        val toolWindowDisposable = toolWindow.disposable

        // Create ViewModel OUTSIDE of Compose to prevent recreation when Compose tree is rebuilt
        // Jewel's addComposeTab may rebuild the Compose tree multiple times during initialization
        thisLogger().warn("Creating coroutine scope with Dispatchers.Main")
        val coroutineScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main
        )

        thisLogger().warn("Creating IdeaAgentViewModel...")
        val viewModel = IdeaAgentViewModel(project, coroutineScope)
        thisLogger().warn("IdeaAgentViewModel created - registering disposable")
        Disposer.register(toolWindowDisposable, viewModel)

        Disposer.register(toolWindowDisposable) {
            thisLogger().warn("ToolWindow disposable triggered - cancelling coroutine scope")
            coroutineScope.cancel()
        }

        thisLogger().warn("Adding Compose tab to tool window...")
        toolWindow.addComposeTab("Agent") {
            thisLogger().warn("IdeaAgentApp composable invoked")
            IdeaAgentApp(viewModel, project, coroutineScope)
        }
        thisLogger().warn("Compose tab added successfully")
    }

    private fun kotlinx.coroutines.CoroutineScope.cancel() {
        (coroutineContext[Job] as? Job)?.cancel()
    }
}
