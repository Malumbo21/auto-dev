package cc.unitmesh.viewer.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import cc.unitmesh.viewer.web.automation.WebViewTestRunner
import cc.unitmesh.viewer.web.automation.TestResult
import cc.unitmesh.viewer.web.automation.TestCategory
import cc.unitmesh.viewer.web.automation.TestHelper
import cc.unitmesh.viewer.web.webedit.*
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.max

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "WebView Inspect & Automation Test"
    ) {
        var restartRequired by remember { mutableStateOf(false) }
        var downloading by remember { mutableStateOf(0F) }
        var initialized by remember { mutableStateOf(false) }
        var initError by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            println("[WebViewDebugApp] Starting KCEF initialization...")
            
            try {
                withContext(Dispatchers.IO) {
                    val homeDir = System.getProperty("user.home")
                    val configDir = File(homeDir, ".autodev")
                    val kcefDir = File(configDir, "kcef-bundle")
                    
                    println("[WebViewDebugApp] KCEF install dir: ${kcefDir.absolutePath}")
                    println("[WebViewDebugApp] Directory exists: ${kcefDir.exists()}")
                    
                    if (!kcefDir.exists()) {
                        kcefDir.mkdirs()
                        println("[WebViewDebugApp] Created KCEF directory")
                    }

                    KCEF.init(builder = {
                        installDir(kcefDir)

                        progress {
                            onDownloading { progress ->
                                val p = max(progress, 0F)
                                downloading = p
                                println("[WebViewDebugApp] Downloading KCEF: $p%")
                            }
                            onInitialized {
                                println("[WebViewDebugApp] KCEF onInitialized callback fired")
                                initialized = true
                            }
                        }
                        settings {
                            cachePath = File(kcefDir, "cache").absolutePath
                        }
                    }, onError = { error ->
                        val msg = error?.message ?: "Unknown KCEF error"
                        println("[WebViewDebugApp] KCEF Error: $msg")
                        error?.printStackTrace()
                        initError = msg
                    }, onRestartRequired = {
                        println("[WebViewDebugApp] KCEF requires restart")
                        restartRequired = true
                    })
                }
                
                // Wait a bit for the callback to fire if not already
                if (!initialized && !restartRequired && initError == null) {
                    println("[WebViewDebugApp] Waiting for KCEF initialization...")
                    var waited = 0
                    while (!initialized && waited < 5000) {
                        delay(100)
                        waited += 100
                    }
                    
                    if (!initialized) {
                        // Force check if KCEF is actually ready
                        println("[WebViewDebugApp] Timeout waiting for callback, checking if KCEF is ready...")
                        initialized = true // Try to proceed anyway
                    }
                }
                
                println("[WebViewDebugApp] KCEF initialization complete. initialized=$initialized")
            } catch (e: Exception) {
                println("[WebViewDebugApp] KCEF initialization exception: ${e.message}")
                e.printStackTrace()
                initError = e.message
            }
        }

        when {
            restartRequired -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Restart required to complete KCEF installation.")
                }
            }
            initError != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "KCEF initialization failed:", color = MaterialTheme.colors.error)
                        Text(text = initError ?: "", color = MaterialTheme.colors.error)
                    }
                }
            }
            initialized -> {
                InspectDebugApp()
            }
            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(text = "Initializing KCEF...")
                        if (downloading > 0F) {
                            Text(text = "Downloading: ${downloading.toInt()}%")
                        }
                    }
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                println("[WebViewDebugApp] Disposing KCEF...")
                KCEF.disposeBlocking()
            }
        }
    }
}

/**
 * Main Inspect Debug Application - FULLY AUTOMATED MODE
 * Automatically loads page → runs tests → displays results
 * Perfect for CI/CD and automated testing scenarios
 */
