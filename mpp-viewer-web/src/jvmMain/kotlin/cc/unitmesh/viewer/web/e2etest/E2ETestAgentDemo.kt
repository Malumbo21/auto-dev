package cc.unitmesh.viewer.web.e2etest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import cc.unitmesh.agent.webagent.E2ETestAgent
import cc.unitmesh.agent.webagent.E2ETestConfig
import cc.unitmesh.agent.webagent.E2ETestInput
import cc.unitmesh.agent.webagent.executor.*
import cc.unitmesh.config.ConfigManager
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
    var targetUrl by remember { mutableStateOf("https://www.google.com") }
    var testGoal by remember { mutableStateOf("Search for 'Kotlin Multiplatform' and click on the first result") }
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

    // Auto-run test on startup
    LaunchedEffect(Unit) {
        println("[E2ETestDemo] LaunchedEffect started, waiting for KCEF...")
        // Wait for KCEF to initialize
        delay(3000)

        println("[E2ETestDemo] Starting auto-test...")
        addLog("Auto-starting E2E test...", LogType.INFO)
        testStatus = "Starting..."

        isRunning = true
        logs = emptyList()
        aiReasoning = ""
        currentStep = 0
        totalSteps = 0

        try {
            runAIE2ETest(
                bridge = bridge,
                browserDriver = browserDriver,
                pageStateExtractor = pageStateExtractor,
                targetUrl = targetUrl,
                testGoal = testGoal,
                onLog = { msg, type ->
                    println("[E2ETestDemo] $msg")
                    addLog(msg, type)
                },
                onStatusChange = { testStatus = it },
                onReasoningUpdate = { aiReasoning = it },
                onStepUpdate = { step, total ->
                    currentStep = step
                    totalSteps = total
                }
            )
        } catch (e: Exception) {
            println("[E2ETestDemo] Error: ${e.message}")
            e.printStackTrace()
        }

        isRunning = false
        addLog("Test completed!", LogType.SUCCESS)
        println("[E2ETestDemo] Test completed!")
    }

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
 * AI-driven E2E test execution using E2ETestAgent
 */
