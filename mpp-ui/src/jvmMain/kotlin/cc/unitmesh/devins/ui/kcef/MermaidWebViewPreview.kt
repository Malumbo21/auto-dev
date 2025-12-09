package cc.unitmesh.devins.ui.kcef

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.viewer.web.MermaidRenderer
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Mermaid Renderer",
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        var restartRequired by remember { mutableStateOf(false) }
        var downloading by remember { mutableStateOf(0F) }
        var initialized by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        // Initialize KCEF
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val installDir = File(ConfigManager.getKcefInstallDir())
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
                    error = "KCEF initialization failed: ${it?.message}"
                }, onRestartRequired = {
                    restartRequired = true
                })
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                KCEF.disposeBlocking()
            }
        }

        MaterialTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                when {
                    error != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Error: $error")
                            }
                        }
                    }

                    restartRequired -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Restart required. Please restart the application.")
                        }
                    }

                    !initialized -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Downloading KCEF: ${downloading.toInt()}%")
                                Text("(This may take a while on first run)")
                            }
                        }
                    }

                    else -> {
                        MainMermaidContent()
                    }
                }
            }
        }
    }
}

@Composable
fun MainMermaidContent() {
    val systemIsDark = isSystemInDarkTheme()
    var isDarkTheme by remember { mutableStateOf(systemIsDark) }
    var showFullscreenViewer by remember { mutableStateOf(false) }

    val examples = """
        graph TD
            A[Start] --> B{Is it working?}
            B -->|Yes| C[Great!]
            B -->|No| D[Debug]
            C --> E[End]
            D --> B
    """.trimIndent()

    val backgroundColor = if (isDarkTheme) {
        androidx.compose.ui.graphics.Color(0xFF171717)
    } else {
        androidx.compose.ui.graphics.Color(0xFFfafafa)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            // Open in Viewer button
            Button(onClick = { showFullscreenViewer = true }) {
                Text("Open in Viewer")
            }

            // Theme toggle button
            Button(onClick = { isDarkTheme = !isDarkTheme }) {
                Text(if (isDarkTheme) "Switch to Light" else "Switch to Dark")
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MermaidRenderer(
                mermaidCode = examples,
                isDarkTheme = isDarkTheme,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Fullscreen Viewer Dialog
    if (showFullscreenViewer) {
        cc.unitmesh.viewer.web.MermaidFullscreenDialog(
            mermaidCode = examples,
            isDarkTheme = isDarkTheme,
            backgroundColor = backgroundColor,
            onDismiss = { showFullscreenViewer = false }
        )
    }
}


