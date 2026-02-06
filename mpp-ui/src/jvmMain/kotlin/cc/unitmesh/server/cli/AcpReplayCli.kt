package cc.unitmesh.server.cli

import cc.unitmesh.devins.ui.compose.agent.acp.AcpRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Replay captured ACP events from JSONL file to test renderers.
 * 
 * Usage:
 * ```bash
 * ./gradlew :mpp-ui:runAcpReplay -PacpCapture="capture_20260206_152815.jsonl"
 * ```
 */
object AcpReplayCli {
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("═".repeat(80))
        println("ACP Replay CLI - Test Renderer with Captured Events")
        println("═".repeat(80))
        println()
        
        val captureFile = System.getProperty("acpCapture") ?: args.getOrNull(0) ?: run {
            System.err.println("Usage: -PacpCapture=\"capture_xxx.jsonl\"")
            return
        }
        
        val jsonlPath = if (captureFile.startsWith("/")) {
            File(captureFile)
        } else {
            File("docs/test-scripts/acp-captures/$captureFile")
        }
        
        if (!jsonlPath.exists()) {
            System.err.println("❌ File not found: ${jsonlPath.absolutePath}")
            return
        }
        
        println("📂 Replay file: ${jsonlPath.name}")
        println()
        
        runBlocking {
            replayCapture(jsonlPath)
        }
    }
    
    private suspend fun replayCapture(jsonlFile: File) {
        val renderer = AcpRenderer()
        
        val lines = jsonlFile.readLines()
        println("📊 Total events: ${lines.size}")
        
        var llmChunks = 0
        var toolCalls = 0
        var toolResults = 0
        
        val startTime = System.currentTimeMillis()
        
        lines.forEachIndexed { index, line ->
            if (line.isBlank()) return@forEachIndexed
            
            // Parse JSON manually (simple since we control format)
            val event = parseJsonlEvent(line)
            
            when (event.type) {
                "LLM_RESPONSE_START" -> {
                    renderer.renderLLMResponseStart()
                    println("[${index + 1}] LLM_RESPONSE_START")
                }
                "LLM_RESPONSE_CHUNK" -> {
                    llmChunks++
                    val chunk = event.data["chunk_full"] ?: ""
                    renderer.renderLLMResponseChunk(chunk)
                    if (llmChunks % 10 == 0) {
                        print(".")
                    }
                }
                "LLM_RESPONSE_END" -> {
                    renderer.renderLLMResponseEnd()
                    println("\n[${index + 1}] LLM_RESPONSE_END")
                }
                "TOOL_CALL" -> {
                    toolCalls++
                    val toolName = event.data["tool_name"] ?: "unknown"
                    val params = event.data["params"] ?: ""
                    renderer.renderToolCall(toolName, params)
                    
                    if (toolCalls % 100 == 0) {
                        println("[${index + 1}] TOOL_CALL #$toolCalls: $toolName")
                    }
                }
                "TOOL_RESULT" -> {
                    toolResults++
                    val toolName = event.data["tool_name"] ?: "unknown"
                    val success = event.data["success"] == "true"
                    val output = event.data["output_preview"]
                    renderer.renderToolResult(toolName, success, output, null, emptyMap())
                }
                "ERROR" -> {
                    val message = event.data["message"] ?: "Unknown error"
                    renderer.renderError(message)
                    println("[${index + 1}] ERROR: $message")
                }
            }
            
            // Small delay to simulate streaming (optional, can remove for faster replay)
            if (event.type == "LLM_RESPONSE_CHUNK" && llmChunks % 50 == 0) {
                delay(10)
            }
        }
        
        val elapsed = System.currentTimeMillis() - startTime
        
        println()
        println("═".repeat(80))
        println("✅ Replay Complete")
        println("═".repeat(80))
        println()
        println("📊 Stats:")
        println("   - LLM chunks: $llmChunks")
        println("   - Tool calls: $toolCalls")
        println("   - Tool results: $toolResults")
        println("   - Replay time: ${elapsed}ms")
        println()
        println("📈 Renderer Stats:")
        println("   - Timeline items: ${renderer.timeline.size}")
        println("   - Reduction: ${toolCalls} → ${renderer.timeline.size} items")
        println("   - Compression ratio: ${String.format("%.1f", toolCalls.toDouble() / renderer.timeline.size.coerceAtLeast(1))}x")
        println()
        
        // Show timeline summary
        println("📋 Timeline Summary (first 20 items):")
        renderer.timeline.take(20).forEachIndexed { i, item ->
            when (item) {
                is cc.unitmesh.agent.render.TimelineItem.ToolCallItem -> {
                    val status = when (item.success) {
                        null -> "⏳"
                        true -> "✅"
                        false -> "❌"
                    }
                    println("   $i. $status ${item.toolName}: ${item.description}")
                }
                is cc.unitmesh.agent.render.TimelineItem.MessageItem -> {
                    println("   $i. 💬 ${item.message?.content?.take(50) ?: "(empty)"}")
                }
                else -> {
                    println("   $i. ${item::class.simpleName}")
                }
            }
        }
        
        if (renderer.timeline.size > 20) {
            println("   ... and ${renderer.timeline.size - 20} more items")
        }
    }
    
    private data class JsonlEvent(
        val n: Int,
        val type: String,
        val data: Map<String, String>
    )
    
    private fun parseJsonlEvent(line: String): JsonlEvent {
        // Very simple JSON parser for our controlled format
        val nMatch = Regex(""""n":(\d+)""").find(line)
        val typeMatch = Regex(""""type":"([^"]+)"""").find(line)
        
        val n = nMatch?.groups?.get(1)?.value?.toIntOrNull() ?: 0
        val type = typeMatch?.groups?.get(1)?.value ?: "UNKNOWN"
        
        // Extract data object
        val dataMatch = Regex(""""data":\{([^}]+)\}""").find(line)
        val dataStr = dataMatch?.groups?.get(1)?.value ?: ""
        
        val data = mutableMapOf<String, String>()
        val kvPattern = Regex(""""([^"]+)":"([^"]*?)"""")
        kvPattern.findAll(dataStr).forEach { match ->
            val key = match.groups[1]?.value ?: return@forEach
            val value = match.groups[2]?.value ?: ""
            data[key] = value
        }
        
        return JsonlEvent(n, type, data)
    }
}