private suspend fun runAIE2ETest(
    bridge: JvmWebEditBridge,
    browserDriver: BrowserDriver,
    pageStateExtractor: cc.unitmesh.agent.webagent.perception.PageStateExtractor,
    targetUrl: String,
    testGoal: String,
    onLog: (String, LogType) -> Unit,
    onStatusChange: (String) -> Unit,
    onReasoningUpdate: (String) -> Unit,
    onStepUpdate: (Int, Int) -> Unit
) {
    try {
        // Step 1: Load LLM config from ConfigManager (~/.autodev/config.yaml)
        onLog("Loading LLM config from ~/.autodev/config.yaml...", LogType.INFO)

        val configWrapper = ConfigManager.load()
        val activeConfig = configWrapper.getActiveModelConfig()

        val modelConfig: ModelConfig? = if (activeConfig != null && configWrapper.isValid()) {
            onLog("Found config: ${configWrapper.getActiveName()} (${activeConfig.provider}/${activeConfig.modelName})", LogType.SUCCESS)
            activeConfig
        } else {
            // Fallback to environment variables
            onLog("No valid config in ~/.autodev/config.yaml, checking environment variables...", LogType.INFO)

            val apiKey = System.getenv("OPENAI_API_KEY")
                ?: System.getenv("ANTHROPIC_API_KEY")
                ?: System.getenv("DEEPSEEK_API_KEY")

            if (apiKey.isNullOrBlank()) {
                null
            } else {
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

                onLog("Using environment variable: $provider / $modelName", LogType.INFO)

                ModelConfig(
                    provider = provider,
                    modelName = modelName,
                    apiKey = apiKey,
                    temperature = 0.3,
                    maxTokens = 2048
                )
            }
        }

        if (modelConfig == null) {
            onLog("No LLM API key found. Please configure in ~/.autodev/config.yaml or set environment variable.", LogType.ERROR)
            onStatusChange("No API Key")
            onReasoningUpdate("No API key configured. Please configure in ~/.autodev/config.yaml or set OPENAI_API_KEY/ANTHROPIC_API_KEY/DEEPSEEK_API_KEY environment variable.")
            return
        }

        onLog("Using LLM: ${modelConfig.provider} / ${modelConfig.modelName}", LogType.INFO)

        val llmService = LLMService.create(modelConfig)

        // Step 2: Navigate to target URL first (required for bridge.isReady to become true)
        onLog("Navigating to $targetUrl...", LogType.INFO)
        onStatusChange("Navigating...")
        bridge.navigateTo(targetUrl)

        // Wait for page load - bridge.isReady becomes true after page loads
        var waitCount = 0
        while (!bridge.isReady.value && waitCount < 100) {
            delay(100)
            waitCount++
        }
        delay(2000) // Extra time for rendering and JS bridge initialization

        if (!bridge.isReady.value) {
            onLog("Page failed to load within timeout", LogType.ERROR)
            onStatusChange("Page Load Failed")
            onReasoningUpdate("Failed to load page: $targetUrl")
            return
        }

        onLog("Page loaded successfully", LogType.SUCCESS)

        // Step 3: Refresh accessibility tree and actionable elements
        onLog("Extracting page state...", LogType.INFO)
        bridge.refreshAccessibilityTree()
        bridge.refreshActionableElements()
        delay(500)

        // Step 4: Create and initialize E2ETestAgent
        onLog("Initializing E2ETestAgent...", LogType.INFO)
        onStatusChange("Initializing Agent...")

        val agentConfig = E2ETestConfig(
            headless = false,
            viewportWidth = 1280,
            viewportHeight = 720,
            defaultTimeoutMs = 10000,
            slowMotionMs = 500,
            enableSelfHealing = true,
            enableLLMHealing = true
        )

        val agent = E2ETestAgent(llmService, agentConfig)

        // Initialize with our bridge-based components
        agent.initializeWithDriver(browserDriver, pageStateExtractor)

        if (!agent.isAvailable) {
            onLog("E2ETestAgent is not available", LogType.ERROR)
            onStatusChange("Agent Not Available")
            onReasoningUpdate("Agent initialization failed. Check browser driver and page state extractor.")
            return
        }

        onLog("E2ETestAgent initialized successfully!", LogType.SUCCESS)
        onReasoningUpdate("Agent ready. Preparing to execute test: \"$testGoal\"")

        // Step 3: Create input and execute
        val input = E2ETestInput(
            naturalLanguage = testGoal,
            startUrl = targetUrl
        )

        onLog("Starting E2ETestAgent execution...", LogType.INFO)
        onStatusChange("Agent Running...")

        var stepCount = 0
        val result = agent.execute(input) { progress ->
            stepCount++
            onLog(progress, LogType.INFO)
            onStatusChange(progress.take(50))
            onStepUpdate(stepCount, stepCount + 1) // Approximate progress
            onReasoningUpdate("Agent progress: $progress")
        }

        // Step 4: Report results
        if (result.success) {
            onLog("E2ETestAgent completed successfully!", LogType.SUCCESS)
            onStatusChange("Test Passed!")
            onReasoningUpdate("Test completed successfully.\n\n${result.content}")
        } else {
            onLog("E2ETestAgent failed: ${result.content}", LogType.ERROR)
            onStatusChange("Test Failed")
            onReasoningUpdate("Test failed.\n\n${result.content}")
        }

        // Log metadata if available
        result.metadata?.forEach { (key, value) ->
            onLog("  $key: $value", LogType.INFO)
        }

        // Cleanup
        agent.close()

    } catch (e: Exception) {
        onLog("Error: ${e.message}", LogType.ERROR)
        onStatusChange("Error: ${e.message?.take(50)}")
        onReasoningUpdate("An error occurred: ${e.message}")
        e.printStackTrace()
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

