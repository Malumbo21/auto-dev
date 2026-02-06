package cc.unitmesh.agent.acp

import cc.unitmesh.agent.render.CodingAgentRenderer
import com.agentclientprotocol.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM-only tests for AcpClient.renderSessionUpdate() - the shared utility that bridges
 * ACP session updates to the CodingAgentRenderer system.
 */
class AcpClientRenderTest {
    /**
     * Simple test renderer that captures all calls for verification.
     */
    private class TestRenderer : CodingAgentRenderer {
        val events = mutableListOf<String>()
        var llmStarted = false
        var lastChunk = ""
        var lastToolName = ""
        var lastToolSuccess: Boolean? = null
        var lastInfo = ""
        var lastThinkingChunk = ""
        var thinkingStarted = false

        override fun renderIterationHeader(current: Int, max: Int) {
            events.add("iteration:$current/$max")
        }

        override fun renderLLMResponseStart() {
            llmStarted = true
            events.add("llm-start")
        }

        override fun renderLLMResponseChunk(chunk: String) {
            lastChunk = chunk
            events.add("llm-chunk:$chunk")
        }

        override fun renderLLMResponseEnd() {
            events.add("llm-end")
        }

        override fun renderThinkingChunk(chunk: String, isStart: Boolean, isEnd: Boolean) {
            lastThinkingChunk = chunk
            thinkingStarted = thinkingStarted || isStart
            events.add("think:$chunk")
        }

        override fun renderToolCall(toolName: String, paramsStr: String) {
            lastToolName = toolName
            events.add("tool-call:$toolName")
        }

        override fun renderToolCallWithParams(toolName: String, params: Map<String, Any>) {
            lastToolName = toolName
            events.add("tool-call-params:$toolName")
        }

        override fun renderToolResult(
            toolName: String,
            success: Boolean,
            output: String?,
            fullOutput: String?,
            metadata: Map<String, String>,
        ) {
            lastToolName = toolName
            lastToolSuccess = success
            events.add("tool-result:$toolName:$success")
        }

        override fun renderTaskComplete(executionTimeMs: Long, toolsUsedCount: Int) {
            events.add("task-complete")
        }

        override fun renderFinalResult(success: Boolean, message: String, iterations: Int) {
            events.add("final:$success:$message")
        }

        override fun renderError(message: String) {
            events.add("error:$message")
        }

        override fun renderRepeatWarning(toolName: String, count: Int) {
            events.add("repeat-warning:$toolName:$count")
        }

        override fun renderRecoveryAdvice(recoveryAdvice: String) {
            events.add("recovery:$recoveryAdvice")
        }

        override fun renderUserConfirmationRequest(toolName: String, params: Map<String, Any>) {
            events.add("confirmation:$toolName")
        }

        override fun renderInfo(message: String) {
            lastInfo = message
            events.add("info:$message")
        }
    }

    @Test
    fun testAgentMessageChunkRendering() {
        val renderer = TestRenderer()
        var receivedChunk = false
        var inThought = false

        val update = SessionUpdate.AgentMessageChunk(
            ContentBlock.Text("Hello from agent", Annotations(), null)
        )

        AcpClient.renderSessionUpdate(
            update,
            renderer,
            { receivedChunk },
            { receivedChunk = it },
            { inThought },
            { inThought = it }
        )

        assertTrue(renderer.llmStarted, "LLM response should start on first chunk")
        assertEquals("Hello from agent", renderer.lastChunk)
        assertTrue(receivedChunk, "receivedChunk should be set to true")
    }

    @Test
    fun testAgentThoughtChunkRendering() {
        val renderer = TestRenderer()
        var receivedChunk = false
        var inThought = false

        val update = SessionUpdate.AgentThoughtChunk(
            ContentBlock.Text("Thinking about this...", Annotations(), null)
        )

        AcpClient.renderSessionUpdate(
            update,
            renderer,
            { receivedChunk },
            { receivedChunk = it },
            { inThought },
            { inThought = it }
        )

        assertEquals("Thinking about this...", renderer.lastThinkingChunk)
        assertTrue(renderer.thinkingStarted, "Thinking should start on first thought chunk")
        assertTrue(inThought, "inThought should be set to true")
    }

    @Test
    fun testToolCallWithOutputRendering() {
        val renderer = TestRenderer()
        var receivedChunk = false
        var inThought = false

        val update = SessionUpdate.ToolCall(
            toolCallId = ToolCallId("tc-2"),
            title = "write_file",
            status = ToolCallStatus.COMPLETED,
            kind = ToolKind.EDIT,
            rawInput = null,
            rawOutput = kotlinx.serialization.json.JsonPrimitive("File written successfully"),
            _meta = null
        )

        AcpClient.renderSessionUpdate(
            update,
            renderer,
            { receivedChunk },
            { receivedChunk = it },
            { inThought },
            { inThought = it }
        )

        assertEquals("write_file", renderer.lastToolName)
        assertTrue(renderer.lastToolSuccess == true)
    }

    @Test
    fun testModeUpdateRendering() {
        val renderer = TestRenderer()
        var receivedChunk = false
        var inThought = false

        val update = SessionUpdate.CurrentModeUpdate(
            currentModeId = SessionModeId("plan")
        )

        AcpClient.renderSessionUpdate(
            update,
            renderer,
            { receivedChunk },
            { receivedChunk = it },
            { inThought },
            { inThought = it }
        )

        assertTrue(renderer.lastInfo.contains("plan"))
    }
}

