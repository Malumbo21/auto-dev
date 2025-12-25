package cc.unitmesh.agent.artifact.executor

import cc.unitmesh.agent.logging.AutoDevLogger
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages long-running processes for artifact execution.
 * Allows starting, stopping, and monitoring processes like Express.js servers.
 */
object ProcessManager {
    private val logger = AutoDevLogger
    private val runningProcesses = ConcurrentHashMap<Long, RunningProcess>()
    private val idGenerator = AtomicLong(0)

    /**
     * A running process with its metadata
     */
    data class RunningProcess(
        val id: Long,
        val process: Process,
        val command: String,
        val workingDirectory: String,
        val outputJob: Job?,
        val startTime: Long = System.currentTimeMillis()
    ) {
        val isAlive: Boolean get() = process.isAlive
        
        fun stop() {
            try {
                // Try graceful shutdown first
                process.destroy()
                
                // Wait a bit for graceful shutdown
                Thread.sleep(500)
                
                // Force kill if still running
                if (process.isAlive) {
                    process.destroyForcibly()
                }
                
                outputJob?.cancel()
            } catch (e: Exception) {
                // Ignore exceptions during shutdown
            }
        }
    }

    /**
     * Start a long-running process (like Express.js server)
     * 
     * @param command The command to execute
     * @param workingDirectory The working directory
     * @param onOutput Callback for output lines
     * @return The process ID and initial output
     */
    suspend fun startProcess(
        command: String,
        workingDirectory: String,
        onOutput: ((String) -> Unit)? = null
    ): Pair<Long, String> = withContext(Dispatchers.IO) {
        val processId = idGenerator.incrementAndGet()
        
        logger.info("ProcessManager") { "🚀 Starting process #$processId: $command in $workingDirectory" }
        
        val processBuilder = ProcessBuilder()
            .command("sh", "-c", command)
            .directory(File(workingDirectory))
            .redirectErrorStream(true)
        
        val process = processBuilder.start()
        val outputBuilder = StringBuilder()
        
        // Start output reading job
        val outputJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                process.inputStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        outputBuilder.appendLine(line)
                        onOutput?.invoke("$line\n")
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    logger.warn("ProcessManager") { "Output reading error: ${e.message}" }
                }
            }
        }
        
        val runningProcess = RunningProcess(
            id = processId,
            process = process,
            command = command,
            workingDirectory = workingDirectory,
            outputJob = outputJob
        )
        
        runningProcesses[processId] = runningProcess
        
        // Wait a bit to capture initial output (server startup messages)
        delay(1000)
        
        // Check if process is still alive (server is running)
        if (!process.isAlive) {
            val exitCode = process.exitValue()
            runningProcesses.remove(processId)
            outputJob.cancel()
            
            // Return output even if process exited (for one-shot scripts)
            return@withContext Pair(-1L, "Process exited with code $exitCode.\n${outputBuilder}")
        }
        
        logger.info("ProcessManager") { "✅ Process #$processId started successfully" }
        Pair(processId, outputBuilder.toString())
    }

    /**
     * Stop a running process
     * 
     * @param processId The process ID to stop
     * @return true if stopped successfully
     */
    fun stopProcess(processId: Long): Boolean {
        val runningProcess = runningProcesses.remove(processId) ?: return false
        
        logger.info("ProcessManager") { "🛑 Stopping process #$processId" }
        runningProcess.stop()
        
        return true
    }

    /**
     * Check if a process is still running
     */
    fun isRunning(processId: Long): Boolean {
        return runningProcesses[processId]?.isAlive == true
    }

    /**
     * Get all running processes
     */
    fun getRunningProcesses(): List<RunningProcess> {
        return runningProcesses.values.filter { it.isAlive }.toList()
    }

    /**
     * Stop all running processes
     */
    fun stopAll() {
        logger.info("ProcessManager") { "🛑 Stopping all ${runningProcesses.size} processes" }
        runningProcesses.values.forEach { it.stop() }
        runningProcesses.clear()
    }
}

