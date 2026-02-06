package cc.unitmesh.server.cli

import cc.unitmesh.agent.acp.AcpClient
import cc.unitmesh.agent.claude.ClaudeCodeClient
import cc.unitmesh.agent.render.CodingAgentRenderer
import cc.unitmesh.config.AcpAgentConfig
import cc.unitmesh.config.ConfigManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import java.io.File

/**
 * CLI tool to debug ACP session and bash tool issues.
 * 
 * Usage:
 *   ./gradlew :mpp-ui:run --args="acp-debug --agent=Gemini --test=wildcard"
 *   ./gradlew :mpp-ui:run --args="acp-debug --agent=Gemini --test=session"
 *   ./gradlew :mpp-ui:run --args="acp-debug --agent=Gemini --test=bash"
 */
object AcpDebugCli {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("🔍 ACP Debug CLI")
        println("=" .repeat(60))

        val agentName = args.find { it.startsWith("--agent=") }
            ?.substringAfter("=")
            ?: "Gemini"
        
        val testType = args.find { it.startsWith("--test=") }
            ?.substringAfter("=")
            ?: "wildcard"

        // Load config
        val config = ConfigManager.load()
        val acpAgents = config.getAcpAgents()
        val agentConfig = acpAgents[agentName]

        if (agentConfig == null) {
            println("❌ Agent '$agentName' not found in config")
            println("Available agents: ${acpAgents.keys.joinToString()}")
            return@runBlocking
        }

        println("🤖 Testing agent: ${agentConfig.name}")
        println("📝 Command: ${agentConfig.command}")
        println("🧪 Test type: $testType")
        println()