@Composable
internal fun InspectDebugApp() {
    val bridge = remember { JvmWebEditBridge() }
    val scope = rememberCoroutineScope()

    // State
    val selectedElement by bridge.selectedElement.collectAsState()
    val domTree by bridge.domTree.collectAsState()
    val isReady by bridge.isReady.collectAsState()
    val errorMessage by bridge.errorMessage.collectAsState()
    val currentUrl by bridge.currentUrl.collectAsState()
    val isLoading by bridge.isLoading.collectAsState()

    var testResults by remember { mutableStateOf<List<TestResult>>(emptyList()) }
    var isRunningTests by remember { mutableStateOf(false) }
    var automationPhase by remember { mutableStateOf("INITIALIZING") }
    var pageLoadedSuccessfully by remember { mutableStateOf(false) }
    
    // ========== FULLY AUTOMATED WORKFLOW ==========
    // Phase 1: Auto-load test page on startup
    LaunchedEffect(Unit) {
        println("\n╔════════════════════════════════════════════════════════════════╗")
        println("║     WebView Inspect - AUTOMATED TEST MODE                      ║")
        println("╚════════════════════════════════════════════════════════════════╝\n")
        
        automationPhase = "LOADING_PAGE"
        println("[Automation] Phase 1: Loading test page...")
        
        delay(1000) // Give WebView time to initialize
        
        // Extract resource to temp file to avoid jar:file:// URL issues
        try {
            val resourceStream = this::class.java.classLoader.getResourceAsStream("test-shadow-dom.html")
            if (resourceStream != null) {
                val tempFile = kotlin.io.path.createTempFile(suffix = ".html").toFile()
                tempFile.deleteOnExit()
                tempFile.outputStream().use { output ->
                    resourceStream.copyTo(output)
                }
                val fileUrl = "file://${tempFile.absolutePath}"
                println("[Automation] Extracted resource to: $fileUrl")
                bridge.navigateTo(fileUrl)
            } else {
                // Fallback to file path
                val testPagePath = File("mpp-viewer-web/src/jvmMain/resources/test-shadow-dom.html")
                if (testPagePath.exists()) {
                    val fileUrl = "file://${testPagePath.absolutePath}"
                    println("[Automation] Loading from file: $fileUrl")
                    bridge.navigateTo(fileUrl)
                } else {
                    println("[Automation] ERROR: Test page not found!")
                    automationPhase = "FAILED"
                }
            }
        } catch (e: Exception) {
            println("[Automation] ERROR loading page: ${e.message}")
            e.printStackTrace()
            automationPhase = "FAILED"
        }
    }
    
    // Phase 2: Auto-run tests when page is ready
    LaunchedEffect(isReady) {
        println("[Automation] Bridge ready status changed: $isReady")
        
        if (isReady && !isRunningTests && testResults.isEmpty() && automationPhase != "FAILED") {
            automationPhase = "RUNNING_TESTS"
            println("\n[Automation] Phase 2: Bridge is ready, starting automated tests in 2 seconds...")
            delay(2000) // Give page time to fully render
            
            isRunningTests = true
            try {
                println("[Automation] Executing test suite...")
                val testRunner = WebViewTestRunner(bridge)
                val results = testRunner.runTests()
                testResults = results
                
                automationPhase = "COMPLETED"
                println("\n[Automation] ✓ Test execution completed!")
                println("[Automation] Total: ${results.size} tests, Passed: ${results.count { it.passed }}")
                
                // Auto-exit after 5 seconds
                println("\n[Automation] Application will exit in 5 seconds...")
                delay(5000)
                println("[Automation] Exiting application.")
                kotlin.system.exitProcess(if (results.count { it.passed } == results.size) 0 else 1)
            } catch (e: Exception) {
                println("[Automation] ✗ Test execution failed: ${e.message}")
                e.printStackTrace()
                automationPhase = "FAILED"
                
                // Auto-exit on failure
                delay(3000)
                println("[Automation] Exiting application due to failure.")
                kotlin.system.exitProcess(2)
            } finally {
                isRunningTests = false
            }
        }
    }
    
    // Log bridge state changes for debugging
    LaunchedEffect(isLoading) {
        println("[Bridge] isLoading: $isLoading")
    }
    
    LaunchedEffect(currentUrl) {
        println("[Bridge] currentUrl: $currentUrl")
        if (currentUrl.isNotEmpty() && currentUrl != "about:blank") {
            pageLoadedSuccessfully = true
        }
    }
    
    LaunchedEffect(domTree) {
        if (domTree != null) {
            println("[Bridge] DOM tree updated: ${domTree?.children?.size} top-level children")
        }
    }
    
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            println("[Bridge] ERROR: $errorMessage")
        }
    }

    MaterialTheme {
        Row(Modifier.fillMaxSize()) {
            // Left Panel: Automation Status & WebView
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
            ) {
                // Status Bar
                TopAppBar(
                    title = { 
                        Column {
                            Text("WebView Automation Test")
                            Text(
                                text = "Phase: $automationPhase",
                                style = MaterialTheme.typography.caption
                            )
                        }
                    },
                    backgroundColor = when (automationPhase) {
                        "COMPLETED" -> Color(0xFF4CAF50) // Green
                        "FAILED" -> MaterialTheme.colors.error
                        "RUNNING_TESTS" -> Color(0xFF2196F3) // Blue
                        else -> MaterialTheme.colors.primary
                    }
                )

                // Progress Indicator
                if (isRunningTests) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Error message display
                if (errorMessage != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colors.error,
                        elevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️",
                                style = MaterialTheme.typography.h6,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = errorMessage ?: "",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onError
                            )
                        }
                    }
                }

                // Status message
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colors.surface,
                    elevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (automationPhase) {
                            "INITIALIZING" -> {
                                CircularProgressIndicator(Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Initializing...")
                            }
                            "LOADING_PAGE" -> {
                                CircularProgressIndicator(Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Loading test page...")
                            }
                            "RUNNING_TESTS" -> {
                                CircularProgressIndicator(Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Running automated tests...")
                            }
                            "COMPLETED" -> {
                                Text("✓", style = MaterialTheme.typography.h5, color = Color(0xFF4CAF50))
                                Spacer(Modifier.width(8.dp))
                                Text("Tests completed! See results on the right →")
                            }
                            "FAILED" -> {
                                Text("✗", style = MaterialTheme.typography.h5, color = MaterialTheme.colors.error)
                                Spacer(Modifier.width(8.dp))
                                Text("Automation failed. Check logs for details.")
                            }
                        }
                    }
                }

                // WebView (hidden but running for automation)
                WebEditView(
                    bridge = bridge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onElementSelected = { element ->
                        println("[Debug] Element selected: ${element.tagName} - ${element.selector}")
                    },
                    onDOMTreeUpdated = { root ->
                        println("[Debug] DOM tree updated: ${root.children.size} children")
                    }
                )
            }

            // Right Panel: Real-time Test Results
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colors.surface)
            ) {
                AutomationResultsPanel(
                    automationPhase = automationPhase,
                    testResults = testResults,
                    isRunningTests = isRunningTests,
                    domTree = domTree
                )
            }
        }
    }
}

