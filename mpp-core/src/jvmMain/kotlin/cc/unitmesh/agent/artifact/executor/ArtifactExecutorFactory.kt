package cc.unitmesh.agent.artifact.executor

import cc.unitmesh.agent.artifact.ArtifactType
import cc.unitmesh.agent.logging.AutoDevLogger
import java.io.File
import java.nio.file.Files
import java.util.UUID

/**
 * Factory for creating appropriate artifact executors
 * 
 * Provides a unified interface for executing different types of artifacts:
 * - Web artifacts (HTML/SVG) -> WebArtifactExecutor
 * - Node.js artifacts -> NodeJsArtifactExecutor
 * - Python artifacts -> PythonArtifactExecutor
 */
object ArtifactExecutorFactory {
    private val logger = AutoDevLogger

    // Available executors
    private val executors = listOf(
        WebArtifactExecutor(),
        NodeJsArtifactExecutor(),
        PythonArtifactExecutor()
    )

    /**
     * Get executor for a specific artifact type
     */
    fun getExecutor(artifactType: ArtifactType): ArtifactExecutor? {
        return executors.firstOrNull { artifactType in it.supportedTypes }
    }

    /**
     * Execute an artifact from a .unit file
     * 
     * This is the main entry point for executing artifacts.
     * It handles:
     * 1. Extracting the .unit file
     * 2. Determining the artifact type
     * 3. Finding the appropriate executor
     * 4. Executing the artifact
     * 
     * @param unitFilePath Path to the .unit file
     * @param onOutput Callback for output lines
     * @return ExecutionResult
     */
    suspend fun executeArtifact(
        unitFilePath: String,
        onOutput: ((String) -> Unit)? = null
    ): ExecutionResult {
        return try {
            logger.info("ArtifactExecutorFactory") { "🚀 Executing artifact from: $unitFilePath" }

            // Step 1: Extract .unit file
            val tempDir = Files.createTempDirectory("autodev-artifact-${UUID.randomUUID()}")
            val extractDir = tempDir.toFile()

            logger.info("ArtifactExecutorFactory") { "📦 Extracting to: ${extractDir.absolutePath}" }
            val packer = cc.unitmesh.agent.artifact.ArtifactBundlePacker()
            when (val extractResult = packer.extractToDirectory(unitFilePath, extractDir.absolutePath)) {
                is cc.unitmesh.agent.artifact.PackResult.Success -> {
                    logger.info("ArtifactExecutorFactory") { "✅ Extracted successfully" }
                }
                is cc.unitmesh.agent.artifact.PackResult.Error -> {
                    return ExecutionResult.Error("Failed to extract bundle: ${extractResult.message}")
                }
            }

            // Step 2: Determine artifact type from ARTIFACT.md
            val artifactType = determineArtifactType(extractDir)
            if (artifactType == null) {
                return ExecutionResult.Error("Could not determine artifact type from bundle")
            }

            logger.info("ArtifactExecutorFactory") { "📋 Artifact type: $artifactType" }

            // Step 3: Get appropriate executor
            val executor = getExecutor(artifactType)
            if (executor == null) {
                return ExecutionResult.Error("No executor available for artifact type: $artifactType")
            }

            logger.info("ArtifactExecutorFactory") { "🔧 Using executor: ${executor::class.simpleName}" }

            // Step 4: Execute
            executor.execute(extractDir, artifactType, onOutput)
        } catch (e: Exception) {
            logger.error("ArtifactExecutorFactory") { "❌ Execution failed: ${e.message}" }
            ExecutionResult.Error("Execution failed: ${e.message}", e)
        }
    }

    /**
     * Determine artifact type from extracted directory
     */
    private suspend fun determineArtifactType(extractDir: File): ArtifactType? {
        val artifactMd = File(extractDir, "ARTIFACT.md")
        if (!artifactMd.exists()) {
            return null
        }

        val content = artifactMd.readText()
        val typePattern = Regex("""type:\s*(\w+)""", RegexOption.IGNORE_CASE)
        val match = typePattern.find(content) ?: return null

        val typeStr = match.groupValues[1].lowercase()
        return ArtifactType.entries.find { it.name.lowercase() == typeStr }
    }
}

