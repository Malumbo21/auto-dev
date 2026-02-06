package cc.unitmesh.agent.acp

import cc.unitmesh.config.AcpAgentConfig
import cc.unitmesh.config.ConfigManager
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Captures raw ACP protocol events directly from `AcpClient.prompt()` Flow.
 * 
 * No renderer, no dedup, no transformation. Each ACP `Event` is serialized
 * with ALL available fields to a JSONL file for offline analysis.
 *
 * Usage:
 * ```bash
 * ./gradlew :mpp-core:runAcpCapture -PacpPrompt="画一下项目架构图"
 * ```
 */
object AcpEventCapture {

    @JvmStatic
    fun main(args: Array<String>) {
        val prompt = System.getProperty("acpPrompt") ?: args.getOrNull(0) ?: run {
            System.err.println("Usage: -PacpPrompt=\"your prompt\"")
            return
        }

        println("ACP Raw Event Capture")
        println("Prompt: $prompt")
        println()

        runBlocking { capture(prompt) }
    }

    private suspend fun capture(prompt: String) {
        val wrapper = ConfigManager.load()
        val agents = wrapper.getAcpAgents()
        val key = wrapper.getActiveAcpAgentKey() ?: run {
            System.err.println("No active ACP agent configured. Set activeAcpAgent in config.")
            return
        }
        val cfg: AcpAgentConfig = agents[key] ?: run {
            System.err.println("Agent '$key' not found in config.")
            return
        }

        println("Agent: ${cfg.name} (${cfg.command})")

        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val outDir = File("docs/test-scripts/acp-raw-events")
        outDir.mkdirs()
        val outFile = File(outDir, "capture_${ts}.jsonl")
        val writer = FileWriter(outFile)

        val cwd = System.getProperty("user.dir")
        val cmdList = mutableListOf(cfg.command).apply { addAll(cfg.getArgsList()) }
        println("Spawning: ${cmdList.joinToString(" ")}")

        val pb = ProcessBuilder(cmdList).apply {
            directory(File(cwd))
            redirectErrorStream(false)
        }
        cfg.getEnvMap().forEach { (k, v) -> pb.environment()[k] = v }
        val proc = pb.start()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = AcpClient(scope, proc.inputStream.asSource(), proc.outputStream.asSink(), cwd = cwd)
        client.connect()
        println("Connected.\n")

        var n = 0

        try {
            client.prompt(prompt).collect { event ->
                n++
                val json = serializeEvent(n, event)
                writer.write(json)
                writer.write("\n")
                writer.flush()

                printSummary(n, event)
            }
        } catch (e: Exception) {
            System.err.println("\nError: ${e.message}")
        } finally {
            writer.close()
            client.disconnect()
            proc.destroyForcibly()
        }

        println("\n\nCaptured $n events -> ${outFile.absolutePath}")
    }

    /**
     * Serialize an ACP Event to a JSON string, preserving all fields.
     */
    private fun serializeEvent(n: Int, event: Event): String {
        val sb = StringBuilder()
        sb.append("{\"n\":$n,\"ts\":${System.currentTimeMillis()}")

        when (event) {
            is Event.SessionUpdateEvent -> {
                sb.append(",\"event\":\"SessionUpdate\"")
                serializeSessionUpdate(sb, event.update)
            }
            is Event.PromptResponseEvent -> {
                sb.append(",\"event\":\"PromptResponse\"")
                sb.append(",\"stopReason\":\"${event.response.stopReason}\"")
            }
        }

        sb.append("}")
        return sb.toString()
    }

