package cc.unitmesh.agent.artifact.executor

import cc.unitmesh.agent.artifact.ArtifactType
import cc.unitmesh.agent.logging.AutoDevLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ServerSocket

/**
 * Executor for Web artifacts (HTML/JS)
 * 
 * Handles:
 * - Starting a local HTTP server for HTML artifacts
 * - Opening in browser
 * - Serving static files
 */
class WebArtifactExecutor : ArtifactExecutor {
    private val logger = AutoDevLogger

    override val supportedTypes: Set<ArtifactType> = setOf(ArtifactType.HTML, ArtifactType.SVG)

    override suspend fun validate(extractDir: File, bundleType: ArtifactType): ValidationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        val mainFile = when (bundleType) {
            ArtifactType.HTML -> File(extractDir, "index.html")
            ArtifactType.SVG -> File(extractDir, "index.svg")
            else -> null
        }

        if (mainFile == null) {
            errors.add("Unsupported bundle type: $bundleType")
        } else if (!mainFile.exists()) {
            errors.add("${mainFile.name} not found")
        }

        if (errors.isEmpty()) {
            ValidationResult.Valid()
        } else {
            ValidationResult.Invalid(errors)
        }
    }

    override suspend fun execute(
        extractDir: File,
        bundleType: ArtifactType,
        onOutput: ((String) -> Unit)?
    ): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            logger.info("WebArtifactExecutor") { "🚀 Starting web server for artifact in: ${extractDir.absolutePath}" }

            // Validate first
            when (val validation = validate(extractDir, bundleType)) {
                is ValidationResult.Invalid -> {
                    return@withContext ExecutionResult.Error("Validation failed: ${validation.errors.joinToString(", ")}")
                }
                is ValidationResult.Valid -> {
                    logger.info("WebArtifactExecutor") { "✅ Validation passed" }
                }
            }

            // Find an available port
            val port = findAvailablePort()
            val serverUrl = "http://localhost:$port"

            logger.info("WebArtifactExecutor") { "🌐 Starting HTTP server on port $port" }
            onOutput?.invoke("Starting web server on $serverUrl...\n")

            // Start a simple HTTP server
            val serverProcess = startHttpServer(extractDir, port, onOutput)

            // Give server time to start
            delay(1000)

            onOutput?.invoke("Web server started successfully.\n")
            onOutput?.invoke("Open in browser: $serverUrl\n")
            onOutput?.invoke("Press Ctrl+C to stop the server.\n")

            ExecutionResult.Success(
                output = "Web server running on $serverUrl\nOpen in browser: $serverUrl",
                workingDirectory = extractDir.absolutePath,
                serverUrl = serverUrl,
                processId = serverProcess?.pid()
            )
        } catch (e: Exception) {
            logger.error("WebArtifactExecutor") { "❌ Execution failed: ${e.message}" }
            ExecutionResult.Error("Execution failed: ${e.message}", e)
        }
    }

    /**
     * Find an available port starting from 8000
     */
    private fun findAvailablePort(startPort: Int = 8000): Int {
        for (port in startPort..9000) {
            try {
                ServerSocket(port).use { socket ->
                    return socket.localPort
                }
            } catch (e: Exception) {
                // Port is in use, try next
            }
        }
        throw IllegalStateException("No available port found in range $startPort-9000")
    }

    /**
     * Start a simple HTTP server using Python's http.server or Node.js http-server
     */
    private suspend fun startHttpServer(
        directory: File,
        port: Int,
        onOutput: ((String) -> Unit)? = null
    ): Process? = withContext(Dispatchers.IO) {
        // Try Python's http.server first (usually available)
        try {
            val process = ProcessBuilder()
                .command("python3", "-m", "http.server", port.toString())
                .directory(directory)
                .redirectErrorStream(true)
                .start()

            // Read output in background
            coroutineScope {
                launch(Dispatchers.IO) {
                    process.inputStream.bufferedReader().use { reader ->
                        reader.lineSequence().forEach { line ->
                            onOutput?.invoke("$line\n")
                        }
                    }
                }
            }

            return@withContext process
        } catch (e: Exception) {
            logger.warn("WebArtifactExecutor") { "Failed to start Python HTTP server: ${e.message}" }
        }

        // Fallback: Try Node.js http-server if available
        try {
            val process = ProcessBuilder()
                .command("npx", "-y", "http-server", directory.absolutePath, "-p", port.toString(), "--silent")
                .redirectErrorStream(true)
                .start()

            coroutineScope {
                launch(Dispatchers.IO) {
                    process.inputStream.bufferedReader().use { reader ->
                        reader.lineSequence().forEach { line ->
                            onOutput?.invoke("$line\n")
                        }
                    }
                }
            }

            return@withContext process
        } catch (e: Exception) {
            logger.warn("WebArtifactExecutor") { "Failed to start Node.js http-server: ${e.message}" }
        }

        null
    }
}

