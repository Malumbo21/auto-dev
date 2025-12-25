package cc.unitmesh.agent.artifact

import cc.unitmesh.agent.logging.AutoDevLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Executor for running artifacts from .unit files
 * Supports Node.js applications with npm install and execution
 */
object ArtifactExecutor {
    private val logger = AutoDevLogger

    /**
     * Result of artifact execution
     */
    sealed class ExecutionResult {
        data class Success(val output: String, val workingDirectory: String) : ExecutionResult()
        data class Error(val message: String, val cause: Throwable? = null) : ExecutionResult()
    }

    /**
     * Execute a Node.js artifact from a .unit file
     * 
     * Steps:
     * 1. Extract .unit file to temporary directory
     * 2. Check for package.json
     * 3. Run npm install if dependencies exist
     * 4. Execute node index.js
     * 
     * @param unitFilePath Path to the .unit file
     * @param onOutput Callback for output lines (stdout/stderr)
     * @return ExecutionResult with output and working directory
     */
    suspend fun executeNodeJsArtifact(
        unitFilePath: String,
        onOutput: ((String) -> Unit)? = null
    ): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            logger.info("ArtifactExecutor") { "🚀 Executing Node.js artifact from: $unitFilePath" }

            // Step 1: Extract .unit file to temporary directory
            val tempDir = Files.createTempDirectory("autodev-artifact-${UUID.randomUUID()}")
            val extractDir = tempDir.toFile()

            logger.info("ArtifactExecutor") { "📦 Extracting to: ${extractDir.absolutePath}" }
            val packer = ArtifactBundlePacker()
            when (val extractResult = packer.extractToDirectory(unitFilePath, extractDir.absolutePath)) {
                is PackResult.Success -> {
                    logger.info("ArtifactExecutor") { "✅ Extracted successfully" }
                }
                is PackResult.Error -> {
                    return@withContext ExecutionResult.Error("Failed to extract bundle: ${extractResult.message}")
                }
            }

            // Step 2: Check for package.json
            val packageJsonFile = File(extractDir, "package.json")
            if (!packageJsonFile.exists()) {
                return@withContext ExecutionResult.Error("package.json not found in artifact")
            }

            // Step 3: Check for dependencies and run npm install
            val packageJsonContent = packageJsonFile.readText()
            val hasDependencies = packageJsonContent.contains("\"dependencies\"") ||
                    packageJsonContent.contains("\"devDependencies\"")

            if (hasDependencies) {
                logger.info("ArtifactExecutor") { "📦 Installing dependencies..." }
                onOutput?.invoke("Installing dependencies...\n")
                
                val installResult = executeCommand(
                    command = "npm install",
                    workingDirectory = extractDir.absolutePath,
                    onOutput = onOutput
                )

                if (installResult.exitCode != 0) {
                    logger.warn("ArtifactExecutor") { "⚠️ npm install failed with exit code ${installResult.exitCode}" }
                    onOutput?.invoke("Warning: npm install failed. Continuing anyway...\n")
                } else {
                    logger.info("ArtifactExecutor") { "✅ Dependencies installed" }
                    onOutput?.invoke("Dependencies installed successfully.\n")
                }
            } else {
                logger.info("ArtifactExecutor") { "ℹ️ No dependencies to install" }
                onOutput?.invoke("No dependencies to install.\n")
            }

            // Step 4: Execute node index.js
            val indexJsFile = File(extractDir, "index.js")
            if (!indexJsFile.exists()) {
                return@withContext ExecutionResult.Error("index.js not found in artifact")
            }

            logger.info("ArtifactExecutor") { "▶️ Executing: node index.js" }
            onOutput?.invoke("Starting application...\n")
            onOutput?.invoke("=".repeat(50) + "\n")

            val executeResult = executeCommand(
                command = "node index.js",
                workingDirectory = extractDir.absolutePath,
                onOutput = onOutput
            )

            val output = if (executeResult.exitCode == 0) {
                "Application executed successfully.\n${executeResult.stdout}"
            } else {
                "Application exited with code ${executeResult.exitCode}.\n${executeResult.stdout}\n${executeResult.stderr}"
            }

            ExecutionResult.Success(
                output = output,
                workingDirectory = extractDir.absolutePath
            )
        } catch (e: Exception) {
            logger.error("ArtifactExecutor") { "❌ Execution failed: ${e.message}" }
            ExecutionResult.Error("Execution failed: ${e.message}", e)
        }
    }

    /**
     * Execute a shell command and capture output
     * Note: For long-running processes (like Express.js servers), this will run until the process exits.
     * For interactive execution, consider using a different approach with process management.
     */
    private suspend fun executeCommand(
        command: String,
        workingDirectory: String,
        onOutput: ((String) -> Unit)? = null
    ): CommandResult = withContext(Dispatchers.IO) {
        try {
            val processBuilder = ProcessBuilder()
                .command("sh", "-c", command)
                .directory(File(workingDirectory))
                .redirectErrorStream(true) // Merge stderr into stdout

            val process = processBuilder.start()
            val outputBuilder = StringBuilder()

            // Read output asynchronously using coroutineScope
            coroutineScope {
                val outputJob = launch(Dispatchers.IO) {
                    process.inputStream.bufferedReader().use { reader ->
                        reader.lineSequence().forEach { line ->
                            outputBuilder.appendLine(line)
                            onOutput?.invoke("$line\n")
                        }
                    }
                }

                // Wait for process to complete
                val exitCode = process.waitFor()
                
                // Wait for output reading to complete
                outputJob.join()

                CommandResult(
                    exitCode = exitCode,
                    stdout = outputBuilder.toString(),
                    stderr = "" // Already merged into stdout
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
}

