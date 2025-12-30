package cc.unitmesh.viewer.web.e2etest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cc.unitmesh.agent.e2etest.E2ETestContext
import cc.unitmesh.agent.e2etest.TestMemory
import cc.unitmesh.agent.e2etest.executor.*
import cc.unitmesh.agent.e2etest.model.*
import cc.unitmesh.agent.e2etest.perception.PageStateExtractor
import cc.unitmesh.agent.e2etest.planner.TestActionPlanner
import cc.unitmesh.llm.LLMService
import cc.unitmesh.llm.ModelConfig
import cc.unitmesh.llm.LLMProviderType
import cc.unitmesh.viewer.web.webedit.*
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * E2E Test Agent Demo Application
 * 
 * This demo shows how E2ETestAgent works with WebEditBridge:
 * 1. Displays a WebView with WebEditBridge
 * 2. Creates BrowserDriver and PageStateExtractor from the bridge
 * 3. Runs E2E test scenarios using the agent
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "E2E Test Agent Demo",
        state = rememberWindowState(width = 1600.dp, height = 1000.dp)
    ) {
        var restartRequired by remember { mutableStateOf(false) }
        var downloading by remember { mutableStateOf(0F) }
        var initialized by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val installDir = File(System.getProperty("user.home"), ".kcef")
                KCEF.init(builder = {
                    installDir(installDir)
                    progress {
                        onDownloading {
                            downloading = max(it, 0F)
                        }
                        onInitialized {
                            initialized = true
                        }
                    }
                    settings {
                        cachePath = File("kcef-cache").absolutePath
                    }
                }, onError = {
                    error = it?.localizedMessage
                }, onRestartRequired = {
                    restartRequired = true
                })
            }
        }

        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                when {
                    restartRequired -> RestartView()
                    !initialized -> LoadingView(downloading)
                    error != null -> ErrorView(error!!)
                    else -> E2ETestAgentDemoApp()
                }
            }
        }
    }
}

@Composable
private fun E2ETestAgentDemoApp() {
    val bridge = remember { createWebEditBridge() as JvmWebEditBridge }
    val scope = rememberCoroutineScope()

    // Test state
    var logs by remember { mutableStateOf(listOf<LogEntry>()) }
    var testStatus by remember { mutableStateOf("Ready") }
    var isRunning by remember { mutableStateOf(false) }
    var targetUrl by remember { mutableStateOf("https://www.phodal.com") }
    var testGoal by remember { mutableStateOf("Navigate to the blog section and find an article about AI") }
    var aiReasoning by remember { mutableStateOf("") }
    var currentStep by remember { mutableStateOf(0) }
    var totalSteps by remember { mutableStateOf(0) }

    // Bridge state
    val isReady by bridge.isReady.collectAsState()
    val currentUrl by bridge.currentUrl.collectAsState()
    val pageTitle by bridge.pageTitle.collectAsState()
    val actionableElements by bridge.actionableElements.collectAsState()

    fun addLog(message: String, type: LogType = LogType.INFO) {
        logs = logs + LogEntry(message, type)
    }

    // Create BrowserDriver and PageStateExtractor from bridge
    val browserDriver = remember { bridge.asBrowserDriver() }
    val pageStateExtractor = remember { bridge.asPageStateExtractor() }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left panel: Controls and Logs
        Column(
            modifier = Modifier
                .width(550.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Text("AI E2E Test Agent", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(12.dp))

            // Status
            StatusCard(testStatus, isReady, currentUrl, pageTitle, actionableElements.size)
            Spacer(modifier = Modifier.height(12.dp))

            // Test Configuration
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Test Goal (Natural Language)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = testGoal,
                        onValueChange = { testGoal = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRunning,
                        minLines = 2,
                        maxLines = 4,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetUrl,
                        onValueChange = { targetUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRunning,
                        label = { Text("Target URL") },
                        singleLine = true,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isRunning = true
                                    logs = emptyList()
                                    aiReasoning = ""
                                    currentStep = 0
                                    totalSteps = 0

                                    runAIE2ETest(
                                        bridge = bridge,
                                        browserDriver = browserDriver,
                                        pageStateExtractor = pageStateExtractor,
                                        targetUrl = targetUrl,
                                        testGoal = testGoal,
                                        onLog = { msg, type -> addLog(msg, type) },
                                        onStatusChange = { testStatus = it },
                                        onReasoningUpdate = { aiReasoning = it },
                                        onStepUpdate = { step, total ->
                                            currentStep = step
                                            totalSteps = total
                                        }
                                    )

                                    isRunning = false
                                }
                            },
                            enabled = !isRunning && isReady
                        ) {
                            Text(if (isRunning) "Running..." else "Run AI Test")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    addLog("Navigating to $targetUrl...", LogType.INFO)
                                    bridge.navigateTo(targetUrl)
                                }
                            },
                            enabled = !isRunning
                        ) {
                            Text("Navigate")
                        }
                    }

                    if (totalSteps > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { currentStep.toFloat() / totalSteps },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Step $currentStep / $totalSteps", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // AI Reasoning Panel
            if (aiReasoning.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E).copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("AI Reasoning", style = MaterialTheme.typography.titleMedium, color = Color(0xFF90CAF9))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(aiReasoning, style = MaterialTheme.typography.bodySmall, color = Color(0xFFBBDEFB))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Logs
            LogPanel(logs, Modifier.weight(1f))
        }

        // Right panel: WebView
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            WebEditView(
                bridge = bridge,
                modifier = Modifier.fillMaxSize(),
                onPageLoaded = { url, title -> addLog("Page loaded: $title") },
                onElementSelected = { element -> addLog("Selected: ${element.tagName}") },
                onDOMTreeUpdated = { root -> addLog("DOM updated: ${root.children.size} children") }
            )
        }
    }
}

