package cc.unitmesh.agent.runconfig

import kotlinx.datetime.Clock

actual suspend fun readProcessOutputPlatform(
    processHandle: Any?,
    onOutput: (String) -> Unit,
    timeoutMs: Long,
    onProcessStarted: (processId: Int?) -> Unit
): RunConfigResult {
    return RunConfigResult(
        success = false,
        error = "Native process execution not supported on JS platform"
    )
}

actual fun killProcessPlatform(processHandle: Any?) {
    // No-op on JS
}

actual fun isNativeProcess(handle: Any?): Boolean = false

actual fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
