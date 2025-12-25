package cc.unitmesh.agent.artifact.executor

import cc.unitmesh.agent.artifact.ArtifactType
import cc.unitmesh.agent.logging.AutoDevLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Executor for Node.js artifacts
 * 
 * Handles:
 * - npm install for dependencies
 * - node index.js execution
 * - Express.js and other Node.js applications
 */
class NodeJsArtifactExecutor : ArtifactExecutor {
    private val logger = AutoDevLogger

    override val supportedTypes: Set<ArtifactType> = setOf(ArtifactType.NODEJS, ArtifactType.REACT)

    override suspend fun validate(extractDir: File, bundleType: ArtifactType): ValidationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        val packageJsonFile = File(extractDir, "package.json")
        if (!packageJsonFile.exists()) {
            errors.add("package.json not found")
        }

        val mainFile = when (bundleType) {
            ArtifactType.NODEJS -> File(extractDir, "index.js")
            ArtifactType.REACT -> File(extractDir, "index.jsx")
            else -> null
        }

        if (mainFile == null) {
            errors.add("Unsupported bundle type: $bundleType")
        } else if (!mainFile.exists()) {
            errors.add("${mainFile.name} not found")
        } else {
            // Verify main file is actually code, not JSON
            val content = mainFile.readText()
            if (content.trim().startsWith("{") && content.contains("\"name\"")) {
                // Try to recover from context.json
                logger.warn("NodeJsArtifactExecutor") { "⚠️ ${mainFile.name} contains JSON (likely package.json). Attempting recovery from context..." }
                val recovered = tryRecoverCodeFromContext(extractDir, bundleType)
                if (recovered != null) {
                    logger.info("NodeJsArtifactExecutor") { "✅ Recovered code from context, fixing ${mainFile.name}..." }
                    mainFile.writeText(recovered)
                    // Re-validate after recovery
                    val newContent = mainFile.readText()
                    if (newContent.trim().startsWith("{") && newContent.contains("\"name\"")) {
                        errors.add("${mainFile.name} contains invalid content and recovery failed")
                    }
                } else {
                    errors.add("${mainFile.name} contains invalid content (appears to be package.json). Could not recover from context.")
                }
            }
        }