@Composable
private fun StatusCard(
    status: String,
    isReady: Boolean,
    currentUrl: String,
    pageTitle: String,
    elementCount: Int
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Status: $status", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Bridge Ready: $isReady", style = MaterialTheme.typography.bodySmall)
            Text("URL: ${currentUrl.take(50)}", style = MaterialTheme.typography.bodySmall)
            Text("Title: ${pageTitle.take(30)}", style = MaterialTheme.typography.bodySmall)
            Text("Actionable Elements: $elementCount", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LogPanel(logs: List<LogEntry>, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Logs", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            val listState = rememberLazyListState()
            LaunchedEffect(logs.size) {
                if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs) { log ->
                    Text(
                        text = log.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (log.type) {
                            LogType.ERROR -> Color.Red
                            LogType.SUCCESS -> Color.Green
                            LogType.WARNING -> Color.Yellow
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

/**
 * AI-driven E2E test execution using LLM for planning and decision making
 */
private suspend fun runAIE2ETest(
    bridge: JvmWebEditBridge,
    browserDriver: BrowserDriver,
    pageStateExtractor: cc.unitmesh.agent.e2etest.perception.PageStateExtractor,
    targetUrl: String,
    testGoal: String,
    onLog: (String, LogType) -> Unit,
    onStatusChange: (String) -> Unit,
    onReasoningUpdate: (String) -> Unit,
    onStepUpdate: (Int, Int) -> Unit
) {
    try {
        // Step 1: Navigate to target URL
        onLog("Navigating to $targetUrl...", LogType.INFO)
        onStatusChange("Navigating...")
        bridge.navigateTo(targetUrl)

        // Wait for page load
        var waitCount = 0
        while (!bridge.isReady.value && waitCount < 100) {
            delay(100)
            waitCount++
        }
        delay(2000) // Extra time for rendering

        // Step 2: Extract initial page state
        onLog("Extracting page state...", LogType.INFO)
        onStatusChange("Analyzing page...")
        bridge.refreshAccessibilityTree()
        bridge.refreshActionableElements()
        delay(500)

        val initialPageState = pageStateExtractor.extractPageState()
        onLog("Found ${initialPageState.actionableElements.size} actionable elements", LogType.SUCCESS)

        // Log some elements for visibility
        initialPageState.actionableElements.take(5).forEach { element ->
            onLog("  [${element.tagId}] ${element.role}: ${element.name?.take(25) ?: "..."}", LogType.INFO)
        }

        // Step 3: Create LLM service (check for API key in environment)
        val apiKey = System.getenv("OPENAI_API_KEY")
            ?: System.getenv("ANTHROPIC_API_KEY")
            ?: System.getenv("DEEPSEEK_API_KEY")

        if (apiKey.isNullOrBlank()) {
            onLog("No LLM API key found. Using rule-based fallback.", LogType.WARNING)
            onReasoningUpdate("No API key configured. Set OPENAI_API_KEY, ANTHROPIC_API_KEY, or DEEPSEEK_API_KEY environment variable.")
            runRuleBasedTest(bridge, browserDriver, pageStateExtractor, testGoal, onLog, onStatusChange, onStepUpdate)
            return
        }

        val provider = when {
            System.getenv("DEEPSEEK_API_KEY") != null -> LLMProviderType.DEEPSEEK
            System.getenv("ANTHROPIC_API_KEY") != null -> LLMProviderType.ANTHROPIC
            else -> LLMProviderType.OPENAI
        }

        val modelName = when (provider) {
            LLMProviderType.DEEPSEEK -> "deepseek-chat"
            LLMProviderType.ANTHROPIC -> "claude-3-5-sonnet-20241022"
            else -> "gpt-4o-mini"
        }

        onLog("Using LLM: $provider / $modelName", LogType.INFO)

        val modelConfig = ModelConfig(
            provider = provider,
            modelName = modelName,
            apiKey = apiKey,
            temperature = 0.3,
            maxTokens = 2048
        )

        val llmService = LLMService.create(modelConfig)
        val planner = TestActionPlanner(llmService)

        // Step 4: Generate test scenario using LLM
        onLog("AI is analyzing the page and planning test steps...", LogType.INFO)
        onStatusChange("AI Planning...")
        onReasoningUpdate("Analyzing page structure and understanding test goal: \"$testGoal\"")

        val scenario = planner.generateScenario(testGoal, targetUrl, initialPageState)

        if (scenario == null) {
            onLog("LLM failed to generate scenario. Using intelligent fallback.", LogType.WARNING)
            onReasoningUpdate("Could not generate scenario from LLM. Falling back to rule-based approach.")
            runRuleBasedTest(bridge, browserDriver, pageStateExtractor, testGoal, onLog, onStatusChange, onStepUpdate)
            return
        }

        onLog("AI generated scenario: ${scenario.name}", LogType.SUCCESS)
        onLog("Steps: ${scenario.steps.size}", LogType.INFO)
        onStepUpdate(0, scenario.steps.size)

        // Step 5: Execute each step
        val executor = JvmBrowserActionExecutor.withDriver(browserDriver)
        var memory = TestMemory.empty()

        for ((index, step) in scenario.steps.withIndex()) {
            onStepUpdate(index + 1, scenario.steps.size)
            onStatusChange("Step ${index + 1}: ${step.description}")
            onLog("Executing: ${step.description}", LogType.INFO)
            onReasoningUpdate("Step ${index + 1}/${scenario.steps.size}: ${step.description}\nExpected: ${step.expectedOutcome ?: "Action completes successfully"}")

            // Refresh page state before each action
            bridge.refreshActionableElements()
            delay(300)
            val currentPageState = pageStateExtractor.extractPageState()

            val context = ActionExecutionContext(
                tagMapping = currentPageState.actionableElements.associateBy { it.tagId }
            )
            executor.setContext(context)

            val result = executor.execute(step.action, context)

            if (result.success) {
                onLog("Step ${index + 1} succeeded (${result.durationMs}ms)", LogType.SUCCESS)
                delay(1000) // Wait for page to update
            } else {
                onLog("Step ${index + 1} failed: ${result.error}", LogType.ERROR)

                // Try self-healing: ask LLM for alternative
                onReasoningUpdate("Step failed. Attempting self-healing...")
                // For now, continue to next step
            }

            memory = memory.withAction(
                cc.unitmesh.agent.e2etest.ActionRecord(
                    actionType = step.action::class.simpleName ?: "Unknown",
                    targetId = getTargetIdFromAction(step.action),
                    timestamp = System.currentTimeMillis(),
                    success = result.success,
                    description = step.description
                )
            )
        }

        // Step 6: Report results
        val passedSteps = memory.recentActions.count { it.success }
        val totalSteps = scenario.steps.size

        if (passedSteps == totalSteps) {
            onLog("All $totalSteps steps passed!", LogType.SUCCESS)
            onStatusChange("Test Passed!")
            onReasoningUpdate("Test completed successfully. All $totalSteps steps executed without errors.")
        } else {
            onLog("$passedSteps/$totalSteps steps passed", LogType.WARNING)
            onStatusChange("Partial Success: $passedSteps/$totalSteps")
            onReasoningUpdate("Test completed with some failures. $passedSteps out of $totalSteps steps succeeded.")
        }

    } catch (e: Exception) {
        onLog("Error: ${e.message}", LogType.ERROR)
        onStatusChange("Error: ${e.message?.take(50)}")
        onReasoningUpdate("An error occurred: ${e.message}")
        e.printStackTrace()
    }
}

/**
 * Rule-based fallback when LLM is not available
 */
private suspend fun runRuleBasedTest(
    bridge: JvmWebEditBridge,
    browserDriver: BrowserDriver,
    pageStateExtractor: cc.unitmesh.agent.e2etest.perception.PageStateExtractor,
    testGoal: String,
    onLog: (String, LogType) -> Unit,
    onStatusChange: (String) -> Unit,
    onStepUpdate: (Int, Int) -> Unit
) {
    onLog("Running rule-based test for: $testGoal", LogType.INFO)
    onStatusChange("Rule-based execution...")

    val pageState = pageStateExtractor.extractPageState()
    val executor = JvmBrowserActionExecutor.withDriver(browserDriver)

    // Simple heuristic: find elements matching keywords in the goal
    val keywords = testGoal.lowercase().split(" ").filter { it.length > 3 }

    val matchingElements = pageState.actionableElements.filter { element ->
        val name = element.name?.lowercase() ?: ""
        keywords.any { keyword -> name.contains(keyword) }
    }

    if (matchingElements.isEmpty()) {
        onLog("No elements matching goal keywords found", LogType.WARNING)
        onStatusChange("No matching elements")
        return
    }

    onLog("Found ${matchingElements.size} elements matching goal", LogType.SUCCESS)
    onStepUpdate(0, matchingElements.size.coerceAtMost(3))

    // Click up to 3 matching elements
    matchingElements.take(3).forEachIndexed { index, element ->
        onStepUpdate(index + 1, matchingElements.size.coerceAtMost(3))
        onLog("Clicking: [${element.tagId}] ${element.name}", LogType.INFO)

        val context = ActionExecutionContext(
            tagMapping = pageState.actionableElements.associateBy { it.tagId }
        )
        executor.setContext(context)

        val result = executor.click(element.tagId, ClickOptions())
        if (result.success) {
            onLog("Click succeeded", LogType.SUCCESS)
        } else {
            onLog("Click failed: ${result.error}", LogType.ERROR)
        }

        delay(1000)
    }

    onStatusChange("Rule-based test completed")
}

private fun getTargetIdFromAction(action: TestAction): Int? {
    return when (action) {
        is TestAction.Click -> action.targetId
        is TestAction.Type -> action.targetId
        is TestAction.Hover -> action.targetId
        is TestAction.Assert -> action.targetId
        is TestAction.Select -> action.targetId
        is TestAction.Scroll -> action.targetId
        else -> null
    }
}

@Composable
private fun RestartView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Please restart the application to complete KCEF installation.")
    }
}

@Composable
private fun LoadingView(progress: Float) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Initializing KCEF...")
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.width(300.dp))
        Text("${(progress * 100).toInt()}%")
    }
}

@Composable
private fun ErrorView(error: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Error: $error", color = Color.Red)
    }
}

data class LogEntry(val message: String, val type: LogType = LogType.INFO)

enum class LogType { INFO, SUCCESS, WARNING, ERROR }

