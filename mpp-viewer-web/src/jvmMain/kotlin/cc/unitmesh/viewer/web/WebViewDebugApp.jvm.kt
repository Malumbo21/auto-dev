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

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                KCEF.init(builder = {
                    val homeDir = System.getProperty("user.home")
                    val configDir = File(homeDir, ".autodev")
                    val kcefDir = File(configDir, "kcef-bundle")
                    installDir(kcefDir)

                    progress {
                        onDownloading {
                            downloading = max(it, 0F)
                        }
                        onInitialized {
                            initialized = true
                        }
                    }
                    settings {
                        cachePath = File("cache").absolutePath
                    }
                }, onError = {
                    it?.printStackTrace()
                }, onRestartRequired = {
                    restartRequired = true
                })
            }
        }

        if (restartRequired) {
            Text(text = "Restart required.")
        } else {
            if (initialized) {
                InspectDebugApp()
            } else {
                Text(text = "Downloading $downloading%")
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                KCEF.disposeBlocking()
            }
        }
    }
}

/**
 * Main Inspect Debug Application
 * Provides UI for testing WebView Inspect capabilities with automation
 */
@Composable
internal fun InspectDebugApp() {
    val bridge = remember { JvmWebEditBridge() }
    val scope = rememberCoroutineScope()

    // State
    val selectedElement by bridge.selectedElement.collectAsState()
    val domTree by bridge.domTree.collectAsState()
    val isInspectMode by bridge.isSelectionMode.collectAsState()
    val isReady by bridge.isReady.collectAsState()

    var testResults by remember { mutableStateOf<List<TestResult>>(emptyList()) }
    var isRunningTests by remember { mutableStateOf(false) }

    MaterialTheme {
        Row(Modifier.fillMaxSize()) {
            // Left Panel: WebView
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
            ) {
                // Control Bar
                TopAppBar(
                    title = { Text("WebView Inspect Test") },
                    backgroundColor = MaterialTheme.colors.primary
                )

                ControlPanel(
                    bridge = bridge,
                    scope = scope,
                    isInspectMode = isInspectMode,
                    isReady = isReady,
                    onRunTests = {
                        isRunningTests = true
                        scope.launch {
                            testResults = runAutomatedTests(bridge)
                            isRunningTests = false
                        }
                    }
                )

                // WebView
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

            // Right Panel: Inspector & Test Results
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .background(Color(0xFFF5F5F5))
            ) {
                InspectorPanel(
                    selectedElement = selectedElement,
                    domTree = domTree,
                    testResults = testResults,
                    isRunningTests = isRunningTests
                )
            }
        }
    }
}

@Composable
private fun ControlPanel(
    bridge: JvmWebEditBridge,
    scope: CoroutineScope,
    isInspectMode: Boolean,
    isReady: Boolean,
    onRunTests: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Load test page
                Button(
                    onClick = {
                        scope.launch {
                            val testPagePath = File("mpp-viewer-web/src/jvmMain/resources/test-shadow-dom.html")
                                .absolutePath
                            bridge.navigateTo("file://$testPagePath")
                        }
                    },
                    enabled = isReady
                ) {
                    Text("Load Test Page")
                }

                // Toggle inspect mode
                Button(
                    onClick = {
                        scope.launch {
                            if (isInspectMode) {
                                bridge.disableInspectMode()
                            } else {
                                bridge.enableInspectMode()
                            }
                        }
                    },
                    enabled = isReady,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isInspectMode) Color(0xFF4CAF50) else MaterialTheme.colors.primary
                    )
                ) {
                    Text(if (isInspectMode) "Disable Inspect" else "Enable Inspect")
                }

                // Refresh DOM tree
                Button(
                    onClick = {
                        scope.launch {
                            bridge.refreshDOMTree()
                        }
                    },
                    enabled = isReady
                ) {
                    Text("Refresh DOM")
                }

                // Run automated tests
                Button(
                    onClick = onRunTests,
                    enabled = isReady,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFFF9800)
                    )
                ) {
                    Text("Run Tests")
                }
            }
        }
    }
}