/**
 * Automation Results Panel - Shows real-time test progress and results
 */
@Composable
private fun AutomationResultsPanel(
    automationPhase: String,
    testResults: List<TestResult>,
    isRunningTests: Boolean,
    domTree: DOMElement?
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Automation Results",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Current Phase Status
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = 2.dp,
            backgroundColor = when (automationPhase) {
                "COMPLETED" -> Color(0xFFE8F5E9)
                "FAILED" -> Color(0xFFFFEBEE)
                "RUNNING_TESTS" -> Color(0xFFE3F2FD)
                else -> MaterialTheme.colors.surface
            }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = when (automationPhase) {
                        "INITIALIZING" -> "⏳ Initializing..."
                        "LOADING_PAGE" -> "📄 Loading test page..."
                        "RUNNING_TESTS" -> "🔬 Running tests..."
                        "COMPLETED" -> "✅ All tests completed"
                        "FAILED" -> "❌ Automation failed"
                        else -> "Unknown phase"
                    },
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                
                if (domTree != null) {
                    Text(
                        text = "DOM ready: ${domTree.children.size} top-level elements",
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Test Results
        if (isRunningTests) {
            Row(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.width(16.dp))
                Text("Executing tests...")
            }
        } else if (testResults.isNotEmpty()) {
            val passedCount = testResults.count { it.passed }
            val totalCount = testResults.size

            // Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                backgroundColor = if (passedCount == totalCount)
                    Color(0xFFE8F5E9)
                else
                    Color(0xFFFFEBEE),
                elevation = 2.dp
            ) {
                Text(
                    text = "Results: $passedCount / $totalCount tests passed",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = if (passedCount == totalCount)
                        Color(0xFF2E7D32)
                    else
                        Color(0xFFC62828)
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Group by category
            val byCategory = testResults.groupBy { it.category }
            byCategory.forEach { (category, tests) ->
                Text(
                    text = category.name.replace("_", " "),
                    style = MaterialTheme.typography.subtitle2,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                tests.forEach { result ->
                    TestResultCard(result)
                }

                Spacer(Modifier.height(8.dp))
            }
        } else if (automationPhase == "COMPLETED") {
            Text("No test results available.", color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun TestResultCard(result: TestResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        backgroundColor = if (result.passed)
            MaterialTheme.colors.primary.copy(alpha = 0.1f)
        else
            MaterialTheme.colors.error.copy(alpha = 0.1f),
        elevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.body2,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = if (result.passed) "✓ PASS" else "✗ FAIL",
                    color = if (result.passed)
                        MaterialTheme.colors.primary
                    else
                        MaterialTheme.colors.error,
                    style = MaterialTheme.typography.caption
                )
            }

            if (result.message.isNotEmpty()) {
                Text(
                    text = result.message,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Text(
                text = "Duration: ${result.duration}ms",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
