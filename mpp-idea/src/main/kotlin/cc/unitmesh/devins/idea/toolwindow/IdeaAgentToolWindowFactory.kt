package cc.unitmesh.devins.idea.toolwindow

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import cc.unitmesh.devins.idea.compose.rememberIdeaCoroutineScope
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
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
        // Enable custom popup rendering to use JBPopup instead of default Compose implementation
        // This fixes z-index issues when Compose Popup is used with SwingPanel (e.g., EditorTextField)
        // See: JewelFlags.useCustomPopupRenderer in Jewel foundation
        System.setProperty("jewel.customPopupRender", "true")
        createAgentPanel(project, toolWindow)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true

    private fun createAgentPanel(project: Project, toolWindow: ToolWindow) {
        val toolWindowDisposable = toolWindow.disposable

        toolWindow.addComposeTab("Agent") {
            // Use rememberIdeaCoroutineScope instead of rememberCoroutineScope to avoid
            // ForgottenCoroutineScopeException when composition is left during LLM streaming.
            // IntelliJ's CoroutineScopeHolder service provides a scope tied to project lifecycle.
            val coroutineScope = rememberIdeaCoroutineScope(project)
            val viewModel = remember { IdeaAgentViewModel(project, coroutineScope) }

            // Register ViewModel with tool window's disposable to ensure proper cleanup
            // This fixes the memory leak where ViewModel was registered with ROOT_DISPOSABLE
            DisposableEffect(viewModel) {
                Disposer.register(toolWindowDisposable, viewModel)
                onDispose {
                    // ViewModel will be disposed when toolWindowDisposable is disposed
                    // No need to manually dispose here as Disposer handles the hierarchy
                }
            }

            IdeaAgentApp(viewModel, project, coroutineScope)
        }
    }
}

