package cc.unitmesh.devins.ui.webedit

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devins.ui.compose.agent.webedit.ElementTagCollection
import cc.unitmesh.devins.ui.compose.agent.webedit.WebEditPage
import cc.unitmesh.devins.ui.compose.agent.webedit.buildWebEditLLMPrompt
import cc.unitmesh.llm.KoogLLMService
import cc.unitmesh.viewer.web.webedit.*
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * WebEdit Preview with Debug Logging
 *
 * 用于调试 WebEdit 功能，带有详细的 println 日志
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "WebEdit Debug Preview",
        state = rememberWindowState(width = 1400.dp, height = 900.dp)
    ) {
        var restartRequired by remember { mutableStateOf(false) }
        var downloading by remember { mutableStateOf(0F) }
        var initialized by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        println("═══════════════════════════════════════════════════════════")
        println("[WebEditPreview] Starting WebEdit Debug Preview")
        println("═══════════════════════════════════════════════════════════")

        // Initialize KCEF
        LaunchedEffect(Unit) {
            println("[WebEditPreview] 🚀 Initializing KCEF...")
            withContext(Dispatchers.IO) {
                val installDir = File(ConfigManager.getKcefInstallDir())
                println("[WebEditPreview] 📁 KCEF Install Dir: ${installDir.absolutePath}")

                KCEF.init(builder = {
                    installDir(installDir)

                    progress {
                        onDownloading {
                            downloading = max(it, 0F)
                            println("[WebEditPreview] 📥 KCEF Downloading: ${(downloading * 100).toInt()}%")
                        }
                        onInitialized {
                            initialized = true
                            println("[WebEditPreview] ✅ KCEF Initialized successfully")
                        }
                    }
                    settings {
                        cachePath = File("kcef-cache").absolutePath
                    }
                }, onError = {
                    error = it?.localizedMessage
                    println("[WebEditPreview] ❌ KCEF Error: $error")
                }, onRestartRequired = {
                    restartRequired = true
                    println("[WebEditPreview] 🔄 KCEF Restart Required")
                })
            }
        }

        MaterialTheme(
            colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                when {
                    restartRequired -> {
                        RestartRequiredView()
                    }

                    !initialized -> {
                        LoadingView(downloading)
                    }

                    error != null -> {
                        ErrorView(error!!)
                    }

                    else -> {
                        println("[WebEditPreview] 🎨 Rendering WebEditPage...")
                        WebEditDebugContainer()
                    }
                }
            }
        }
    }
}