        if (errors.isEmpty()) {
            ValidationResult.Valid()
        } else {
            ValidationResult.Invalid(errors)
        }
    }

    /**
     * Try to recover JavaScript code from context.json conversation history
     * This handles cases where AI mistakenly put package.json as artifact content
     */
    private fun tryRecoverCodeFromContext(extractDir: File, bundleType: ArtifactType): String? {
        try {
            val contextFile = File(extractDir, ".artifact/context.json")
            if (!contextFile.exists()) {
                return null
            }

            val contextJson = contextFile.readText()
            // Parse JSON to extract conversation history
            val json = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
            val context = json.decodeFromString<cc.unitmesh.agent.artifact.ArtifactContext>(contextJson)

            // Look for the actual code in conversation history
            // Usually the assistant's response contains multiple artifacts, and the code is in the second one
            for (message in context.conversationHistory.reversed()) {
                if (message.role == "assistant") {
                    // Try to extract the actual code artifact (not package.json)
                    val codeArtifact = extractCodeArtifactFromMessage(message.content, bundleType)
                    if (codeArtifact != null) {
                        return codeArtifact
                    }
                }
            }

            return null
        } catch (e: Exception) {
            logger.warn("NodeJsArtifactExecutor") { "Failed to recover from context: ${e.message}" }
            return null
        }
    }

    /**
     * Extract code artifact from assistant message
     * Looks for the second or later artifact that contains actual code (not JSON)
     */
    private fun extractCodeArtifactFromMessage(message: String, bundleType: ArtifactType): String? {
        // Pattern to match <autodev-artifact ...>...</autodev-artifact>
        val artifactPattern = Regex(
            """<autodev-artifact\s+([^>]+)>([\s\S]*?)</autodev-artifact>""",
            RegexOption.MULTILINE
        )

        val artifacts = artifactPattern.findAll(message).toList()
        
        // Look for artifacts that are NOT JSON (skip package.json artifacts)
        for (match in artifacts) {
            val attributesStr = match.groupValues[1]
            val content = match.groupValues[2].trim()
            
            // Skip if it's clearly JSON (package.json)
            if (content.trim().startsWith("{") && content.contains("\"name\"") && content.contains("\"dependencies\"")) {
                continue
            }
            
            // Check if it's the right type
            val typeStr = extractAttribute(attributesStr, "type") ?: ""
            val expectedType = when (bundleType) {
                ArtifactType.NODEJS -> "application/autodev.artifacts.nodejs"
                ArtifactType.REACT -> "application/autodev.artifacts.react"
                else -> return null
            }
            
            if (typeStr == expectedType && !content.trim().startsWith("{")) {
                // This looks like actual code
                return content
            }
        }

        // Fallback: return the last artifact that's not JSON
        for (match in artifacts.reversed()) {
            val content = match.groupValues[2].trim()
            if (!content.trim().startsWith("{") || !content.contains("\"name\"")) {
                return content
            }
        }

        return null
    }

    private fun extractAttribute(attributesStr: String, name: String): String? {
        val pattern = Regex("""$name\s*=\s*["']([^"']+)["']""")
        return pattern.find(attributesStr)?.groupValues?.get(1)
    }

    override suspend fun execute(
        extractDir: File,
        bundleType: ArtifactType,
        onOutput: ((String) -> Unit)?
    ): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            logger.info("NodeJsArtifactExecutor") { "🚀 Executing Node.js artifact in: ${extractDir.absolutePath}" }

            // Validate first
            when (val validation = validate(extractDir, bundleType)) {
                is ValidationResult.Invalid -> {
                    return@withContext ExecutionResult.Error("Validation failed: ${validation.errors.joinToString(", ")}")
                }
                is ValidationResult.Valid -> {
                    logger.info("NodeJsArtifactExecutor") { "✅ Validation passed" }
                }
            }

            // Step 1: Check for dependencies and run npm install
            val packageJsonFile = File(extractDir, "package.json")
            val packageJsonContent = packageJsonFile.readText()
            val hasDependencies = packageJsonContent.contains("\"dependencies\"") ||
                    packageJsonContent.contains("\"devDependencies\"")

            if (hasDependencies) {
                logger.info("NodeJsArtifactExecutor") { "📦 Installing dependencies..." }
                onOutput?.invoke("Installing dependencies...\n")

                val installResult = executeCommand(
                    command = "npm install",
                    workingDirectory = extractDir.absolutePath,
                    onOutput = onOutput
                )

                if (installResult.exitCode != 0) {
                    logger.warn("NodeJsArtifactExecutor") { "⚠️ npm install failed with exit code ${installResult.exitCode}" }
                    onOutput?.invoke("Warning: npm install failed. Continuing anyway...\n")
                } else {
                    logger.info("NodeJsArtifactExecutor") { "✅ Dependencies installed" }
                    onOutput?.invoke("Dependencies installed successfully.\n")
                }
            } else {
                logger.info("NodeJsArtifactExecutor") { "ℹ️ No dependencies to install" }
                onOutput?.invoke("No dependencies to install.\n")
            }

            // Step 2: Execute the application
            val mainFile = when (bundleType) {
                ArtifactType.NODEJS -> "index.js"
                ArtifactType.REACT -> "index.jsx"
                else -> "index.js"
            }

            logger.info("NodeJsArtifactExecutor") { "▶️ Executing: node $mainFile" }
            onOutput?.invoke("Starting application...\n")
            onOutput?.invoke("=".repeat(50) + "\n")

            // Use ProcessManager for long-running processes (like Express.js servers)
            val (processId, initialOutput) = ProcessManager.startProcess(
                command = "node $mainFile",
                workingDirectory = extractDir.absolutePath,
                onOutput = onOutput
            )

            if (processId == -1L) {
                // Process exited immediately (one-shot script or error)
                ExecutionResult.Success(
                    output = initialOutput,
                    workingDirectory = extractDir.absolutePath
                )
            } else {
                // Long-running process (server)
                // Try to detect the server URL from output
                val serverUrl = detectServerUrl(initialOutput)
                
                onOutput?.invoke("\n✅ Server started (Process #$processId)\n")
                serverUrl?.let { url ->
                    onOutput?.invoke("🌐 Server URL: $url\n")
                }
                onOutput?.invoke("💡 Click 'Stop' to stop the server\n")

                ExecutionResult.Success(
                    output = initialOutput,
                    workingDirectory = extractDir.absolutePath,
                    serverUrl = serverUrl,
                    processId = processId
                )
            }
        } catch (e: Exception) {
            logger.error("NodeJsArtifactExecutor") { "❌ Execution failed: ${e.message}" }
            ExecutionResult.Error("Execution failed: ${e.message}", e)
        }
    }

    private suspend fun executeCommand(
        command: String,
        workingDirectory: String,
        onOutput: ((String) -> Unit)? = null
    ): CommandResult = withContext(Dispatchers.IO) {
        try {
            val processBuilder = ProcessBuilder()
                .command("sh", "-c", command)
                .directory(File(workingDirectory))
                .redirectErrorStream(true)

            val process = processBuilder.start()
            val outputBuilder = StringBuilder()

            coroutineScope {
                val outputJob = launch(Dispatchers.IO) {
                    process.inputStream.bufferedReader().use { reader ->
                        reader.lineSequence().forEach { line ->
                            outputBuilder.appendLine(line)
                            onOutput?.invoke("$line\n")
                        }
                    }
                }

                val exitCode = process.waitFor()
                outputJob.join()

                CommandResult(
                    exitCode = exitCode,
                    stdout = outputBuilder.toString(),
                    stderr = ""
                )
            }
        } catch (e: Exception) {
            CommandResult(
                exitCode = -1,
                stdout = "",
                stderr = "Error executing command: ${e.message}"
            )
        }
    }

    private data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )

    /**
     * Try to detect server URL from console output
     * Common patterns: "Server running on http://...", "listening on port ...", etc.
     */
    private fun detectServerUrl(output: String): String? {
        // Pattern 1: Direct URL mention
        val urlPattern = Regex("""https?://(?:localhost|127\.0\.0\.1|0\.0\.0\.0):\d+/?""")
        urlPattern.find(output)?.let { return it.value }

        // Pattern 2: "listening on port XXXX" or "running on port XXXX"
        val portPattern = Regex("""(?:listening|running|started)\s+(?:on\s+)?port\s+(\d+)""", RegexOption.IGNORE_CASE)
        portPattern.find(output)?.let { match ->
            val port = match.groupValues[1]
            return "http://localhost:$port"
        }

        // Pattern 3: ":PORT" at end of line (common in Express)
        val colonPortPattern = Regex(""":(\d{4,5})""")
        colonPortPattern.find(output)?.let { match ->
            val port = match.groupValues[1]
            return "http://localhost:$port"
        }

        return null
    }

    companion object {
        /**
         * Stop a running Node.js process
         */
        fun stopProcess(processId: Long): Boolean {
            return ProcessManager.stopProcess(processId)
        }

        /**
         * Check if a process is still running
         */
        fun isProcessRunning(processId: Long): Boolean {
            return ProcessManager.isRunning(processId)
        }
    }
}

