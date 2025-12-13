package cc.unitmesh.devins.ui.webedit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.viewer.web.webedit.*
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.max

/**
 * WebEdit Automation Test Application
 *
 * 自动化测试 WebEdit 功能：
 * 1. 自动加载真实网页
 * 2. 测试 DOM 树提取
 * 3. 测试元素选择
 * 4. 测试 Shadow DOM 支持
 * 5. 显示详细测试结果
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "WebEdit Automation Test",
        state = rememberWindowState(width = 1600.dp, height = 1000.dp)
    ) {
        var restartRequired by remember { mutableStateOf(false) }
        var downloading by remember { mutableStateOf(0F) }
        var initialized by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        println("═══════════════════════════════════════════════════════════")
        println("[WebEditAutomationTest] Starting Automation Test Suite")
        println("═══════════════════════════════════════════════════════════")

        // Initialize KCEF
        LaunchedEffect(Unit) {
            println("[AutoTest] 🚀 Initializing KCEF...")
            withContext(Dispatchers.IO) {
                val installDir = File(ConfigManager.getKcefInstallDir())
                println("[AutoTest] 📁 KCEF Install Dir: ${installDir.absolutePath}")

                KCEF.init(builder = {
                    installDir(installDir)

                    progress {
                        onDownloading {
                            downloading = max(it, 0F)
                            println("[AutoTest] 📥 Downloading: ${(downloading * 100).toInt()}%")
                        }
                        onInitialized {
                            initialized = true
                            println("[AutoTest] ✅ KCEF Initialized")
                        }
                    }
                    settings {
                        cachePath = File(installDir, "cache").absolutePath
                    }
                }, onError = {
                    error = it?.localizedMessage
                    println("[AutoTest] ❌ KCEF Error: $error")
                }, onRestartRequired = {
                    restartRequired = true
                    println("[AutoTest] 🔄 KCEF Restart Required")
                })
            }
        }

        MaterialTheme(
            colorScheme = darkColorScheme()
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                when {
                    restartRequired -> AutoTestRestartView()
                    !initialized -> AutoTestLoadingView(downloading)
                    error != null -> AutoTestErrorView(error!!)
                    else -> AutomationTestApp()
                }
            }
        }
    }
}

@Composable
private fun AutomationTestApp() {
    val bridge = remember { createWebEditBridge() }
    val scope = rememberCoroutineScope()

    // Bridge state
    val currentUrl by bridge.currentUrl.collectAsState()
    val pageTitle by bridge.pageTitle.collectAsState()
    val isLoading by bridge.isLoading.collectAsState()
    val isReady by bridge.isReady.collectAsState()
    val domTree by bridge.domTree.collectAsState()
    val selectedElement by bridge.selectedElement.collectAsState()
    val errorMessage by bridge.errorMessage.collectAsState()

    // Test state
    var testPhase by remember { mutableStateOf("INITIALIZING") }
    var testResults by remember { mutableStateOf<List<TestResult>>(emptyList()) }
    var testLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var isRunningTests by remember { mutableStateOf(false) }

    // Helper function to add log
    fun addLog(message: String) {
        testLogs = testLogs + message
        println("[AutoTest] $message")
    }

    // Phase 1: Auto-load test page
    LaunchedEffect(Unit) {
        addLog("═══════════════════════════════════════════════════════")
        addLog("Phase 1: Loading test page...")
        addLog("═══════════════════════════════════════════════════════")

        testPhase = "LOADING_PAGE"
        delay(1000)

        val testUrl = "https://ide.unitmesh.cc"
        addLog("📍 Navigating to: $testUrl")

        if (bridge is JvmWebEditBridge) {
            bridge.navigateTo(testUrl)
        } else {
            addLog("❌ Bridge is not JvmWebEditBridge!")
        }
    }

    // Phase 2: Wait for bridge ready and run tests
    LaunchedEffect(isReady) {
        if (isReady && !isRunningTests && testResults.isEmpty() && testPhase != "FAILED") {
            addLog("")
            addLog("═══════════════════════════════════════════════════════")
            addLog("Phase 2: Bridge ready, starting tests in 3 seconds...")
            addLog("═══════════════════════════════════════════════════════")

            delay(3000) // Wait for page to fully load

            testPhase = "RUNNING_TESTS"
            isRunningTests = true

            try {
                val results = mutableListOf<TestResult>()

                // Test 1: Check if page loaded
                addLog("")
                addLog("🧪 Test 1: Page Load")
                if (currentUrl.contains("unitmesh.cc")) {
                    results.add(TestResult("Page Load", true, "✓ URL: $currentUrl"))
                    addLog("  ✅ PASSED - Page loaded: $currentUrl")
                } else {
                    results.add(TestResult("Page Load", false, "URL: $currentUrl"))
                    addLog("  ❌ FAILED - Wrong URL: $currentUrl")
                }

                delay(500)

                // Test 2: Check DOM tree
                addLog("")
                addLog("🧪 Test 2: DOM Tree Extraction")
                try {
                    bridge.refreshDOMTree()

                    val timeoutMs = 3000L
                    val startedAt = System.currentTimeMillis()
                    while ((domTree == null || domTree?.children?.isEmpty() == true) &&
                        (System.currentTimeMillis() - startedAt) < timeoutMs
                    ) {
                        delay(150)
                    }

                    if (domTree != null && domTree!!.children.isNotEmpty()) {
                        val childCount = domTree!!.children.size
                        results.add(TestResult("DOM Tree", true, "✓ $childCount children"))
                        addLog("  ✅ PASSED - DOM tree has $childCount top-level children")
                        addLog("  📊 Root element: ${domTree!!.tagName}")
                    } else {
                        results.add(TestResult("DOM Tree", false, "DOM tree is null/empty after refresh"))
                        addLog("  ❌ FAILED - DOM tree is null or empty")
                    }
                } catch (e: Exception) {
                    results.add(TestResult("DOM Tree", false, e.message ?: "Error"))
                    addLog("  ❌ FAILED - ${e.message}")
                }

                delay(500)

                // Test 3: Enable selection mode
                addLog("")
                addLog("🧪 Test 3: Selection Mode Toggle")
                try {
                    bridge.setSelectionMode(true)
                    delay(200)
                    bridge.setSelectionMode(false)
                    results.add(TestResult("Selection Mode", true, "✓ Toggle successful"))
                    addLog("  ✅ PASSED - Selection mode toggled")
                } catch (e: Exception) {
                    results.add(TestResult("Selection Mode", false, e.message ?: "Error"))
                    addLog("  ❌ FAILED - ${e.message}")
                }

                delay(500)

                // Test 4: Highlight element
                addLog("")
                addLog("🧪 Test 4: Element Highlighting")
                if (domTree != null && domTree!!.children.isNotEmpty()) {
                    try {
                        val firstChild = domTree!!.children.first()
                        bridge.highlightElement(firstChild.selector)
                        delay(200)
                        bridge.clearHighlights()
                        results.add(TestResult("Element Highlight", true, "✓ Highlight: ${firstChild.selector}"))
                        addLog("  ✅ PASSED - Highlighted ${firstChild.selector}")
                    } catch (e: Exception) {
                        results.add(TestResult("Element Highlight", false, e.message ?: "Error"))
                        addLog("  ❌ FAILED - ${e.message}")
                    }
                } else {
                    results.add(TestResult("Element Highlight", false, "No DOM tree"))
                    addLog("  ⚠️ SKIPPED - No DOM tree available")
                }

                delay(500)

                // Test 5: Navigation controls
                addLog("")
                addLog("🧪 Test 5: Navigation Controls")
                try {
                    bridge.reload()
                    delay(1000)
                    results.add(TestResult("Reload", true, "✓ Page reloaded"))
                    addLog("  ✅ PASSED - Page reloaded")
                } catch (e: Exception) {
                    results.add(TestResult("Reload", false, e.message ?: "Error"))
                    addLog("  ❌ FAILED - ${e.message}")
                }

                delay(500)

                // Test 6: Error handling
                addLog("")
                addLog("🧪 Test 6: Error State")
                if (errorMessage == null) {
                    results.add(TestResult("Error Handling", true, "✓ No errors"))
                    addLog("  ✅ PASSED - No errors reported")
                } else {
                    results.add(TestResult("Error Handling", false, "Error: $errorMessage"))
                    addLog("  ❌ FAILED - Error: $errorMessage")
                }

                testResults = results
                testPhase = "COMPLETED"

                val passed = results.count { it.passed }
                val total = results.size

                addLog("")
                addLog("═══════════════════════════════════════════════════════")
                addLog("🎯 TEST RESULTS: $passed/$total PASSED")
                addLog("═══════════════════════════════════════════════════════")

                if (passed == total) {
                    addLog("✅ ALL TESTS PASSED!")
                } else {
                    addLog("❌ SOME TESTS FAILED")
                }

            } catch (e: Exception) {
                addLog("❌ TEST SUITE FAILED: ${e.message}")
                e.printStackTrace()
                testPhase = "FAILED"
            } finally {
                isRunningTests = false
            }
        }
    }

    // Monitor state changes
    LaunchedEffect(isLoading) {
        addLog("⏳ Loading: $isLoading")
    }

    LaunchedEffect(currentUrl) {
        if (currentUrl.isNotEmpty()) {
            addLog("🌐 URL: $currentUrl")
        }
    }

    LaunchedEffect(pageTitle) {
        if (pageTitle.isNotEmpty()) {
            addLog("📄 Title: $pageTitle")
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left: WebView
        Column(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Status bar
            Surface(
                tonalElevation = 4.dp,
                color = when (testPhase) {
                    "COMPLETED" -> Color(0xFF4CAF50)
                    "FAILED" -> MaterialTheme.colorScheme.error
                    "RUNNING_TESTS" -> Color(0xFF2196F3)
                    else -> MaterialTheme.colorScheme.primary
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Phase: $testPhase",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = "URL: $currentUrl",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Text(
                        text = "Bridge: ${if (isReady) "✅ Ready" else "⏳ Not Ready"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            // WebView
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                WebEditView(
                    bridge = bridge,
                    modifier = Modifier.fillMaxSize(),
                    onPageLoaded = { url, title ->
                        addLog("✓ Page loaded: $title")
                    },
                    onElementSelected = { element ->
                        addLog("✓ Element selected: ${element.tagName}")
                    },
                    onDOMTreeUpdated = { root ->
                        addLog("✓ DOM tree updated: ${root.children.size} children")
                    }
                )

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    )
                }
            }
        }

        // Right: Test Results & Logs
        Column(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Test Results
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "📊 Test Results (${testResults.count { it.passed }}/${testResults.size} passed)",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        testResults.forEach { result ->
                            TestResultCard(result)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            HorizontalDivider()

            // Logs
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "📝 Test Logs",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    val scrollState = rememberScrollState()
                    LaunchedEffect(testLogs.size) {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        testLogs.forEach { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TestResultCard(result: TestResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.passed)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (result.passed) "✅" else "❌",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (result.passed)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )
                if (result.message.isNotEmpty()) {
                    Text(
                        text = result.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.passed)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else
                            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoTestRestartView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
private fun AutoTestLoadingView(progress: Float) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()

            if (progress > 0) {
                Text(
                    "Downloading KCEF: ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyLarge
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.width(300.dp)
                )
            } else {
                Text(
                    "Initializing...",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun AutoTestErrorView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
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

data class TestResult(
    val name: String,
    val passed: Boolean,
    val message: String = ""
)
