package cc.unitmesh.devins.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cc.unitmesh.agent.AgentType
import cc.unitmesh.devins.ui.kcef.KcefManager
import cc.unitmesh.devins.ui.kcef.KcefProgressBar
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
    onNotification: (String, String) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()
    
    // KCEF initialization state
    val kcefInitState by KcefManager.initState.collectAsState()
    val kcefDownloadProgress by KcefManager.downloadProgress.collectAsState()
    
    // Initialize KCEF on first launch (background operation)
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                println("🔍 Checking KCEF installation status...")
                val installed = KcefManager.isInstalled()
                println("📊 KCEF installed: $installed")
                
                if (!installed) {
                    println("📦 KCEF not installed, starting download and initialization...")
                    println("⏰ This may take a few minutes on first run (80-150MB download)")
                    KcefManager.initialize(
                        onError = { error ->
                            println("❌ KCEF initialization failed: ${error.message}")
                            error.printStackTrace()
                            onNotification("WebView 初始化失败", error.message ?: "未知错误")
                        },
                        onRestartRequired = {
                            println("🔄 KCEF requires restart")
                            onNotification("需要重启", "WebView 组件需要重启应用才能生效")
                        }
                    )
                    println("✅ KCEF initialization request completed")
                } else {
                    println("✅ KCEF already installed, skipping download")
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
            onNotification = onNotification
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

