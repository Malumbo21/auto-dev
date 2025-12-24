package cc.unitmesh.devins.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import cc.unitmesh.agent.AgentType
import cc.unitmesh.agent.Platform
import cc.unitmesh.agent.logging.AutoDevLogger
import cc.unitmesh.devins.ui.compose.DesktopAutoDevApp
import cc.unitmesh.devins.ui.compose.launch.XiuperLaunchScreen
import cc.unitmesh.devins.ui.compose.state.rememberDesktopUiState
import cc.unitmesh.devins.ui.compose.theme.AutoDevTheme
import cc.unitmesh.devins.ui.compose.theme.ThemeManager
import cc.unitmesh.devins.ui.desktop.AutoDevMenuBar
import cc.unitmesh.devins.ui.desktop.AutoDevTray
import cc.unitmesh.devins.ui.desktop.ComposeSelectionCrashGuard
import cc.unitmesh.devins.ui.desktop.DesktopWindowLayout
import cc.unitmesh.devins.ui.desktop.UnitFileHandler
import cc.unitmesh.agent.artifact.ArtifactBundle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay

fun main(args: Array<String>) {
    AutoDevLogger.initialize()
    ComposeSelectionCrashGuard.install()
    AutoDevLogger.info("AutoDevMain") { "🚀 AutoDev Desktop starting..." }
    AutoDevLogger.info("AutoDevMain") { "📁 Log files location: ${AutoDevLogger.getLogDirectory()}" }
    AutoDevLogger.info("AutoDevMain") { "📋 Command line args: ${args.joinToString(", ")}" }

    val mode = args.find { it.startsWith("--mode=") }?.substringAfter("--mode=") ?: "auto"
    // 检查是否跳过启动动画（通过命令行参数）
    val skipSplash = args.contains("--skip-splash")

    // Check if launched with a .unit file
    val hasUnitFile = UnitFileHandler.hasUnitFile(args)
    AutoDevLogger.info("AutoDevMain") { "🔍 Checking for .unit file: hasUnitFile=$hasUnitFile" }
    if (hasUnitFile) {
        val unitFilePath = UnitFileHandler.getUnitFilePath(args)
        AutoDevLogger.info("AutoDevMain") { "📦 Launched with .unit file: $unitFilePath" }
        runBlocking {
            val success = UnitFileHandler.processArgs(args)
            AutoDevLogger.info("AutoDevMain") { "📦 UnitFileHandler.processArgs result: $success" }
        }
    } else {
        AutoDevLogger.info("AutoDevMain") { "ℹ️ No .unit file detected in command line args" }
    }

    application {
        val trayState = rememberTrayState()
        var isWindowVisible by remember { mutableStateOf(true) }
        var triggerFileChooser by remember { mutableStateOf(false) }
        // 启动动画状态
        var showSplash by remember { mutableStateOf(!skipSplash) }
        // Cache prefersReducedMotion result to avoid repeated system calls
        val reducedMotion = remember { Platform.prefersReducedMotion() }

        // Set initial agent type to ARTIFACT if launched with .unit file
        val initialAgentType = if (hasUnitFile) AgentType.ARTIFACT else AgentType.CODING

        val uiState = rememberDesktopUiState(initialAgentType = initialAgentType)

        // Observe UnitFileHandler's pending bundle (for file association opens)
        val pendingBundle by UnitFileHandler.pendingBundle.collectAsState()

        // Store bundle in local state to prevent it from being cleared before use
        var localBundle by remember { mutableStateOf<ArtifactBundle?>(null) }

        // Log bundle state changes and store it locally
        LaunchedEffect(pendingBundle) {
            if (pendingBundle != null) {
                AutoDevLogger.info("AutoDevMain") { "📦 Pending bundle detected: ${pendingBundle?.name} (id: ${pendingBundle?.id})" }
                AutoDevLogger.info("AutoDevMain") { "📦 Bundle will be passed to DesktopAutoDevApp -> AutoDevApp -> AgentInterfaceRouter -> ArtifactPage" }
                localBundle = pendingBundle
                // Clear the pending bundle after storing it locally (to prevent re-loading on recomposition)
                kotlinx.coroutines.delay(500) // Longer delay to ensure bundle is passed down
                AutoDevLogger.info("AutoDevMain") { "📦 Clearing pending bundle after storing locally" }
                UnitFileHandler.clearPendingBundle()
            } else {
                AutoDevLogger.info("AutoDevMain") { "📦 No pending bundle" }
            }
        }

        val windowState =
            rememberWindowState(
                width = 1200.dp,
                height = 800.dp
            )

        AutoDevTray(
            trayState = trayState,
            isWindowVisible = isWindowVisible,
            onShowWindow = { isWindowVisible = true },
            onExit = ::exitApplication
        )

        if (isWindowVisible) {
            Window(
                onCloseRequest = { isWindowVisible = false },
                title = "AutoDev Desktop",
                state = windowState,
                undecorated = true,
            ) {
                // 显示启动动画或主界面
                if (showSplash) {
                    AutoDevTheme(themeMode = ThemeManager.ThemeMode.DARK) {
                        XiuperLaunchScreen(
                            onFinished = {
                                showSplash = false
                                AutoDevLogger.info("AutoDevMain") { "✨ Launch animation completed" }
                            },
                            reducedMotion = reducedMotion
                        )
                    }
                } else {
                    DesktopWindowLayout(
                        onMinimize = { windowState.isMinimized = true },
                        onMaximize = {
                            windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                                WindowPlacement.Floating
                            } else {
                                WindowPlacement.Maximized
                            }
                        },
                        onClose = { isWindowVisible = false },
                        titleBarContent = {
                            cc.unitmesh.devins.ui.compose.chat.DesktopTitleBarTabs(
                                currentAgentType = uiState.currentAgentType,
                                onAgentTypeChange = { newType ->
                                    uiState.updateAgentType(newType)
                                    AutoDevLogger.info("AutoDevMain") { "🔄 Switch Agent Type: $newType" }
                                },
                                onConfigureRemote = {
                                    uiState.showRemoteConfigDialog = true
                                    AutoDevLogger.info("AutoDevMain") { "☁️ Configure Remote" }
                                },
                                onDoubleClick = {
                                    windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                                        WindowPlacement.Floating
                                    } else {
                                        WindowPlacement.Maximized
                                    }
                                }
                            )
                        }
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            AutoDevMenuBar(
                                onOpenFile = {
                                    triggerFileChooser = true
                                    AutoDevLogger.info("AutoDevMain") { "Open File menu clicked" }
                                },
                                onExit = ::exitApplication
                            )

                            DesktopAutoDevApp(
                                triggerFileChooser = triggerFileChooser,
                                onFileChooserHandled = { triggerFileChooser = false },
                                initialMode = mode,
                                showTopBarInContent = false,
                                initialAgentType = uiState.currentAgentType,
                                initialTreeViewVisible = uiState.isTreeViewVisible,
                                onAgentTypeChanged = { type ->
                                    uiState.updateAgentType(type)
                                },
                                onTreeViewVisibilityChanged = { visible ->
                                    // 已由全局状态管理，无需额外操作
                                },
                                onSidebarVisibilityChanged = { visible ->
                                    // 已由全局状态管理，无需额外操作
                                },
                                onWorkspacePathChanged = { path ->
                                    uiState.updateWorkspacePath(path)
                                },
                                onNotification = { title, message ->
                                    trayState.sendNotification(androidx.compose.ui.window.Notification(title, message))
                                },
                                initialBundle = (localBundle ?: pendingBundle).also {
                                    if (it != null) {
                                        AutoDevLogger.info("AutoDevMain") { "📦 Passing bundle to DesktopAutoDevApp: ${it.name} (from ${if (localBundle != null) "local" else "pending"})" }
                                    }
                                } // Pass bundle from UnitFileHandler (use localBundle first, fallback to pendingBundle)
                            )
                        }
                    }
                }
            }
        }
    }
}