@Composable
fun WebEditDebugContainer() {
    val scope = rememberCoroutineScope()

    // Create a debug bridge with logging
    val bridge = remember {
        println("[WebEditDebugContainer] 🌉 Creating WebEditBridge...")
        val b = createWebEditBridge()

        // Add debug logging for bridge
        if (b is JvmWebEditBridge) {
            println("[WebEditDebugContainer] ✅ JvmWebEditBridge created")
        }
        b
    }

    // Monitor bridge state
    val currentUrl by bridge.currentUrl.collectAsState()
    val pageTitle by bridge.pageTitle.collectAsState()
    val isLoading by bridge.isLoading.collectAsState()
    val isSelectionMode by bridge.isSelectionMode.collectAsState()
    val selectedElement by bridge.selectedElement.collectAsState()
    val domTree by bridge.domTree.collectAsState()
    val actionableElements by bridge.actionableElements.collectAsState()
    val errorMessage by bridge.errorMessage.collectAsState()
    val isReady by bridge.isReady.collectAsState()

    var llmService by remember { mutableStateOf<KoogLLMService?>(null) }
    var llmInitError by remember { mutableStateOf<String?>(null) }
    var hasRunAutoTest by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            if (!ConfigManager.exists()) {
                llmInitError = "Config file not found: ${ConfigManager.getConfigPath()}"
                println("[WebEditDebugContainer] ⚠️ $llmInitError")
                return@LaunchedEffect
            }

            val wrapper = ConfigManager.load()
            val activeConfig = wrapper.getActiveModelConfig()
            if (activeConfig == null || !activeConfig.isValid()) {
                llmInitError = "No valid LLM config found in ${ConfigManager.getConfigPath()}"
                println("[WebEditDebugContainer] ⚠️ $llmInitError")
                return@LaunchedEffect
            }

            llmService = KoogLLMService.create(activeConfig)
            println("[WebEditDebugContainer] ✅ LLM initialized: ${activeConfig.provider.displayName} / ${activeConfig.modelName}")
        } catch (e: Exception) {
            llmInitError = e.message ?: "Failed to init LLM"
            println("[WebEditDebugContainer] ❌ LLM init failed: $llmInitError")
            e.printStackTrace()
        }
    }

    // Log state changes
    LaunchedEffect(currentUrl) {
        println("[WebEditDebugContainer] 🌐 URL changed: '$currentUrl'")
    }

    LaunchedEffect(pageTitle) {
        println("[WebEditDebugContainer] 📄 Title changed: '$pageTitle'")
    }

    LaunchedEffect(isLoading) {
        println("[WebEditDebugContainer] ⏳ Loading: $isLoading")
    }

    LaunchedEffect(isSelectionMode) {
        println("[WebEditDebugContainer] 🎯 Selection Mode: $isSelectionMode")
    }

    LaunchedEffect(selectedElement) {
        if (selectedElement != null) {
            println("[WebEditDebugContainer] ✨ Element Selected:")
            println("  - Tag: ${selectedElement?.tagName}")
            println("  - Selector: ${selectedElement?.selector}")
            println("  - Text: ${selectedElement?.textContent?.take(50)}")
        } else {
            println("[WebEditDebugContainer] 🔲 No element selected")
        }
    }

    LaunchedEffect(domTree) {
        if (domTree != null) {
            println("[WebEditDebugContainer] 🌳 DOM Tree Updated:")
            println("  - Root: ${domTree?.tagName}")
            println("  - Children: ${domTree?.children?.size ?: 0}")
            println("  - Selector: ${domTree?.selector}")

            // Print first level children
            domTree?.children?.take(5)?.forEach { child ->
                println("    └─ ${child.tagName} (${child.children.size} children)")
            }
            if ((domTree?.children?.size ?: 0) > 5) {
                println("    └─ ... and ${(domTree?.children?.size ?: 0) - 5} more")
            }
        } else {
            println("[WebEditDebugContainer] 🌳 DOM Tree: null")
        }
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            println("[WebEditDebugContainer] ❌ Error: $errorMessage")
        }
    }

    LaunchedEffect(isReady) {
        println("[WebEditDebugContainer] 🚦 Bridge Ready: $isReady")
    }

    // Debug panel overlay
    Column(modifier = Modifier.fillMaxSize()) {
        // Status bar
        Surface(
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "🌉 Bridge: ${if (isReady) "✅ Ready" else "⏳ Not Ready"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "⏳ Loading: ${if (isLoading) "Yes" else "No"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "🌳 DOM: ${if (domTree != null) "✅ Loaded (${domTree?.children?.size ?: 0} children)" else "❌ Empty"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "🎯 Selection: ${if (isSelectionMode) "ON" else "OFF"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "🤖 LLM: ${if (llmService != null) "✅ Ready" else "❌ Not Ready"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // Main WebEdit Page
        WebEditPage(
            llmService = llmService,
            bridge = bridge,
            modifier = Modifier.weight(1f),
            onBack = {
                println("[WebEditDebugContainer] 🔙 Back button clicked")
            },
            onNotification = { title, message ->
                println("[WebEditDebugContainer] 🔔 Notification: $title - $message")
            }
        )
    }

    // Auto-test: open Google + GitHub and call LLM with WebEditPage-style prompt.
    LaunchedEffect(llmService, isReady, currentUrl, pageTitle, actionableElements) {
        val service = llmService ?: return@LaunchedEffect
        if (hasRunAutoTest) return@LaunchedEffect
        if (llmInitError != null) return@LaunchedEffect

        // Start once the first page is ready (WebEditPage will auto-load an initial URL).
        if (!isReady || currentUrl.isBlank() || currentUrl == "about:blank") return@LaunchedEffect

        hasRunAutoTest = true
        scope.launch {
            val testUrls = listOf(
                "https://www.google.com",
                "https://github.com"
            )

            for (url in testUrls) {
                println("═══════════════════════════════════════════════════════════")
                println("[WebEditDebugContainer] 🧪 LLM test starting for: $url")
                println("═══════════════════════════════════════════════════════════")

                bridge.navigateTo(url)

                // Wait for page to fully load (isLoading -> false and isReady -> true)
                var waitCount = 0
                while ((bridge.isLoading.value || !bridge.isReady.value) && waitCount < 60) {
                    delay(500)
                    waitCount++
                    if (waitCount % 4 == 0) {
                        println("[WebEditDebugContainer] ⏳ Waiting for page to load... (${waitCount / 2}s, loading=${bridge.isLoading.value}, ready=${bridge.isReady.value})")
                    }
                }

                if (waitCount >= 60) {
                    println("[WebEditDebugContainer] ⚠️ Page load timeout for $url, skipping LLM test")
                    continue
                }

                println("[WebEditDebugContainer] ✅ Page loaded successfully: ${bridge.pageTitle.value}")
                delay(1000) // Extra time for actionable elements to stabilize

                // Ask WebView to refresh actionable elements again for this page.
                bridge.refreshActionableElements()
                delay(800)

                val prompt = buildWebEditLLMPrompt(
                    message = "请用中文总结当前页面，并从 Actionable elements 里挑选 5 个你认为最重要的交互点（带 selector）。",
                    currentUrl = bridge.currentUrl.value,
                    pageTitle = bridge.pageTitle.value,
                    selectedElement = bridge.selectedElement.value,
                    elementTags = ElementTagCollection(),
                    actionableElements = bridge.actionableElements.value
                )

                println("[WebEditDebugContainer] 📤 Sending prompt to LLM (${prompt.length} chars)...")
                println("[WebEditDebugContainer] 📊 Context: ${bridge.actionableElements.value.size} actionable elements")

                try {
                    val response = service.sendPrompt(prompt)
                    println("[WebEditDebugContainer] ✅ LLM response for $url:")
                    println("─".repeat(60))
                    println(response)
                    println("─".repeat(60))
                } catch (e: Exception) {
                    println("[WebEditDebugContainer] ❌ LLM call failed: ${e.message}")
                    e.printStackTrace()
                }

                println() // blank line for readability
            }

            println("═══════════════════════════════════════════════════════════")
            println("[WebEditDebugContainer] ✅ All LLM tests completed")
            println("═══════════════════════════════════════════════════════════")
        }
    }
}

@Composable
fun RestartRequiredView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(
                "KCEF Restart Required",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Please restart the application",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun LoadingView(progress: Float) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()

            if (progress > 0) {
                Text(
                    "Downloading KCEF: ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyLarge
                )
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.width(300.dp)
                )
            } else {
                Text(
                    "Initializing WebEdit...",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun ErrorView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "❌ Error",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
