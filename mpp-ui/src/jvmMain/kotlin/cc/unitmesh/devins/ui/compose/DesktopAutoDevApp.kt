package cc.unitmesh.devins.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cc.unitmesh.agent.AgentType
import cc.unitmesh.agent.artifact.ArtifactBundle
import cc.unitmesh.viewer.web.KcefManager
import cc.unitmesh.viewer.web.KcefProgressBar
import kotlinx.coroutines.launch

/**
 * Desktop-specific AutoDevApp wrapper that includes KCEF initialization and progress bar
 *
 * This component:
 * - Initializes KCEF in the background on first launch
 * - Shows download progress at the bottom of the window
 * - Does not block user interaction during download
 */
@Composable
fun DesktopAutoDevApp(
    triggerFileChooser: Boolean = false,
    onFileChooserHandled: () -> Unit = {},
    initialMode: String = "auto",
    showTopBarInContent: Boolean = true,
    initialAgentType: AgentType = AgentType.CODING,
    initialTreeViewVisible: Boolean = false,
    onAgentTypeChanged: (AgentType) -> Unit = {},
    onTreeViewVisibilityChanged: (Boolean) -> Unit = {},
    onSidebarVisibilityChanged: (Boolean) -> Unit = {},
    onWorkspacePathChanged: (String) -> Unit = {},
    onHasHistoryChanged: (Boolean) -> Unit = {},
    onNotification: (String, String) -> Unit = { _, _ -> },
    initialBundle: ArtifactBundle? = null // Bundle from file association
) {
    val scope = rememberCoroutineScope()

    // Log bundle reception
    LaunchedEffect(initialBundle) {
        if (initialBundle != null) {
            cc.unitmesh.agent.logging.AutoDevLogger.info("DesktopAutoDevApp") { "📦 Received bundle: ${initialBundle.name} (id: ${initialBundle.id})" }
            cc.unitmesh.agent.logging.AutoDevLogger.info("DesktopAutoDevApp") { "📦 Passing bundle to AutoDevApp" }
        }
    }

    // KCEF initialization state
    val kcefInitState by KcefManager.initState.collectAsState()
    val kcefDownloadProgress by KcefManager.downloadProgress.collectAsState()

    // Initialize KCEF on first launch (background operation)
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val installed = KcefManager.isInstalled()
                if (installed) {
                    KcefManager.initialize(
                        onError = { error ->
                            error.printStackTrace()
                            onNotification("WebView 初始化失败", error.message ?: "未知错误")
                        },
                        onRestartRequired = {
                            onNotification("需要重启", "WebView 组件需要重启应用才能生效")
                        }
                    )
                }
            } catch (e: Exception) {
                println("⚠️ KCEF initialization error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main AutoDevApp content
        AutoDevApp(
            triggerFileChooser = triggerFileChooser,
            onFileChooserHandled = onFileChooserHandled,
            initialMode = initialMode,
            showTopBarInContent = showTopBarInContent,
            initialAgentType = initialAgentType,
            initialTreeViewVisible = initialTreeViewVisible,
            onAgentTypeChanged = onAgentTypeChanged,
            onTreeViewVisibilityChanged = onTreeViewVisibilityChanged,
            onSidebarVisibilityChanged = onSidebarVisibilityChanged,
            onWorkspacePathChanged = onWorkspacePathChanged,
            onHasHistoryChanged = onHasHistoryChanged,
            onNotification = onNotification,
            initialBundle = initialBundle // Pass bundle to AutoDevApp
        )

        // KCEF progress bar at the bottom (overlays the main content)
        KcefProgressBar(
            initState = kcefInitState,
            downloadProgress = kcefDownloadProgress,
            modifier = Modifier.align(Alignment.BottomCenter),
            onDismiss = {
                // User dismissed the notification, but download continues in background
                println("🔕 KCEF progress bar dismissed by user")
            }
        )
    }
}

