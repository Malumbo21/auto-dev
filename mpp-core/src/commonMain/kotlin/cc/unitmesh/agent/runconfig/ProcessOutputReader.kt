package cc.unitmesh.agent.runconfig

/**
 * Platform-specific process output reader.
 * On JVM: Reads from java.lang.Process streams
 * On other platforms: Not supported (returns error result)
 */
expect suspend fun readProcessOutputPlatform(
    processHandle: Any?,
    onOutput: (String) -> Unit,
    timeoutMs: Long,
    onProcessStarted: (processId: Int?) -> Unit
): RunConfigResult

/**
 * Platform-specific process killer.
 * On JVM: Kills java.lang.Process
 * On other platforms: No-op
 */
expect fun killProcessPlatform(processHandle: Any?)

/**
 * Platform-specific check if process handle is a native process.
 * On JVM: Returns true if handle is java.lang.Process
 * On other platforms: Returns false
 */
expect fun isNativeProcess(handle: Any?): Boolean

/**
 * Get current time in milliseconds (platform-specific)
 */
expect fun currentTimeMillis(): Long
