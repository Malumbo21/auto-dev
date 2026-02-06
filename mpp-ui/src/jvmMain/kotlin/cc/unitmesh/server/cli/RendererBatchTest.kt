package cc.unitmesh.server.cli

import cc.unitmesh.devins.ui.compose.agent.ComposeRenderer
import cc.unitmesh.agent.render.ToolCallInfo
import cc.unitmesh.agent.tool.ToolType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock

/**
 * Test CLI for ComposeRenderer batching functionality
 * 
 * Tests the ReadFile batching feature to ensure that multiple file reads
 * are collapsed into a single batch item in the timeline.
 * 
 * Usage:
 * ```bash
 * ./gradlew :mpp-ui:runBatchTest
 * ```
 */
object RendererBatchTest {
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("═".repeat(80))
        println("ComposeRenderer Batching Test")
        println("═".repeat(80))
        println()
        
        runBlocking {
            testReadFileBatching()
            println()
            testMixedToolCalls()
            println()
            testBatchBoundaries()
        }
        
        println()
        println("═".repeat(80))
        println("✅ All tests completed")
        println("═".repeat(80))
    }
    
    /**
     * Test 1: ReadFile batching
     * Simulates rapid file reads (like Kimi reviewing a project)
     */
    private suspend fun testReadFileBatching() {
        println("Test 1: ReadFile Batching")
        println("─".repeat(80))
        
        val renderer = ComposeRenderer()
        
        // Start LLM response
        renderer.renderLLMResponseStart()
        renderer.renderLLMResponseChunk("Let me review the project files...")
        renderer.renderLLMResponseEnd()
        
        // Simulate rapid file reads (within 2 second window)
        val files = listOf(
            "src/main/kotlin/Main.kt",
            "src/main/kotlin/Utils.kt",
            "src/main/kotlin/Config.kt",
            "src/main/kotlin/Agent.kt",
            "src/main/kotlin/Renderer.kt",
            "src/main/kotlin/FileSystem.kt",
            "src/test/kotlin/MainTest.kt",
            "src/test/kotlin/UtilsTest.kt",
            "build.gradle.kts",
            "settings.gradle.kts"
        )
        
        println("\n📂 Simulating ${files.size} rapid file reads...")
        for ((index, file) in files.withIndex()) {
            renderer.renderToolCall("read-file", """path="$file"""")
            renderer.renderToolResult(
                toolName = "read-file",
                success = true,
                output = "File content (${100 + index * 50} lines)",
                fullOutput = null,
                metadata = mapOf("execution_time_ms" to "5")
            )
            
            // Small delay between calls (simulating streaming)
            delay(50)
        }
        
        // Print timeline summary
        println("\n📊 Timeline Summary:")
        println("   Total timeline items: ${renderer.timeline.size}")
        
        renderer.timeline.forEachIndexed { index, item ->
            when (item) {
                is cc.unitmesh.agent.render.TimelineItem.ToolCallItem -> {
                    val status = when (item.success) {
                        null -> "⏳ running"
                        true -> "✅ completed"
                        false -> "❌ failed"
                    }
                    println("   $index. ${item.toolName} - $status")
                    if (item.toolName.startsWith("batch:")) {
                        println("       └─ ${item.description}")
                    }
                }
                else -> {
                    println("   $index. ${item::class.simpleName}")
                }
            }
        }
        
        // Validation
        val batchItems = renderer.timeline.filterIsInstance<cc.unitmesh.agent.render.TimelineItem.ToolCallItem>()
            .filter { it.toolName.startsWith("batch:") }
        
        if (batchItems.isNotEmpty()) {
            println("\n✅ Batching worked! Found ${batchItems.size} batch item(s)")
            batchItems.forEach { batch ->
                println("   • ${batch.description}")
            }
        } else {
            println("\n⚠️  No batching occurred (timeline has ${renderer.timeline.size} items)")
            println("   Expected: 1-2 batch items for ${files.size} file reads")
        }
    }
    
    /**
     * Test 2: Mixed tool calls (batched and non-batched)
     */
    private suspend fun testMixedToolCalls() {
        println("\n\nTest 2: Mixed Tool Calls")
        println("─".repeat(80))
        
        val renderer = ComposeRenderer()
        
        renderer.renderLLMResponseStart()
        renderer.renderLLMResponseChunk("Analyzing and modifying files...")
        renderer.renderLLMResponseEnd()
        
        // Mix of read_file (batched) and write_file (not batched)
        println("\n📝 Simulating mixed operations...")
        
        // Batch of reads
        for (i in 1..6) {
            renderer.renderToolCall("read-file", """path="src/File$i.kt"""")
            renderer.renderToolResult("read-file", true, "Content $i", null, emptyMap())
            delay(50)
        }
        
        // Write operation (should NOT be batched)
        renderer.renderToolCall("write-file", """path="src/Output.kt" content="// Updated"""")
        renderer.renderToolResult("write-file", true, "File written", null, emptyMap())
        
        delay(100) // Longer delay - breaks batch window
        
        // Another batch of reads
        for (i in 7..10) {
            renderer.renderToolCall("read-file", """path="src/File$i.kt"""")
            renderer.renderToolResult("read-file", true, "Content $i", null, emptyMap())
            delay(50)
        }
        
        println("\n📊 Timeline Summary:")
        println("   Total timeline items: ${renderer.timeline.size}")
        
        val readCalls = renderer.timeline.filterIsInstance<cc.unitmesh.agent.render.TimelineItem.ToolCallItem>()
            .filter { it.toolName == "read-file" || it.toolName.contains("batch:read-file") }
        val writeCalls = renderer.timeline.filterIsInstance<cc.unitmesh.agent.render.TimelineItem.ToolCallItem>()
            .filter { it.toolName == "write-file" }
        
        println("   • ReadFile calls/batches: ${readCalls.size}")
        println("   • WriteFile calls: ${writeCalls.size}")
        
        if (readCalls.size < 10) {
            println("\n✅ Batching reduced ReadFile items from 10 to ${readCalls.size}")
        }
    }
    
    /**
     * Test 3: Batch window boundaries
     * Tests that batching respects the time window (2 seconds)
     */
    private suspend fun testBatchBoundaries() {
        println("\n\nTest 3: Batch Window Boundaries")
        println("─".repeat(80))
        
        val renderer = ComposeRenderer()
        
        renderer.renderLLMResponseStart()
        
        println("\n⏱️  Testing batch time window (2 seconds)...")
        
        // First batch (within window)
        println("   • Batch 1: 3 reads within 150ms")
        for (i in 1..3) {
            renderer.renderToolCall("read-file", """path="batch1_$i.kt"""")
            renderer.renderToolResult("read-file", true, "Content", null, emptyMap())
            delay(50)
        }
        
        // Wait longer than batch window
        println("   • Waiting 2.5 seconds (exceeds batch window)")
        delay(2500)
        
        // Second batch (new window)
        println("   • Batch 2: 3 reads within 150ms")
        for (i in 1..3) {
            renderer.renderToolCall("read-file", """path="batch2_$i.kt"""")
            renderer.renderToolResult("read-file", true, "Content", null, emptyMap())
            delay(50)
        }
        
        renderer.renderLLMResponseEnd()
        
        println("\n📊 Timeline Summary:")
        println("   Total timeline items: ${renderer.timeline.size}")
        
        val batchItems = renderer.timeline.filterIsInstance<cc.unitmesh.agent.render.TimelineItem.ToolCallItem>()
            .filter { it.toolName.startsWith("batch:") }
        
        println("   • Batch items: ${batchItems.size}")
        println("   • Expected: 2 separate batches (window exceeded)")
        
        if (batchItems.size >= 2) {
            println("\n✅ Batch window boundary respected!")
        }
    }
}
