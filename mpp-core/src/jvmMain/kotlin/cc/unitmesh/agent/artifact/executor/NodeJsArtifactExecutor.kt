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
                errors.add("${mainFile.name} contains invalid content (appears to be package.json)")
            }
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

            val executeResult = executeCommand(
                command = "node $mainFile",
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
}

