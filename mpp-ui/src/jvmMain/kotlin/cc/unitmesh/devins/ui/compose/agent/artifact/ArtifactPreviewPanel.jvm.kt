package cc.unitmesh.devins.ui.compose.agent.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import cc.unitmesh.agent.ArtifactAgent
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandler
import javax.swing.JPanel

/**
 * JVM implementation of ArtifactPreviewPanel using KCEF (Kotlin CEF)
 */
@Composable
actual fun ArtifactPreviewPanel(
    artifact: ArtifactAgent.Artifact,
    onConsoleLog: (String, String) -> Unit,
    modifier: Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    var kcefInitialized by remember { mutableStateOf(false) }
    var browser by remember { mutableStateOf<KCEFBrowser?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Initialize KCEF
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                KCEF.init(
                    builder = {
                        installDir(java.io.File(System.getProperty("user.home"), ".autodev/kcef"))
                        progress {
                            onInitialized {
                                kcefInitialized = true
                            }
                        }
                        settings {
                            cachePath = java.io.File(System.getProperty("user.home"), ".autodev/kcef-cache").absolutePath
                        }
                    },
                    onError = { e ->
                        error = e?.message ?: "Failed to initialize KCEF"
                    },
                    onRestartRequired = {
                        // Handle restart if needed
                    }
                )
            } catch (e: Exception) {
                error = "KCEF init failed: ${e.message}"
            }
        }
    }

    // Create browser when KCEF is ready and artifact changes
    LaunchedEffect(kcefInitialized, artifact.content) {
        if (kcefInitialized) {
            withContext(Dispatchers.IO) {
                try {
                    browser?.dispose()

                    val newBrowser = KCEF.newClientOrNullBlocking()?.let { client ->
                        // Add console message handler
                        setupConsoleHandler(client, onConsoleLog)

                        KCEFBrowser(
                            client = client,
                            url = "about:blank",
                            useOSR = false,
                            transparent = false
                        )
                    }

                    browser = newBrowser
                    isLoading = false

                    // Load HTML content
                    newBrowser?.loadHtml(artifact.content)

                } catch (e: Exception) {
                    error = "Browser creation failed: ${e.message}"
                    isLoading = false
                }
            }
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            browser?.dispose()
        }
    }

    Box(
        modifier = modifier
            .background(AutoDevColors.Dark.background)
    ) {
        when {
            error != null -> {
                // Error state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Preview Error",
                        style = MaterialTheme.typography.titleMedium,
                        color = AutoDevColors.Dark.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error ?: "Unknown error",
                        style = MaterialTheme.typography.bodySmall,
                        color = AutoDevColors.Dark.textSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // Fallback: show raw HTML
                    HtmlSourceView(artifact.content)
                }
            }

            isLoading || !kcefInitialized -> {
                // Loading state
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (kcefInitialized) "Loading preview..." else "Initializing browser...",
                        style = MaterialTheme.typography.bodySmall,
                        color = AutoDevColors.Dark.textSecondary
                    )
                }
            }

            browser != null -> {
                // WebView
                SwingPanel(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        val panel = JPanel().apply {
                            layout = java.awt.BorderLayout()
                            add(browser!!.getComponent(), java.awt.BorderLayout.CENTER)
                        }
                        panel
                    }
                )
            }
        }
    }
}

/**
 * Setup console message handler to capture console.log output
 */
private fun setupConsoleHandler(
    client: CefClient,
    onConsoleLog: (String, String) -> Unit
) {
    client.addDisplayHandler(object : CefDisplayHandler {
        override fun onAddressChange(browser: CefBrowser?, frame: CefFrame?, url: String?) {}
        override fun onTitleChange(browser: CefBrowser?, title: String?) {}
        override fun onFullscreenModeChange(browser: CefBrowser?, fullscreen: Boolean) {}
        override fun onTooltip(browser: CefBrowser?, text: String?): Boolean = false
        override fun onStatusMessage(browser: CefBrowser?, value: String?) {}

        override fun onConsoleMessage(
            browser: CefBrowser?,
            level: org.cef.CefSettings.LogSeverity?,
            message: String?,
            source: String?,
            line: Int
        ): Boolean {
            val logLevel = when (level) {
                org.cef.CefSettings.LogSeverity.LOGSEVERITY_ERROR,
                org.cef.CefSettings.LogSeverity.LOGSEVERITY_FATAL -> "error"
                org.cef.CefSettings.LogSeverity.LOGSEVERITY_WARNING -> "warn"
                org.cef.CefSettings.LogSeverity.LOGSEVERITY_INFO -> "info"
                else -> "log"
            }
            onConsoleLog(logLevel, message ?: "")
            return false
        }

        override fun onCursorChange(browser: CefBrowser?, cursorType: Int): Boolean = false
    })
}

/**
 * Fallback HTML source view when WebView is not available
 */
@Composable
private fun HtmlSourceView(html: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .background(AutoDevColors.Dark.codeBackground)
            .padding(12.dp)
    ) {
        Text(
            text = html.take(2000) + if (html.length > 2000) "\n..." else "",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            color = AutoDevColors.Dark.textPrimary
        )
    }
}

