package cc.unitmesh.agent.artifact

import cc.unitmesh.agent.artifact.executor.ArtifactExecutorFactory
import cc.unitmesh.agent.artifact.executor.ExecutionResult as NewExecutionResult

/**
 * Legacy ArtifactExecutor - now delegates to ArtifactExecutorFactory
 * 
 * This maintains backward compatibility while using the new executor architecture.
 * 
 * @deprecated Use ArtifactExecutorFactory.executeArtifact() directly
 */
@Deprecated("Use ArtifactExecutorFactory.executeArtifact() instead", ReplaceWith("ArtifactExecutorFactory.executeArtifact(unitFilePath, onOutput)"))
object ArtifactExecutor {
    /**
     * Result of artifact execution
     * @deprecated Use executor.ExecutionResult instead
     */
    @Deprecated("Use executor.ExecutionResult instead")
    sealed class ExecutionResult {
        @Deprecated("Use executor.ExecutionResult.Success instead")
        data class Success(val output: String, val workingDirectory: String) : ExecutionResult()
        @Deprecated("Use executor.ExecutionResult.Error instead")
        data class Error(val message: String, val cause: Throwable? = null) : ExecutionResult()
    }

    /**
     * Execute a Node.js artifact from a .unit file
     * 
     * @deprecated Use ArtifactExecutorFactory.executeArtifact() instead
     */
    @Deprecated("Use ArtifactExecutorFactory.executeArtifact() instead")
    suspend fun executeNodeJsArtifact(
        unitFilePath: String,
        onOutput: ((String) -> Unit)? = null
    ): ExecutionResult {
        // Delegate to new factory-based executor
        val result = ArtifactExecutorFactory.executeArtifact(unitFilePath, onOutput)
        
        // Convert new ExecutionResult to legacy format
        return when (result) {
            is NewExecutionResult.Success -> {
                LegacyExecutionResult.Success(
                    output = result.output,
                    workingDirectory = result.workingDirectory
                )
            }
            is NewExecutionResult.Error -> {
                LegacyExecutionResult.Error(
                    message = result.message,
                    cause = result.cause
                )
            }
        }
    }

    // Legacy result types for backward compatibility
    private sealed class LegacyExecutionResult : ExecutionResult() {
        data class Success(val output: String, val workingDirectory: String) : LegacyExecutionResult()
        data class Error(val message: String, val cause: Throwable? = null) : LegacyExecutionResult()
    }
}

