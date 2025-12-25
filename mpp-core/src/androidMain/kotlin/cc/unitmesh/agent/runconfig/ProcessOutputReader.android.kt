package cc.unitmesh.agent.runconfig

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

actual suspend fun readProcessOutputPlatform(
    processHandle: Any?,
    onOutput: (String) -> Unit,
    timeoutMs: Long,
    onProcessStarted: (processId: Int?) -> Unit
): RunConfigResult = withContext(Dispatchers.IO) {
    val process = processHandle as? Process
        ?: return@withContext RunConfigResult(
            success = false,
            error = "Invalid process handle"
        )
    
    val startTime = System.currentTimeMillis()
    val outputBuilder = StringBuilder()
    var cancelled = false
    
    // Read stdout in a separate thread
    val stdoutReader = Thread {
        try {
            process.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(1024)
                while (!cancelled) {
                    val charsRead = reader.read(buffer)
                    if (charsRead == -1) break
                    val chunk = buffer.concatToString(0, charsRead)
                    outputBuilder.append(chunk)
                    onOutput(chunk)
                }
            }
        } catch (_: Exception) {
            // Stream closed, ignore
        }
    }.apply { start() }
    
    // Read stderr in a separate thread
    val stderrReader = Thread {
        try {
            process.errorStream.bufferedReader().use { reader ->
                val buffer = CharArray(1024)
                while (!cancelled) {
                    val charsRead = reader.read(buffer)
                    if (charsRead == -1) break
                    val chunk = buffer.concatToString(0, charsRead)
                    outputBuilder.append(chunk)
                    onOutput(chunk)
                }
            }
        } catch (_: Exception) {
            // Stream closed, ignore
        }
    }.apply { start() }
    
    try {
        // Wait for process with timeout
        val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        
        // Wait for readers to finish
        stdoutReader.join(2000)
        stderrReader.join(2000)
        
        if (!completed) {
            onOutput("\n[INFO] Process still running after ${timeoutMs / 1000}s timeout.\n")
            // Android API level check for pid() - use reflection or fallback
            val pid = try {
                process.javaClass.getMethod("pid").invoke(process) as? Long
            } catch (_: Exception) {
                null
            }
            onProcessStarted(pid?.toInt())
            return@withContext RunConfigResult(
                success = true,
                message = "Process started (may still be running)",
                pid = pid?.toInt()
            )
        }
        
        val exitCode = process.exitValue()
        val executionTime = System.currentTimeMillis() - startTime
        
        RunConfigResult(
            success = exitCode == 0,
            exitCode = exitCode,
            message = if (exitCode == 0) {
                "Command completed successfully (${executionTime}ms)"
            } else {
                "Command exited with code $exitCode"
            }
        )
    } catch (e: kotlinx.coroutines.CancellationException) {
        cancelled = true
        stdoutReader.interrupt()
        stderrReader.interrupt()
        throw e
    }
}

actual fun killProcessPlatform(processHandle: Any?) {
    val process = processHandle as? Process ?: return
    try {
        // Use exitValue to check if alive (throws if still running)
        try {
            process.exitValue()
        } catch (_: IllegalThreadStateException) {
            // Process is still running, destroy it
            process.destroyForcibly()
        }
    } catch (_: Exception) {
        // Ignore
    }
}

actual fun isNativeProcess(handle: Any?): Boolean = handle is Process

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
