package cc.unitmesh.devins.ui.kcef

import cc.unitmesh.config.ConfigManager
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * KCEF (Kotlin Chromium Embedded Framework) Manager
 *
 * Manages the initialization and lifecycle of KCEF for WebView support on Desktop.
 * KCEF is installed to ~/.autodev/kcef-bundle for easy manual download/replacement.
 */
object KcefManager {
    private val _initState = MutableStateFlow<KcefInitState>(KcefInitState.Idle)
    val initState: StateFlow<KcefInitState> = _initState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    /**
     * Initialize KCEF with progress reporting
     *
     * @param onError Callback when initialization fails
     * @param onRestartRequired Callback when app restart is required
     */
    suspend fun initialize(
        onError: ((Throwable) -> Unit)? = null,
        onRestartRequired: (() -> Unit)? = null
    ) {
        if (_initState.value is KcefInitState.Initialized) {
            println("✅ KCEF already initialized")
            return
        }

        if (_initState.value is KcefInitState.Initializing) {
            println("⏳ KCEF initialization already in progress")
            return
        }

        _initState.value = KcefInitState.Initializing

        withContext(Dispatchers.IO) {
            try {
                val installDir = File(ConfigManager.getKcefInstallDir())

                // Create install directory if it doesn't exist
                if (!installDir.exists()) {
                    installDir.mkdirs()
                    println("✅ Created KCEF install directory")
                }

                // Initialize KCEF with actual API
                KCEF.init(builder = {
                    // Install to ~/.autodev/kcef-bundle
                    installDir(installDir)

                    // Progress tracking
                    progress {
                        onDownloading {
                            val progress = max(it, 0f)
                            _downloadProgress.value = progress
                        }
                        onInitialized {
                            _downloadProgress.value = 100f
                            _initState.value = KcefInitState.Initialized
                            // Invalidate cache since KCEF is now installed
                            invalidateInstallCache()
                        }
                    }

                    // Settings
                    settings {
                        cachePath = File(installDir, "cache").absolutePath
                    }
                }, onError = { error ->
                    val exception = error ?: Exception("Unknown KCEF error")
                    _initState.value = KcefInitState.Error(exception)
                    onError?.invoke(exception)
                }, onRestartRequired = {
                    _initState.value = KcefInitState.RestartRequired
                    onRestartRequired?.invoke()
                })

            } catch (e: Exception) {
                _initState.value = KcefInitState.Error(e)
                e.printStackTrace()
                onError?.invoke(e)
            }
        }
    }

    /**
     * Dispose KCEF resources
     * Should be called when app is closing
     */
    suspend fun dispose() {
        withContext(Dispatchers.IO) {
            try {
                KCEF.disposeBlocking()
                _initState.value = KcefInitState.Idle
                _downloadProgress.value = 0f
            } catch (e: Exception) {
                println("⚠️ Error disposing KCEF: ${e.message}")
            }
        }
    }

    // Cached installation status to avoid repeated file system checks and logging
    private var cachedInstallStatus: Boolean? = null

    /**
     * Check if KCEF is already installed
     * Results are cached to avoid repeated file system access and logging
     */
    fun isInstalled(): Boolean {
        // Return cached result if available
        cachedInstallStatus?.let { return it }

        val installDir = File(ConfigManager.getKcefInstallDir())
        val exists = installDir.exists()
        val files = installDir.listFiles()
        val fileCount = files?.size ?: 0

        println("📁 KCEF install directory: ${installDir.absolutePath}")
        println("   - Directory exists: $exists")
        println("   - File count: $fileCount")

        if (files != null && files.isNotEmpty()) {
            println("   - Files: ${files.take(5).map { it.name }.joinToString(", ")}${if (fileCount > 5) "..." else ""}")
        }

        // Check if KCEF binary exists (look for typical KCEF files)
        val hasContent = exists && fileCount > 0

        if (hasContent) {
            println("✅ KCEF appears to be installed")
        } else {
            println("❌ KCEF not installed, will trigger download")
        }

        // Cache the result
        cachedInstallStatus = hasContent
        return hasContent
    }

    /**
     * Invalidate the cached installation status
     * Call this after KCEF installation/uninstallation
     */
    fun invalidateInstallCache() {
        cachedInstallStatus = null
    }
}

/**
 * KCEF initialization state
 */
sealed class KcefInitState {
    data object Idle : KcefInitState()
    data object Initializing : KcefInitState()
    data object Initialized : KcefInitState()
    data object RestartRequired : KcefInitState()
    data class Error(val exception: Throwable) : KcefInitState()
}

