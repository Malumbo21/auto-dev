package cc.unitmesh.devins.ui.compose.state

import androidx.compose.runtime.*
import cc.unitmesh.agent.AgentType
import cc.unitmesh.config.AutoDevConfigWrapper
import cc.unitmesh.devins.ui.state.UIStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Desktop UI State ViewModel
 * 管理桌面端 UI 的所有状态，同步全局 UIStateManager
 */
class DesktopUiState(initialAgentType: AgentType = AgentType.CODING) {
    private val scope = CoroutineScope(Dispatchers.Default)

    // Agent Type
    var currentAgentType by mutableStateOf(initialAgentType)

    // Sidebar & TreeView - 从全局状态读取
    val showSessionSidebar: Boolean
        get() = UIStateManager.isSessionSidebarVisible.value

    val isTreeViewVisible: Boolean
        get() = UIStateManager.isTreeViewVisible.value

    val workspacePath: String
        get() = UIStateManager.workspacePath.value

    // Agent
    var selectedAgent by mutableStateOf("Default")

    var availableAgents by mutableStateOf(listOf("Default"))

    // Mode
    var useAgentMode by mutableStateOf(true)

    // Dialogs
    var showModelConfigDialog by mutableStateOf(false)

    var showToolConfigDialog by mutableStateOf(false)

    var showRemoteConfigDialog by mutableStateOf(false)

    // Actions
    fun updateAgentType(type: AgentType) {
        currentAgentType = type
        // Save to config file for persistence
        scope.launch {
            try {
                val typeString = type.getDisplayName()
                AutoDevConfigWrapper.saveAgentTypePreference(typeString)
            } catch (e: Exception) {
                println("⚠️ Failed to save agent type preference: ${e.message}")
            }
        }
    }

    fun toggleSessionSidebar() {
        UIStateManager.toggleSessionSidebar()
    }

    fun toggleTreeView() {
        UIStateManager.toggleTreeView()
    }

    fun updateWorkspacePath(path: String) {
        UIStateManager.setWorkspacePath(path)
    }

    fun updateSelectedAgent(agent: String) {
        selectedAgent = agent
    }

    fun updateAvailableAgents(agents: List<String>) {
        availableAgents = agents
    }

    fun toggleAgentMode() {
        useAgentMode = !useAgentMode
    }
}

/**
 * Remember DesktopUiState across recompositions
 *
 * @param initialAgentType Initial agent type (default: CODING, or ARTIFACT when opening .unit file)
 */
@Composable
fun rememberDesktopUiState(initialAgentType: AgentType = AgentType.CODING): DesktopUiState {
    return remember { DesktopUiState(initialAgentType) }
}
