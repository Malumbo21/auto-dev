package cc.unitmesh.agent.subagent

import cc.unitmesh.agent.tool.ToolResult
import cc.unitmesh.llm.KoogLLMService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.js.JsExport
import kotlin.js.Promise

/**
 * JS exports for Agents
 *
 * Provides JavaScript-friendly exports for error recovery and analysis agents
 * Now returns ToolResult.AgentResult directly for better interoperability
 */

/**
 * JS-friendly ErrorContext
 */
@JsExport
data class JsErrorContext(
    val command: String,
    val errorMessage: String,
    val exitCode: Int? = null,
    val stdout: String? = null,
    val stderr: String? = null
) {
    fun toCommon(): ErrorContext = ErrorContext(
        command = command,
        errorMessage = errorMessage,
        exitCode = exitCode,
        stdout = stdout,
        stderr = stderr
    )
}

/**
 * JS-friendly AnalysisContext
 */
@JsExport
data class JsAnalysisContext(
    val content: String,
    val contentType: String = "text",
    val source: String = "unknown",
    val metadata: Map<String, String> = emptyMap()
) {
    fun toCommon(): ContentHandlerContext = ContentHandlerContext(
        content = content,
        contentType = contentType,
        source = source,
        metadata = metadata
    )
}

/**
 * JS-friendly ErrorRecoveryAgent
 * Returns ToolResult directly
 */
@JsExport
class JsErrorRecoveryAgent(
    private val projectPath: String,
    private val llmService: KoogLLMService
) {
    private val agent = ErrorRecoveryAgent(projectPath, llmService)
    
    /**
     * Analyze and recover from error
     * Returns a ToolResult with analysis and metadata
     */
    fun execute(errorContext: JsErrorContext): Promise<ToolResult.AgentResult> {
        return GlobalScope.promise {
            val commonContext = errorContext.toCommon()
            agent.execute(commonContext) { /* ignore progress */ }
        }
    }
}

/**
 * JS-friendly AnalysisAgent
 * Returns ToolResult directly
 */
@JsExport
class JsAnalysisAgent(
    private val llmService: KoogLLMService,
    private val contentThreshold: Int = 5000
) {
    private val agent = AnalysisAgent(llmService, contentThreshold)

    /**
     * Analyze content intelligently
     * Returns a ToolResult with analysis and metadata
     */
    fun execute(context: JsAnalysisContext): Promise<ToolResult.AgentResult> {
        return GlobalScope.promise {
            val commonContext = context.toCommon()
            agent.execute(commonContext) { /* ignore progress */ }
        }
    }

    /**
     * Check if content needs analysis
     */
    fun needsHandling(content: String): Boolean {
        return agent.needsHandling(content)
    }
}

/**
 * JS-friendly NanoDSLContext
 */
@JsExport
data class JsNanoDSLContext(
    val description: String,
    val componentType: String? = null,
    val includeState: Boolean = true,
    val includeHttp: Boolean = false
) {
    fun toCommon(): NanoDSLContext = NanoDSLContext(
        description = description,
        componentType = componentType,
        includeState = includeState,
        includeHttp = includeHttp
    )
}

/**
 * JS-friendly NanoDSLAgent
 * Generates NanoDSL UI code from natural language descriptions
 */
@JsExport
class JsNanoDSLAgent(
    private val llmService: KoogLLMService,
    private val promptTemplate: String? = null
) {
    private val agent = NanoDSLAgent(
        llmService = llmService,
        promptTemplate = promptTemplate ?: NanoDSLAgent.DEFAULT_PROMPT
    )

    /**
     * Generate NanoDSL code from description
     * Returns a ToolResult with generated code and metadata
     */
    fun execute(context: JsNanoDSLContext): Promise<ToolResult.AgentResult> {
        return GlobalScope.promise {
            val commonContext = context.toCommon()
            agent.execute(commonContext) { /* ignore progress */ }
        }
    }

    /**
     * Generate NanoDSL code from simple description string
     * Convenience method for simple use cases
     */
    fun generate(description: String, includeHttp: Boolean = false): Promise<ToolResult.AgentResult> {
        return GlobalScope.promise {
            val context = NanoDSLContext(
                description = description,
                includeHttp = includeHttp
            )
            agent.execute(context) { /* ignore progress */ }
        }
    }

    /**
     * Check if content suggests UI generation is needed
     */
    fun shouldTrigger(content: String): Boolean {
        return agent.shouldTrigger(mapOf("content" to content))
    }
}

/**
 * JS-friendly ChartContext
 */
@JsExport
data class JsChartContext(
    val data: String,
    val chartType: String? = null,
    val description: String? = null,
    val title: String? = null
) {
    fun toCommon(): ChartContext = ChartContext(
        data = data,
        chartType = chartType,
        description = description,
        title = title
    )
}

/**
 * JS-friendly ChartAgent
 * Generates chart configurations from data using AI
 */
@JsExport
class JsChartAgent(
    private val llmService: KoogLLMService,
    private val promptTemplate: String? = null
) {
    private val agent = ChartAgent(
        llmService = llmService,
        promptTemplate = promptTemplate ?: ChartAgent.DEFAULT_PROMPT
    )

    /**
     * Generate chart from data
     * Returns a ToolResult with generated chart code and metadata
     */
    fun execute(context: JsChartContext): Promise<ToolResult.AgentResult> {
        return GlobalScope.promise {
            val commonContext = context.toCommon()
            agent.execute(commonContext) { /* ignore progress */ }
        }
    }

    /**
     * Generate chart from simple data string
     * Convenience method for simple use cases
     */
    fun generate(
        data: String,
        chartType: String? = null,
        title: String? = null
    ): Promise<ToolResult.AgentResult> {
        return GlobalScope.promise {
            val context = ChartContext(
                data = data,
                chartType = chartType,
                title = title
            )
            agent.execute(context) { /* ignore progress */ }
        }
    }

    /**
     * Check if content suggests chart generation is needed
     */
    fun shouldTrigger(content: String): Boolean {
        return agent.shouldTrigger(mapOf("content" to content))
    }
}
