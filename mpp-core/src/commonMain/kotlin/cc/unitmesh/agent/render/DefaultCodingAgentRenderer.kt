package cc.unitmesh.agent.render

import cc.unitmesh.agent.logging.getLogger
import cc.unitmesh.agent.subagent.SqlOperationType

/**
 * Default console renderer - simple text output
 * Suitable for basic console applications and testing
 */
class DefaultCodingAgentRenderer : BaseRenderer() {

    private val logger = getLogger("DefaultCodingAgentRenderer")

    override fun renderIterationHeader(current: Int, max: Int) {
        logger.info { "\n[$current/$max] Analyzing and executing..." }
        println("\n[$current/$max] Analyzing and executing...")
    }

    override fun renderLLMResponseStart() {
        super.renderLLMResponseStart()
//        print("💭 ")
    }

    override fun renderLLMResponseChunk(chunk: String) {
        reasoningBuffer.append(chunk)

        // Wait for more content if we detect an incomplete devin block
        if (hasIncompleteDevinBlock(reasoningBuffer.toString())) {
            return
        }

        // Extract and handle thinking content
        val extraction = extractThinkingContent(reasoningBuffer.toString())

        // Render thinking content if present
        if (extraction.hasThinking) {
            val thinkContent = extraction.thinkingContent.toString()
            if (thinkContent.isNotEmpty()) {
                val wasInThinkBlock = isInThinkBlock
                isInThinkBlock = extraction.hasIncompleteThinkBlock
                renderThinkingChunk(
                    thinkContent,
                    isStart = !wasInThinkBlock && (extraction.hasCompleteThinkBlock || extraction.hasIncompleteThinkBlock),
                    isEnd = extraction.hasCompleteThinkBlock && !extraction.hasIncompleteThinkBlock
                )
            }
        } else if (isInThinkBlock && !extraction.hasIncompleteThinkBlock) {
            // Think block just ended
            isInThinkBlock = false
        }

        // Filter devin blocks and output clean content
        val processedContent = filterDevinBlocks(extraction.contentWithoutThinking)
        val cleanContent = cleanNewlines(processedContent)

        // Simple output for default renderer
        print(cleanContent)
    }

    // Track thinking display state
    private var thinkingLineCount = 0
    private val maxThinkingLines = 3
    private val thinkingLines = mutableListOf<String>()
    private var currentThinkingLine = StringBuilder()

    override fun renderThinkingChunk(chunk: String, isStart: Boolean, isEnd: Boolean) {
        if (isStart) {
            // Start of thinking block - show header
            thinkingLineCount = 0
            thinkingLines.clear()
            currentThinkingLine.clear()
            print("\u001B[90m🧠 Thinking: ")  // Gray color
        }

        // Process chunk character by character to handle line breaks
        for (char in chunk) {
            if (char == '\n') {
                // Complete current line
                thinkingLines.add(currentThinkingLine.toString())
                currentThinkingLine.clear()
                thinkingLineCount++

                // Keep only last N lines for scrolling effect
                if (thinkingLines.size > maxThinkingLines) {
                    thinkingLines.removeAt(0)
                }

                // Clear line and reprint last N lines (scrolling effect)
                print("\r\u001B[K")  // Clear current line
                print("\u001B[90m🧠 ${thinkingLines.lastOrNull() ?: ""}")
            } else {
                currentThinkingLine.append(char)
                // Print character in gray
                print("\u001B[90m$char")
            }
        }

        if (isEnd) {
            // End of thinking block - reset color and add newline
            println("\u001B[0m")  // Reset color
            thinkingLines.clear()
            currentThinkingLine.clear()
        }
    }

    override fun renderLLMResponseEnd() {
        super.renderLLMResponseEnd()
        logger.debug { "LLM response ended" }
        println("\n")
    }

    override fun renderToolCall(toolName: String, paramsStr: String) {
        logger.info { "Tool call: $toolName $paramsStr" }
        println("🔧 /$toolName $paramsStr")
    }

