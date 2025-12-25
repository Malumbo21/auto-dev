package cc.unitmesh.devins.ui.compose.agent.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.agent.ArtifactAgent
import cc.unitmesh.agent.artifact.executor.ArtifactExecutorFactory
import cc.unitmesh.agent.artifact.executor.ExecutionResult
import cc.unitmesh.agent.logging.AutoDevLogger
import cc.unitmesh.viewer.web.KcefInitState
import cc.unitmesh.viewer.web.KcefManager
import com.multiplatform.webview.jsbridge.IJsMessageHandler
import com.multiplatform.webview.jsbridge.JsMessage
import com.multiplatform.webview.jsbridge.rememberWebViewJsBridge
import com.multiplatform.webview.util.KLogSeverity
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * JVM implementation of ArtifactPreviewPanel using compose-webview-multiplatform.
 * This uses KCEF (initialized by KcefManager) to render HTML artifacts.
 */
@Composable
actual fun ArtifactPreviewPanel(
    artifact: ArtifactAgent.Artifact,
    onConsoleLog: (String, String) -> Unit,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    val logger = AutoDevLogger

    // Check KCEF initialization state
    val kcefInitState by KcefManager.initState.collectAsState()

    // Toggle between preview and source view
    var showSource by remember { mutableStateOf(false) }

    // Node.js execution state
    var isNodeJsType by remember { mutableStateOf(artifact.type == ArtifactAgent.Artifact.ArtifactType.NODEJS) }
    var isExecuting by remember { mutableStateOf(false) }
    var executionOutput by remember { mutableStateOf<String>("") }
    var executionError by remember { mutableStateOf<String?>(null) }

    // Prepare HTML with console.log interception script
    val htmlWithConsole = remember(artifact.content) {
        ArtifactConsoleBridgeJvm.injectConsoleCapture(artifact.content)
    }

    Column(modifier = modifier) {
        // Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title
                Text(
                    text = artifact.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Execute button for Node.js artifacts
                    if (isNodeJsType) {
                        IconButton(
                            onClick = {
                                if (!isExecuting) {
                                    isExecuting = true
                                    executionOutput = ""
                                    executionError = null
                                    scope.launch {
                                        try {
                                            executeNodeJsArtifact(artifact, onConsoleLog) { output, error ->
                                                executionOutput = output
                                                executionError = error
                                            }
                                        } finally {
                                            isExecuting = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(32.dp),
                            enabled = !isExecuting
                        ) {
                            if (isExecuting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Execute",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Toggle source/preview
                    IconButton(
                        onClick = { showSource = !showSource },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (showSource) Icons.Default.Visibility else Icons.Default.Code,
                            contentDescription = if (showSource) "Show Preview" else "Show Source",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Open in browser (only for HTML artifacts)
                    if (!isNodeJsType) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    openInBrowser(artifact.content, artifact.title)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Open in Browser",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Save file
                    IconButton(
                        onClick = {
                            saveArtifactFile(artifact)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Content area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                showSource -> {
                    // Show source code
                    SourceCodeView(
                        content = artifact.content,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                isNodeJsType -> {
                    // Node.js execution view
                    NodeJsExecutionView(
                        artifact = artifact,
                        isExecuting = isExecuting,
                        output = executionOutput,
                        error = executionError,
                        onConsoleLog = onConsoleLog,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                kcefInitState !is KcefInitState.Initialized -> {
                    // KCEF not ready - show loading or fallback
                    KcefStatusView(
                        state = kcefInitState,
                        onShowSource = { showSource = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    // KCEF ready - show WebView
                    ArtifactWebView(
                        html = htmlWithConsole,
                        onConsoleLog = onConsoleLog,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * WebView component using compose-webview-multiplatform
 */
@Composable
private fun ArtifactWebView(
    html: String,
    onConsoleLog: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    // IMPORTANT:
    // Start from about:blank so that JS bridge handlers are registered BEFORE we inject HTML.
    // Otherwise, early console.log calls during page parse can run before kmpJsBridge is available,
    // causing logs to be dropped (same class of issue as WebEditView.jvm.kt).
    val webViewState = rememberWebViewState(url = "about:blank")
    val webViewNavigator = rememberWebViewNavigator()
    val jsBridge = rememberWebViewJsBridge()

    // Register JS message handler for console.log capture
    LaunchedEffect(Unit) {
        jsBridge.register(object : IJsMessageHandler {
            override fun methodName(): String = ArtifactConsoleBridgeJvm.METHOD_NAME

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit
            ) {
                try {
                    val (level, msg) = ArtifactConsoleBridgeJvm.parseConsoleParams(message.params)
                    if (msg.isNotBlank()) onConsoleLog(level, msg)
                } catch (e: Exception) {
                    onConsoleLog("warn", "Error handling console message: ${e.message}")
                }
                callback("{}")
            }
        })
    }

    // Configure WebView settings early (helps debugging + parity with WebEditView)
    LaunchedEffect(Unit) {
        webViewState.webSettings.apply {
            // Avoid noisy CEF console output (e.g. internal bridge callback logs)
            logSeverity = KLogSeverity.Error
            allowUniversalAccessFromFileURLs = true
        }
    }

    // Inject/refresh HTML only after the initial about:blank load is finished.
    // This ensures kmpJsBridge is available when the page's scripts (console.log) run.
    LaunchedEffect(webViewState.isLoading, html) {
        val finished = !webViewState.isLoading && webViewState.loadingState is LoadingState.Finished
        if (finished) {
            // Small delay to give the bridge time to attach in some environments.
            delay(50)
            val escaped = ArtifactConsoleBridgeJvm.escapeForTemplateLiteral(html)
            val js = """
                try {
                  document.open();
                  document.write(`$escaped`);
                  document.close();
                } catch (e) {
                  console.error('ArtifactWebView document.write failed:', e);
                }
            """.trimIndent()
            webViewNavigator.evaluateJavaScript(js)
        }
    }

    WebView(
        state = webViewState,
        navigator = webViewNavigator,
        modifier = modifier,
        captureBackPresses = false,
        webViewJsBridge = jsBridge
    )
}

/**
 * KCEF status view - shown when KCEF is not initialized
 */
@Composable
private fun KcefStatusView(
    state: KcefInitState,
    onShowSource: () -> Unit,
    modifier: Modifier = Modifier
) {
    val downloadProgress by KcefManager.downloadProgress.collectAsState()

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (state) {
            is KcefInitState.Idle -> {
                Icon(
                    imageVector = Icons.Default.Web,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "WebView not initialized",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Click 'Show Source' to view the generated code",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onShowSource) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Show Source")
                }
            }

            is KcefInitState.Initializing -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Initializing WebView...",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (downloadProgress > 0f && downloadProgress < 100f) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.width(200.dp)
                    )
                    Text(
                        text = "${downloadProgress.toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            is KcefInitState.RestartRequired -> {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Restart Required",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Please restart the application to enable WebView",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is KcefInitState.Error -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "WebView Error",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = state.exception.message ?: "Unknown error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onShowSource) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Show Source Instead")
                }
            }

            is KcefInitState.Initialized -> {
                // Should not reach here
            }
        }
    }
}

/**
 * Source code view
 */
@Composable
private fun SourceCodeView(
    content: String,
    modifier: Modifier = Modifier
) {
    SelectionContainer {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// (console bridge logic extracted to ArtifactConsoleBridgeJvm)

/**
 * Open HTML content in system browser
 */
private suspend fun openInBrowser(html: String, title: String) {
    withContext(Dispatchers.IO) {
        try {
            val tempFile = File.createTempFile("artifact_${title.replace(" ", "_")}_", ".html")
            tempFile.writeText(html)
            tempFile.deleteOnExit()

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(tempFile.toURI())
            }
        } catch (e: Exception) {
            println("Failed to open in browser: ${e.message}")
        }
    }
}

/**
 * Save artifact to file
 */
private fun saveArtifactFile(artifact: ArtifactAgent.Artifact) {
    try {
        val fileChooser = JFileChooser().apply {
            dialogTitle = "Save Artifact"
            selectedFile = File("${artifact.title.replace(" ", "_")}.html")
            fileFilter = FileNameExtensionFilter("HTML Files", "html", "htm")
        }

        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            var file = fileChooser.selectedFile
            if (!file.name.endsWith(".html") && !file.name.endsWith(".htm")) {
                file = File(file.absolutePath + ".html")
            }
            file.writeText(artifact.content)
        }
    } catch (e: Exception) {
        println("Failed to save file: ${e.message}")
    }
}

/**
 * Export artifact implementation for JVM
 * Supports both .html (raw) and .unit (bundle with metadata) formats
 */
actual fun exportArtifact(
    artifact: ArtifactAgent.Artifact,
    onNotification: (String, String) -> Unit
) {
    try {
        val sanitizedName = artifact.title.replace(Regex("[^a-zA-Z0-9\\-_ ]"), "").replace(" ", "_")

        val fileChooser = JFileChooser().apply {
            dialogTitle = "Export Artifact"
            selectedFile = File("$sanitizedName.unit")

            // Add filter for .unit bundle format (recommended)
            val unitFilter = FileNameExtensionFilter("AutoDev Unit Bundle (*.unit)", "unit")
            addChoosableFileFilter(unitFilter)
            // Add filter for raw HTML
            addChoosableFileFilter(FileNameExtensionFilter("HTML Files (*.html)", "html", "htm"))

            fileFilter = unitFilter // Default to .unit
        }

        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            var file = fileChooser.selectedFile
            val selectedFilter = fileChooser.fileFilter

            // Determine format based on filter or extension
            val isUnitFormat = when {
                file.name.endsWith(".unit") -> true
                file.name.endsWith(".html") || file.name.endsWith(".htm") -> false
                selectedFilter.description.contains("unit", ignoreCase = true) -> {
                    file = File(file.absolutePath + ".unit")
                    true
                }
                else -> {
                    file = File(file.absolutePath + ".html")
                    false
                }
            }

            if (isUnitFormat) {
                // Export as .unit bundle (without conversation history - use exportArtifactBundle for full bundle)
                val bundle = cc.unitmesh.agent.artifact.ArtifactBundle.fromArtifact(
                    artifact = artifact,
                    conversationHistory = emptyList(),
                    modelInfo = null
                )
                exportAsUnitBundle(bundle, file, onNotification)
            } else {
                // Export as raw HTML
                file.writeText(artifact.content)
                onNotification("success", "Artifact exported to ${file.absolutePath}")
            }
        }
    } catch (e: Exception) {
        onNotification("error", "Failed to export: ${e.message}")
    }
}

/**
 * Export artifact as .unit bundle format
 */
private fun exportAsUnitBundle(
    bundle: cc.unitmesh.agent.artifact.ArtifactBundle,
    outputFile: File,
    onNotification: (String, String) -> Unit
) {
    try {
        // Pack bundle using coroutines
        kotlinx.coroutines.runBlocking {
            val packer = cc.unitmesh.agent.artifact.ArtifactBundlePacker()
            when (val result = packer.pack(bundle, outputFile.absolutePath)) {
                is cc.unitmesh.agent.artifact.PackResult.Success -> {
                    onNotification("success", "Artifact bundle exported to ${result.outputPath}")
                }
                is cc.unitmesh.agent.artifact.PackResult.Error -> {
                    onNotification("error", "Failed to export bundle: ${result.message}")
                }
            }
        }
    } catch (e: Exception) {
        onNotification("error", "Failed to create bundle: ${e.message}")
    }
}

/**
 * Export artifact bundle implementation for JVM
 * This is called from ArtifactPage with full conversation history
 */
actual fun exportArtifactBundle(
    bundle: cc.unitmesh.agent.artifact.ArtifactBundle,
    onNotification: (String, String) -> Unit
) {
    try {
        val sanitizedName = bundle.name.replace(Regex("[^a-zA-Z0-9\\-_ ]"), "").replace(" ", "_")

        val fileChooser = JFileChooser().apply {
            dialogTitle = "Export Artifact Bundle"
            selectedFile = File("$sanitizedName.unit")

            // Only .unit format for bundle export
            val unitFilter = FileNameExtensionFilter("AutoDev Unit Bundle (*.unit)", "unit")
            addChoosableFileFilter(unitFilter)
            fileFilter = unitFilter
        }

        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            var file = fileChooser.selectedFile
            if (!file.name.endsWith(".unit")) {
                file = File(file.absolutePath + ".unit")
            }
            exportAsUnitBundle(bundle, file, onNotification)
        }
    } catch (e: Exception) {
        onNotification("error", "Failed to export: ${e.message}")
    }
}

/**
 * Execute artifact (supports Node.js, Python, Web artifacts)
 */
private suspend fun executeNodeJsArtifact(
    artifact: ArtifactAgent.Artifact,
    onConsoleLog: (String, String) -> Unit,
    onResult: (String, String?) -> Unit
) {
    try {
        // First, export artifact to a temporary .unit file
        val tempUnitFile = File.createTempFile("artifact-${artifact.identifier}", ".unit")
        tempUnitFile.deleteOnExit()

        val bundle = cc.unitmesh.agent.artifact.ArtifactBundle.fromArtifact(
            artifact = artifact,
            conversationHistory = emptyList(),
            modelInfo = null
        )

        val packer = cc.unitmesh.agent.artifact.ArtifactBundlePacker()
        when (val packResult = packer.pack(bundle, tempUnitFile.absolutePath)) {
            is cc.unitmesh.agent.artifact.PackResult.Success -> {
                // Execute the .unit file using the factory (supports all artifact types)
                when (val execResult = ArtifactExecutorFactory.executeArtifact(
                    unitFilePath = tempUnitFile.absolutePath,
                    onOutput = { line ->
                        onConsoleLog("info", line)
                    }
                )) {
                    is ExecutionResult.Success -> {
                        val output = buildString {
                            append(execResult.output)
                            execResult.serverUrl?.let { url ->
                                append("\n\n🌐 Server URL: $url")
                            }
                        }
                        onResult(output, null)
                    }
                    is ExecutionResult.Error -> {
                        onResult("", execResult.message)
                    }
                }
            }
            is cc.unitmesh.agent.artifact.PackResult.Error -> {
                onResult("", "Failed to create .unit file: ${packResult.message}")
            }
        }
    } catch (e: Exception) {
        onResult("", "Execution error: ${e.message}")
    }
}

/**
 * Node.js execution view - shows terminal output
 */
@Composable
private fun NodeJsExecutionView(
    artifact: ArtifactAgent.Artifact,
    isExecuting: Boolean,
    output: String,
    error: String?,
    onConsoleLog: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Node.js Application",
                style = MaterialTheme.typography.titleMedium
            )
            if (isExecuting) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Running...",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Terminal output
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        ) {
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Column {
                        if (output.isNotEmpty()) {
                            Text(
                                text = output,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else if (error != null) {
                            Text(
                                text = "Error: $error",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                text = "Click the play button to execute the Node.js application.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
