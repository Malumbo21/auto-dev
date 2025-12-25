package cc.unitmesh.agent.artifact.executor

import cc.unitmesh.agent.artifact.ArtifactType
import java.io.File

/**
 * Base interface for artifact executors
 * 
 * Each executor handles a specific artifact type:
 * - WebArtifactExecutor: HTML/JS artifacts (preview in browser or local server)
 * - NodeJsArtifactExecutor: Node.js applications (npm install + node)
 * - PythonArtifactExecutor: Python scripts (pip install + python)
 */
interface ArtifactExecutor {
    /**
     * Supported artifact types
     */
    val supportedTypes: Set<ArtifactType>

    /**
     * Execute an artifact from an extracted directory
     * 
     * @param extractDir The directory where the .unit file was extracted
     * @param bundleType The type of artifact bundle
     * @param onOutput Callback for output lines (stdout/stderr)
     * @return ExecutionResult with output and metadata
     */
    suspend fun execute(
        extractDir: File,
        bundleType: ArtifactType,
        onOutput: ((String) -> Unit)?
    ): ExecutionResult

    /**
     * Validate that the extracted directory contains required files
     * 
     * @param extractDir The extracted directory
     * @param bundleType The type of artifact bundle
     * @return ValidationResult indicating if the artifact is valid
     */
    suspend fun validate(extractDir: File, bundleType: ArtifactType): ValidationResult
}

/**
 * Result of artifact execution
 */
sealed class ExecutionResult {
    /**
     * Successful execution
     * @param output Console output from the execution
     * @param workingDirectory The directory where the artifact was executed
     * @param serverUrl Optional URL if a server was started (for web artifacts)
     * @param processId Optional process ID for long-running processes
     */
    data class Success(
        val output: String,
        val workingDirectory: String,
        val serverUrl: String? = null,
        val processId: Long? = null
    ) : ExecutionResult()

    /**
     * Execution failed
     * @param message Error message
     * @param cause Optional exception that caused the failure
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : ExecutionResult()
}

/**
 * Result of artifact validation
 */
sealed class ValidationResult {
    data class Valid(val message: String = "Artifact is valid") : ValidationResult()
    data class Invalid(val errors: List<String>) : ValidationResult()
}