    override fun renderToolResult(toolName: String, success: Boolean, output: String?, fullOutput: String?, metadata: Map<String, String>) {
        val icon = if (success) "✓" else "✗"
        print("   $icon $toolName")

        // Show key result info if available
        if (success && output != null) {
            // For read-file, show full content (no truncation) so LLM can see complete file
            // For other tools, show preview (300 chars)
            val shouldTruncate = toolName != "read-file"
            val maxLength = if (shouldTruncate) 300 else Int.MAX_VALUE

            val preview = if (output.length > maxLength) output.take(maxLength) else output
            if (preview.isNotEmpty() && !preview.startsWith("Successfully")) {
                print(" → ${preview.replace("\n", " ")}")
                if (shouldTruncate && output.length > maxLength) print("...")
            }
        }
        logger.debug { "Tool result: $toolName success=$success" }
        println()
    }

    override fun renderTaskComplete(executionTimeMs: Long, toolsUsedCount: Int) {
        val parts = mutableListOf<String>()

        if (executionTimeMs > 0) {
            val seconds = executionTimeMs / 1000.0
            val rounded = (seconds * 100).toLong() / 100.0
            parts.add("${rounded}s")
        }

        if (toolsUsedCount > 0) {
            parts.add("$toolsUsedCount tools")
        }

        val info = if (parts.isNotEmpty()) " (${parts.joinToString(", ")})" else ""
        println("✓ Task marked as complete$info\n")
    }

    override fun renderFinalResult(success: Boolean, message: String, iterations: Int) {
        val icon = if (success) "✅" else "⚠️ "
        println("\n$icon $message")
    }

    override fun renderError(message: String) {
        println("❌ $message")
    }

    override fun renderRepeatWarning(toolName: String, count: Int) {
        println("⚠️  Warning: Tool '$toolName' has been called $count times in a row")
    }

    override fun renderRecoveryAdvice(recoveryAdvice: String) {
        println("\n🔧 ERROR RECOVERY ADVICE:")
        println("─".repeat(50))
        // Split by lines and add proper indentation
        recoveryAdvice.lines().forEach { line ->
            if (line.trim().isNotEmpty()) {
                println("   $line")
            } else {
                println()
            }
        }
        println("─".repeat(50))
    }

    override fun renderUserConfirmationRequest(toolName: String, params: Map<String, Any>) {
        println("🔐 Tool '$toolName' requires user confirmation")
        println("   Parameters: ${params.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
        println("   (Auto-approved for now)")
    }

    override fun renderSqlApprovalRequest(
        sql: String,
        operationType: SqlOperationType,
        affectedTables: List<String>,
        isHighRisk: Boolean,
        dryRunResult: cc.unitmesh.agent.database.DryRunResult?,
        onApprove: () -> Unit,
        onReject: () -> Unit
    ) {
        val riskIcon = if (isHighRisk) "!!" else "!"
        println("\n$riskIcon SQL Write Operation Requires Approval")
        println("-".repeat(50))
        println("Operation: ${operationType.name}")
        println("Affected Tables: ${affectedTables.joinToString(", ")}")
        if (isHighRisk) {
            println("WARNING: This is a HIGH-RISK operation!")
        }
        if (dryRunResult != null) {
            println("\nDry Run Result:")
            println("  Valid: ${dryRunResult.isValid}")
            if (dryRunResult.estimatedRows != null) {
                println("  Estimated Rows Affected: ${dryRunResult.estimatedRows}")
            }
            if (dryRunResult.warnings.isNotEmpty()) {
                println("  Warnings: ${dryRunResult.warnings.joinToString(", ")}")
            }
        }
        println("\nSQL:")
        println(sql)
        println("-".repeat(50))
        // Default console renderer auto-rejects for safety
        println("(Auto-rejected in console mode - use interactive UI for approval)")
        onReject()
    }

    override fun renderAgentSketchBlock(
        agentName: String,
        language: String,
        code: String,
        metadata: Map<String, String>
    ) {
        // Console renderer: display the code block with syntax highlighting hint
        println("\n📊 Agent Sketch Block [$agentName]")
        println("─".repeat(50))
        println("```$language")
        println(code)
        println("```")
        println("─".repeat(50))
    }
}