        when (testType) {
            "wildcard" -> testWildcard(agentConfig)
            "session" -> testSession(agentConfig)
            "bash" -> testBashCommands(agentConfig)
            "claude" -> testClaudeCode(agentConfig)
            else -> {
                println("Unknown test type: $testType")
                println("Available types: wildcard, session, bash, claude")
            }
        }
    }

    /**
     * Test wildcard/glob patterns with the Bash tool
     */
    private suspend fun testWildcard(config: AcpAgentConfig) {
        println("🧪 Testing wildcard/glob patterns")
        println("-".repeat(60))

        val testCases = listOf(
            "List all Kotlin files: ls *.kt",
            "Find Gradle files: find . -name '*.gradle.kts'",
            "Glob pattern: echo src/**/*.kt",
            "Count files: ls -la | wc -l"
        )

        for ((index, testCase) in testCases.withIndex()) {
            println("\nTest ${index + 1}: $testCase")
            println("-".repeat(40))
            
            val client = createClient(config, System.getProperty("user.dir"))
            val renderer = DebugRenderer()
            
            client.promptAndRender(testCase, renderer)
            client.disconnect()
            
            println("Result: ${renderer.getLastOutput()}")
            println()
        }
    }

    /**
     * Test ACP session lifecycle (connect, prompt, disconnect, reconnect)
     */
    private suspend fun testSession(config: AcpAgentConfig) {
        println("🧪 Testing ACP session lifecycle")
        println("-".repeat(60))

        // Session 1
        println("\n📍 Session 1: Initial connection")
        var client = createClient(config, System.getProperty("user.dir"))
        var renderer = DebugRenderer()
        
        client.promptAndRender("What is 2+2?", renderer)
        println("Session 1 output: ${renderer.getLastOutput()}")
        
        client.disconnect()
        println("✅ Session 1 disconnected")

        // Wait a bit
        kotlinx.coroutines.delay(2000)

        // Session 2 - simulate newSession()
        println("\n📍 Session 2: New session (simulating newSession())")
        client = createClient(config, System.getProperty("user.dir"))
        renderer = DebugRenderer()
        
        client.promptAndRender("Draw a PlantUML architecture diagram for a DDD project", renderer)
        println("Session 2 output: ${renderer.getLastOutput()}")
        
        client.disconnect()
        println("✅ Session 2 disconnected")

        println("\n✅ Session lifecycle test complete")
    }

    /**
     * Test various bash commands to see which ones fail
     */
    private suspend fun testBashCommands(config: AcpAgentConfig) {
        println("🧪 Testing bash commands")
        println("-".repeat(60))

        val testCommands = listOf(
            "pwd",
            "ls",
            "ls -la",
            "echo 'Hello World'",
            "cat README.md | head -5",
            "find . -name '*.kt' | wc -l",
            "ls *.kt",
            "echo *.gradle.kts"
        )

        for ((index, cmd) in testCommands.withIndex()) {
            println("\nCommand ${index + 1}: $cmd")
            println("-".repeat(40))
            
            val client = createClient(config, System.getProperty("user.dir"))
            val renderer = DebugRenderer()
            
            client.promptAndRender("Run this command: $cmd", renderer)
            client.disconnect()
            
            println("Result: ${renderer.getLastOutput()}")
            
            // Small delay between tests
            kotlinx.coroutines.delay(1000)
        }
    }

    /**
     * Test Claude Code direct integration (stream-json protocol, not ACP).
     */
    private suspend fun testClaudeCode(config: AcpAgentConfig) {
        println("Testing Claude Code (stream-json protocol)")
        println("-".repeat(60))

        val cwd = System.getProperty("user.dir")
        val client = ClaudeCodeClient(
            scope = scope,
            binaryPath = config.command,
            workingDirectory = cwd,
            agentName = config.name,
            enableLogging = true,
        )

        println("Starting Claude Code process...")
        client.start()
        println("Claude Code process started. Sending prompt...")

        val renderer = DebugRenderer()

        println("\nSession 1: Simple question")
        println("-".repeat(40))
        client.promptAndRender("What is 2+2? Answer in one word.", renderer)
        println("Session 1 output: ${renderer.getLastOutput()}")

        client.stop()
        println("\nClaude Code test complete")
    }

    private suspend fun createClient(config: AcpAgentConfig, cwd: String): AcpClient {
        println("🔌 Connecting to ${config.name}...")

        // Build command
        val args = config.getArgsList().toMutableList()
        val commandList = mutableListOf(config.command).apply { addAll(args) }

        // Spawn process
        val pb = ProcessBuilder(commandList)
        pb.directory(File(cwd))
        pb.redirectErrorStream(false)

        config.getEnvMap().forEach { (key, value) ->
            pb.environment()[key] = value
        }
        pb.environment()["PWD"] = cwd

        val proc = pb.start()

        // Create ACP client
        val input = proc.inputStream.asSource()
        val output = proc.outputStream.asSink()

        val client = AcpClient(
            coroutineScope = scope,
            input = input,
            output = output,
            clientName = "acp-debug-cli",
            clientVersion = "1.0.0",
            cwd = cwd,
            agentName = config.name,
            enableLogging = true
        )

        client.connect()
        println("✅ Connected (logging to ~/.autodev/acp-logs/)")
        
        return client
    }

    /**
     * Simple renderer that captures output for debugging
     */
    private class DebugRenderer : CodingAgentRenderer {
        private val outputBuilder = StringBuilder()
        private var lastToolCall = ""
        private var lastToolResult = ""

        fun getLastOutput(): String = outputBuilder.toString()

        override fun renderIterationHeader(current: Int, max: Int) {
            println("📍 Iteration $current/$max")
        }

        override fun renderLLMResponseStart() {
            println("🤖 LLM Response Start")
        }

        override fun renderLLMResponseChunk(text: String) {
            print(text)
            outputBuilder.append(text)
        }

        override fun renderLLMResponseEnd() {
            println("\n🤖 LLM Response End")
            outputBuilder.append("\n")
        }

        override fun renderToolCall(toolName: String, paramsStr: String) {
            println("🔧 Tool: $toolName($paramsStr)")
            lastToolCall = "$toolName($paramsStr)"
            outputBuilder.append("Tool: $lastToolCall\n")
        }

        override fun renderToolResult(
            toolName: String,
            success: Boolean,
            output: String?,
            fullOutput: String?,
            metadata: Map<String, String>
        ) {
            val status = if (success) "✅" else "❌"
            val outputText = output ?: fullOutput ?: "(no output)"
            println("$status Result: ${outputText.take(200)}")
            lastToolResult = outputText
            outputBuilder.append("Result: $outputText\n")
        }

        override fun renderTaskComplete(executionTimeMs: Long, toolsUsedCount: Int) {
            println("⏱️  Task complete (${executionTimeMs}ms, $toolsUsedCount tools)")
        }

        override fun renderFinalResult(success: Boolean, message: String, iterations: Int) {
            val status = if (success) "✅" else "❌"
            println("$status Final: $message (iterations: $iterations)")
            outputBuilder.append("Final: $message\n")
        }

        override fun renderThinkingChunk(thought: String, isStart: Boolean, isEnd: Boolean) {
            if (isStart) println("💭 Thinking Start")
            if (thought.isNotEmpty()) {
                print(thought)
                outputBuilder.append(thought)
            }
            if (isEnd) println("\n💭 Thinking End")
        }

        override fun renderInfo(message: String) {
            println("ℹ️  $message")
            outputBuilder.append("Info: $message\n")
        }

        override fun renderError(error: String) {
            println("❌ Error: $error")
            outputBuilder.append("Error: $error\n")
        }

        override fun renderRepeatWarning(toolName: String, count: Int) {
            println("⚠️  Warning: $toolName repeated $count times")
        }

        override fun renderRecoveryAdvice(recoveryAdvice: String) {
            println("💡 Recovery advice: $recoveryAdvice")
        }

        override fun renderUserConfirmationRequest(toolName: String, params: Map<String, Any>) {
            println("❓ Confirmation request: $toolName with params $params")
        }
    }
}
