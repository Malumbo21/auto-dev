package cc.unitmesh.devins.ui.desktop

import cc.unitmesh.agent.artifact.ArtifactBundle
import cc.unitmesh.agent.artifact.ArtifactBundlePacker
import cc.unitmesh.agent.artifact.UnpackResult
import cc.unitmesh.agent.logging.AutoDevLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Handles .unit file associations and loading
 *
 * When the application is launched with a .unit file path (e.g., double-click),
 * this handler will:
 * 1. Parse the command line arguments for .unit files
 * 2. Load the bundle and expose it for the UI to consume
 * 3. Trigger navigation to Artifact mode
 */
object UnitFileHandler {
    private val logger = AutoDevLogger

    private val _pendingBundle = MutableStateFlow<ArtifactBundle?>(null)
    val pendingBundle: StateFlow<ArtifactBundle?> = _pendingBundle.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    /**
     * Check if args contain a .unit file path
     */
    fun hasUnitFile(args: Array<String>): Boolean {
        return args.any { it.endsWith(ArtifactBundle.BUNDLE_EXTENSION) && File(it).exists() }
    }

    /**
     * Extract .unit file path from args
     */
    fun getUnitFilePath(args: Array<String>): String? {
        return args.firstOrNull { it.endsWith(ArtifactBundle.BUNDLE_EXTENSION) && File(it).exists() }
    }

    /**
     * Process command line arguments for .unit files
     */
    suspend fun processArgs(args: Array<String>): Boolean {
        val unitFilePath = getUnitFilePath(args) ?: return false

        logger.info("UnitFileHandler") { "📦 Opening .unit file: $unitFilePath" }
        return loadUnitFile(unitFilePath)
    }

    /**
     * Load a .unit bundle file
     */
    suspend fun loadUnitFile(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) {
            _loadError.value = "File not found: $path"
            logger.error("UnitFileHandler") { "❌ File not found: $path" }
            return false
        }

        if (!file.name.endsWith(ArtifactBundle.BUNDLE_EXTENSION)) {
            _loadError.value = "Not a .unit file: ${file.name}"
            logger.error("UnitFileHandler") { "❌ Not a .unit file: ${file.name}" }
            return false
        }

        val packer = ArtifactBundlePacker()
        return when (val result = packer.unpack(path)) {
            is UnpackResult.Success -> {
                _pendingBundle.value = result.bundle
                _loadError.value = null
                logger.info("UnitFileHandler") { "✅ Loaded bundle: ${result.bundle.name}" }
                true
            }
            is UnpackResult.Error -> {
                _loadError.value = result.message
                _pendingBundle.value = null
                logger.error("UnitFileHandler") { "❌ Failed to load bundle: ${result.message}" }
                false
            }
        }
    }

    /**
     * Clear the pending bundle (after it's been consumed by UI)
     */
    fun clearPendingBundle() {
        _pendingBundle.value = null
        _loadError.value = null
    }

    /**
     * Get the pending bundle and clear it
     */
    fun consumePendingBundle(): ArtifactBundle? {
        val bundle = _pendingBundle.value
        _pendingBundle.value = null
        return bundle
    }
}

