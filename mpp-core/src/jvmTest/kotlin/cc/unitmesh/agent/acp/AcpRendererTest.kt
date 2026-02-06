package cc.unitmesh.agent.acp

import com.agentclientprotocol.model.PlanEntry
import com.agentclientprotocol.model.ToolCallStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM-only tests for the AcpRenderer which bridges CodingAgentRenderer to ACP update emitter.
 */
class AcpRendererTest {
    /**
     * Test emitter that captures all emitted events.
     */
    private class TestEmitter : AcpUpdateEmitter {
        val textChunks = mutableListOf<String>()
        val thoughtChunks = mutableListOf<String>()
        val toolCalls = mutableListOf<Triple<String, String, ToolCallStatus>>()
        val planUpdates = mutableListOf<List<PlanEntry>>()

        override suspend fun emitTextChunk(text: String) {
            textChunks.add(text)
        }

        override suspend fun emitThoughtChunk(text: String) {
            thoughtChunks.add(text)
        }

        override suspend fun emitToolCall(
            toolCallId: String,
            title: String,
            status: ToolCallStatus,
            kind: com.agentclientprotocol.model.ToolKind?,
            input: String?,
            output: String?,
        ) {
            toolCalls.add(Triple(toolCallId, title, status))
        }

        override suspend fun emitPlanUpdate(entries: List<PlanEntry>) {
            planUpdates.add(entries)
        }
    }

    @Test
    fun testRenderLLMResponseChunk() = runTest {
        val emitter = TestEmitter()
        val renderer = AcpRenderer(emitter, this)

        renderer.renderLLMResponseStart()
        renderer.renderLLMResponseChunk("Hello ")
        renderer.renderLLMResponseChunk("World")

        testScheduler.advanceUntilIdle()

        assertEquals(2, emitter.textChunks.size)
        assertEquals("Hello ", emitter.textChunks[0])
        assertEquals("World", emitter.textChunks[1])
    }

    @Test
    fun testRenderToolCallAndResult() = runTest {
        val emitter = TestEmitter()
        val renderer = AcpRenderer(emitter, this)

        renderer.renderToolCall("write_file", "path=/src/main.kt")
        renderer.renderToolResult(
            toolName = "write_file",
            success = true,
            output = "File written",
            fullOutput = null
        )

        testScheduler.advanceUntilIdle()

        assertEquals(2, emitter.toolCalls.size)
        assertEquals(ToolCallStatus.IN_PROGRESS, emitter.toolCalls[0].third)
        assertEquals(ToolCallStatus.COMPLETED, emitter.toolCalls[1].third)
    }

    @Test
    fun testRenderError() = runTest {
        val emitter = TestEmitter()
        val renderer = AcpRenderer(emitter, this)

        renderer.renderError("Something went wrong")

        testScheduler.advanceUntilIdle()

        assertEquals(1, emitter.textChunks.size)
        assertTrue(emitter.textChunks[0].contains("Error: Something went wrong"))
    }
}

