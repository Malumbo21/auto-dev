package cc.unitmesh.agent.acp

import cc.unitmesh.config.AcpAgentConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger("AcpAgentProcessManager")

/**
 * Manages the lifecycle of ACP agent processes.
 *
 * Inspired by JetBrains' AcpProcessHandlerService, this manager provides:
 * - Process reuse: if an agent is already running, return the existing process
 * - Graceful shutdown: SIGTERM first, then SIGKILL after timeout
 * - Process health monitoring: detect crashed processes and restart
 * - Clean shutdown on JVM exit via shutdown hook
 *
 * This is JVM-only since it uses java.lang.Process.
 */
class AcpAgentProcessManager private constructor() {

    private val processes = ConcurrentHashMap<String, ManagedProcess>()

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            shutdownAll()
        })
    }

    /**
     * Get or create an agent process for the given config.
     *
     * If a healthy process already exists for this agent key, it is reused.
     * Otherwise, a new process is spawned.
     *
     * @param agentKey Unique key for the agent (e.g., "codex", "claude")
     * @param config The agent configuration
     * @param cwd Working directory for the process
     * @return A [ManagedProcess] wrapping the live process
     */
    fun getOrCreateProcess(
        agentKey: String,
        config: AcpAgentConfig,
        cwd: String,
    ): ManagedProcess {
        val existing = processes[agentKey]
        if (existing != null && existing.isAlive()) {
            logger.info { "Reusing existing ACP agent process for '$agentKey' (pid=${existing.pid})" }
            return existing
        }

        // Clean up stale entry if present
        if (existing != null) {
            logger.info { "Previous ACP agent process for '$agentKey' is dead, spawning a new one" }
            existing.destroyQuietly()
            processes.remove(agentKey)
        }

        val managed = spawnProcess(agentKey, config, cwd)
        processes[agentKey] = managed
        return managed
    }

    /**
     * Terminate a specific agent process.
     */
    fun terminateProcess(agentKey: String) {
        val managed = processes.remove(agentKey) ?: return
        logger.info { "Terminating ACP agent process '$agentKey' (pid=${managed.pid})" }
        managed.destroy()
    }

    /**
     * Terminate all managed agent processes.
     */
    fun shutdownAll() {
        if (processes.isEmpty()) return
        logger.info { "Shutting down all ACP agent processes (${processes.size})" }
        val keys = processes.keys.toList()
        for (key in keys) {
            terminateProcess(key)
        }
    }

    /**
     * Check if a process is running for the given agent key.
     */
    fun isRunning(agentKey: String): Boolean {
        return processes[agentKey]?.isAlive() == true
    }

    /**
     * Get all currently managed process keys.
     */
    fun getActiveAgents(): Set<String> {
        return processes.keys.toSet()
    }

    private fun spawnProcess(
        agentKey: String,
        config: AcpAgentConfig,
        cwd: String,
    ): ManagedProcess {
        val cmdList = mutableListOf(config.command).apply {
            addAll(config.getArgsList())
        }

        logger.info { "Spawning ACP agent process: ${cmdList.joinToString(" ")} (cwd=$cwd)" }

        val pb = ProcessBuilder(cmdList).apply {
            directory(File(cwd))
            redirectErrorStream(false) // keep stderr separate for debugging
        }

        // Apply environment variables from config
        config.getEnvMap().forEach { (k, v) ->
            pb.environment()[k] = v
        }

        val process = pb.start()
        logger.info { "ACP agent '$agentKey' started (pid=${process.pid()})" }

        return ManagedProcess(
            agentKey = agentKey,
            process = process,
            command = cmdList,
        )
    }

    companion object {
        @Volatile
        private var instance: AcpAgentProcessManager? = null

        /**
         * Get the singleton instance.
         */
        fun getInstance(): AcpAgentProcessManager {
            return instance ?: synchronized(this) {
                instance ?: AcpAgentProcessManager().also { instance = it }
            }
        }
    }
}

/**
 * Wrapper around a JVM Process with lifecycle helpers.
 */
class ManagedProcess(
    val agentKey: String,
    val process: Process,
    val command: List<String>,
) {
    val pid: Long get() = process.pid()

    val inputStream get() = process.inputStream
    val outputStream get() = process.outputStream
    val errorStream get() = process.errorStream

    fun isAlive(): Boolean = process.isAlive

    /**
     * Gracefully destroy the process (SIGTERM, then force after timeout).
     */
    fun destroy(timeoutMs: Long = 5000) {
        if (!process.isAlive) return
        try {
            process.destroy()
            val exited = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!exited) {
                logger.warn { "ACP agent '$agentKey' did not exit gracefully, force-killing" }
                process.destroyForcibly()
            }
        } catch (e: Exception) {
            logger.warn(e) { "Error destroying ACP agent process '$agentKey'" }
            process.destroyForcibly()
        }
    }

    /**
     * Destroy without logging errors (for shutdown hooks).
     */
    fun destroyQuietly() {
        try {
            if (process.isAlive) {
                process.destroyForcibly()
            }
        } catch (_: Exception) {
        }
    }
}