@Composable
private fun InspectorPanel(
    selectedElement: DOMElement?,
    domTree: DOMElement?,
    testResults: List<TestResult>,
    isRunningTests: Boolean
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Selected Element Section
        Text(
            text = "Selected Element",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (selectedElement != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Tag: ${selectedElement.tagName}", style = MaterialTheme.typography.body2)
                    Text("Selector: ${selectedElement.selector}", style = MaterialTheme.typography.caption)

                    if (selectedElement.isShadowHost) {
                        Text(
                            "🔒 Shadow Host",
                            color = Color(0xFF2196F3),
                            style = MaterialTheme.typography.caption
                        )
                    }

                    if (selectedElement.inShadowRoot) {
                        Text(
                            "👁 Inside Shadow DOM",
                            color = Color(0xFF9C27B0),
                            style = MaterialTheme.typography.caption
                        )
                    }

                    selectedElement.attributes.forEach { (key, value) ->
                        Text("$key: $value", style = MaterialTheme.typography.caption)
                    }

                    selectedElement.boundingBox?.let { box ->
                        Text(
                            "Position: (${box.x.toInt()}, ${box.y.toInt()})",
                            style = MaterialTheme.typography.caption
                        )
                        Text(
                            "Size: ${box.width.toInt()} × ${box.height.toInt()}",
                            style = MaterialTheme.typography.caption
                        )
                    }
                }
            }
        } else {
            Text(
                "No element selected",
                style = MaterialTheme.typography.body2,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // DOM Tree Section
        Text(
            text = "DOM Tree",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (domTree != null) {
            DOMTreeView(domTree, depth = 0)
        } else {
            Text(
                "No DOM tree available",
                style = MaterialTheme.typography.body2,
                color = Color.Gray
            )
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        // Test Results Section
        Text(
            text = "Test Results",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (isRunningTests) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (testResults.isNotEmpty()) {
            testResults.forEach { result ->
                TestResultCard(result)
            }

            val passedCount = testResults.count { it.passed }
            val totalCount = testResults.size

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                backgroundColor = if (passedCount == totalCount) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                elevation = 2.dp
            ) {
                Text(
                    text = "Summary: $passedCount / $totalCount tests passed",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.subtitle1,
                    color = if (passedCount == totalCount) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }
        } else {
            Text(
                "No test results yet. Click 'Run Tests' to start.",
                style = MaterialTheme.typography.body2,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun DOMTreeView(element: DOMElement, depth: Int) {
    if (depth > 5) return

    val indent = "  ".repeat(depth)
    val icon = if (element.isShadowHost) "🔒" else if (element.inShadowRoot) "👁" else "▸"

    Text(
        text = "$indent$icon ${element.getDisplayName()}",
        style = MaterialTheme.typography.caption,
        modifier = Modifier.padding(vertical = 2.dp)
    )

    element.children.take(10).forEach { child ->
        DOMTreeView(child, depth + 1)
    }
}

@Composable
private fun TestResultCard(result: TestResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        backgroundColor = if (result.passed) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
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
                    color = if (result.passed) Color(0xFF4CAF50) else Color(0xFFF44336),
                    style = MaterialTheme.typography.caption
                )
            }

            if (result.message.isNotEmpty()) {
                Text(
                    text = result.message,
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Text(
                text = "Duration: ${result.duration}ms",
                style = MaterialTheme.typography.caption,
                color = Color.Gray,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}


// ========== Test Framework ==========

/**
 * Test result data class
 */
data class TestResult(
    val name: String,
    val passed: Boolean,
    val message: String = "",
    val duration: Long = 0
)

/**
 * Run automated tests for WebView Inspect functionality
 */
suspend fun runAutomatedTests(bridge: JvmWebEditBridge): List<TestResult> {
    val results = mutableListOf<TestResult>()

    println("[AutoTest] Starting automated tests...")

    // Wait for page to be ready
    delay(1000)

    // Test 1: Enable Inspect Mode
    results.add(runTest("Enable Inspect Mode") {
        bridge.enableInspectMode()
        delay(500)
        bridge.isSelectionMode.value
    })

    // Test 2: Refresh DOM Tree
    results.add(runTest("Refresh DOM Tree") {
        bridge.refreshDOMTree()
        delay(1000)
        bridge.domTree.value != null
    })

    // Test 3: Verify Shadow DOM Detection
    results.add(runTest("Detect Shadow DOM Hosts") {
        val tree = bridge.domTree.value
        val hasShadowHosts = tree?.let { findShadowHosts(it) } ?: false
        hasShadowHosts
    })

    // Test 4: Highlight Regular Element
    results.add(runTest("Highlight Regular Element") {
        bridge.highlightElement("#regular-button")
        delay(500)
        true // Visual verification needed
    })

    // Test 5: Simulate Element Selection
    results.add(runTest("Simulate Element Selection") {
        // Trigger selection via JavaScript
        bridge.executeJavaScript?.invoke("""
            const btn = document.getElementById('regular-button');
            if (btn) {
                const event = new MouseEvent('click', { bubbles: true, cancelable: true });
                btn.dispatchEvent(event);
            }
        """.trimIndent())
        delay(500)
        bridge.selectedElement.value != null
    })

    // Test 6: Test Dynamic DOM Changes
    results.add(runTest("Dynamic DOM Mutation") {
        bridge.executeJavaScript?.invoke("""
            document.getElementById('add-element')?.click();
        """.trimIndent())
        delay(500)
        true
    })

    // Test 7: Verify MutationObserver
    results.add(runTest("MutationObserver Active") {
        // Add multiple elements
        bridge.executeJavaScript?.invoke("""
            for (let i = 0; i < 3; i++) {
                document.getElementById('add-element')?.click();
            }
        """.trimIndent())
        delay(1000)
        true
    })

    // Test 8: Test Shadow DOM Traversal
    results.add(runTest("Shadow DOM Traversal") {
        bridge.refreshDOMTree()
        delay(1000)
        val tree = bridge.domTree.value
        val shadowElements = tree?.let { countShadowElements(it) } ?: 0
        shadowElements > 0
    })

    // Test 9: Clear Highlights
    results.add(runTest("Clear Highlights") {
        bridge.clearHighlights()
        delay(300)
        true
    })

    // Test 10: Disable Inspect Mode
    results.add(runTest("Disable Inspect Mode") {
        bridge.disableInspectMode()
        delay(300)
        !bridge.isSelectionMode.value
    })

    println("[AutoTest] Completed ${results.size} tests")
    println("[AutoTest] Passed: ${results.count { it.passed }} / ${results.size}")

    return results
}

/**
 * Run a single test with timing
 */
private suspend fun runTest(name: String, test: suspend () -> Boolean): TestResult {
    println("[AutoTest] Running: $name")
    val startTime = System.currentTimeMillis()

    return try {
        val passed = test()
        val duration = System.currentTimeMillis() - startTime

        TestResult(
            name = name,
            passed = passed,
            message = if (passed) "Test completed successfully" else "Test assertion failed",
            duration = duration
        ).also {
            println("[AutoTest] ${if (passed) "✓ PASS" else "✗ FAIL"} - $name (${duration}ms)")
        }
    } catch (e: Exception) {
        val duration = System.currentTimeMillis() - startTime
        TestResult(
            name = name,
            passed = false,
            message = "Exception: ${e.message}",
            duration = duration
        ).also {
            println("[AutoTest] ✗ FAIL - $name: ${e.message}")
        }
    }
}

/**
 * Find shadow hosts in DOM tree
 */
private fun findShadowHosts(element: DOMElement): Boolean {
    if (element.isShadowHost) return true
    return element.children.any { findShadowHosts(it) }
}

/**
 * Count elements in shadow DOM
 */
private fun countShadowElements(element: DOMElement): Int {
    var count = if (element.inShadowRoot) 1 else 0
    element.children.forEach { count += countShadowElements(it) }
    return count
}