    private fun serializeSessionUpdate(sb: StringBuilder, u: SessionUpdate) {
        sb.append(",\"type\":\"${u::class.simpleName}\"")

        when (u) {
            is SessionUpdate.AgentMessageChunk -> {
                sb.append(",\"contentType\":\"${u.content::class.simpleName}\"")
                sb.append(",\"text\":\"${esc(AcpClient.extractText(u.content))}\"")
            }

            is SessionUpdate.AgentThoughtChunk -> {
                sb.append(",\"text\":\"${esc(AcpClient.extractText(u.content))}\"")
            }

            is SessionUpdate.ToolCall -> {
                sb.append(",\"toolCallId\":\"${esc(u.toolCallId?.value ?: "")}\"")
                sb.append(",\"title\":\"${esc(u.title)}\"")
                sb.append(",\"kind\":${jsonNullableEnum(u.kind)}")
                sb.append(",\"status\":${jsonNullableEnum(u.status)}")
                sb.append(",\"rawInput\":${jsonNullableStr(u.rawInput?.toString(), 500)}")
                sb.append(",\"rawOutput\":${jsonNullableStr(u.rawOutput?.toString(), 500)}")
            }

            is SessionUpdate.ToolCallUpdate -> {
                sb.append(",\"toolCallId\":\"${esc(u.toolCallId?.value ?: "")}\"")
                sb.append(",\"title\":${jsonNullableStr(u.title, 200)}")
                sb.append(",\"kind\":${jsonNullableEnum(u.kind)}")
                sb.append(",\"status\":${jsonNullableEnum(u.status)}")
                sb.append(",\"rawInput\":${jsonNullableStr(u.rawInput?.toString(), 500)}")
                sb.append(",\"rawOutput\":${jsonNullableStr(u.rawOutput?.toString(), 500)}")
            }

            is SessionUpdate.PlanUpdate -> {
                sb.append(",\"entriesCount\":${u.entries.size}")
                sb.append(",\"entries\":[")
                u.entries.forEachIndexed { i, entry ->
                    if (i > 0) sb.append(",")
                    sb.append("{\"content\":\"${esc(entry.content)}\",\"status\":\"${entry.status}\"}")
                }
                sb.append("]")
            }

            is SessionUpdate.CurrentModeUpdate -> {
                sb.append(",\"modeId\":\"${esc(u.currentModeId.toString())}\"")
            }

            else -> {
                sb.append(",\"raw\":\"${esc(u.toString().take(300))}\"")
            }
        }
    }

    private fun printSummary(n: Int, event: Event) {
        when (event) {
            is Event.SessionUpdateEvent -> {
                when (val u = event.update) {
                    is SessionUpdate.AgentMessageChunk -> {
                        print(AcpClient.extractText(u.content))
                    }
                    is SessionUpdate.AgentThoughtChunk -> {
                        // silent for readability
                    }
                    is SessionUpdate.ToolCall -> {
                        val id = u.toolCallId?.value?.takeLast(8) ?: "?"
                        println("\n  [$n] ToolCall id=..${id} title=\"${u.title}\" kind=${u.kind} status=${u.status}")
                        u.rawInput?.toString()?.take(100)?.let { if (it.isNotBlank()) println("       input: $it") }
                        u.rawOutput?.toString()?.take(100)?.let { if (it.isNotBlank()) println("       output: $it") }
                    }
                    is SessionUpdate.ToolCallUpdate -> {
                        val id = u.toolCallId?.value?.takeLast(8) ?: "?"
                        val title = u.title ?: ""
                        println("  [$n] ToolCallUpdate id=..${id} title=\"$title\" status=${u.status}")
                        u.rawOutput?.toString()?.take(100)?.let { if (it.isNotBlank()) println("       output: $it") }
                    }
                    is SessionUpdate.PlanUpdate -> {
                        println("  [$n] PlanUpdate (${u.entries.size} entries)")
                    }
                    else -> {
                        println("  [$n] ${u::class.simpleName}")
                    }
                }
            }
            is Event.PromptResponseEvent -> {
                println("\n  [$n] PromptResponse: ${event.response.stopReason}")
            }
        }
    }

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun jsonNullableStr(s: String?, maxLen: Int = 200): String {
        if (s == null) return "null"
        return "\"${esc(s.take(maxLen))}\""
    }

    private fun jsonNullableEnum(e: Enum<*>?): String {
        if (e == null) return "null"
        return "\"${e.name}\""
    }
}
