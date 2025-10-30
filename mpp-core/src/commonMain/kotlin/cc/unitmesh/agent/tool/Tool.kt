package cc.unitmesh.agent.tool

import kotlinx.serialization.Serializable

/**
 * Base interface for all tools in the Agent system.
 * Tools provide specific functionality that can be invoked by agents.
 */
interface Tool {
    /**
     * The unique name of the tool
     */
    val name: String
    
    /**
     * Human-readable description of what the tool does
     */
    val description: String
}

/**
 * Serializable representation of an agent tool with metadata
 */
@Serializable
data class AgentTool(
    override val name: String,
    override val description: String,
    val example: String = "",
    val isMcp: Boolean = false,
    val completion: String = "",
    val mcpGroup: String = "",
    val isDevIns: Boolean = false,
    val devinScriptPath: String = "",
) : Tool {
    
    override fun toString(): String {
        val descAttr = if (description.isNotEmpty()) " description=\"$description\"" else ""
        val exampleContent = if (example.isNotEmpty()) """
    <example>
        <devin>$example</devin>
    </example>""" else ""
        
        return """<tool name="$name"$descAttr>$exampleContent
</tool>"""
    }
}

/**
 * Result of a tool execution
 */
@Serializable
sealed class ToolResult {
    @Serializable
    data class Success(val content: String, val metadata: Map<String, String> = emptyMap()) : ToolResult()

    @Serializable
    data class Error(val message: String, val errorType: String = "UNKNOWN") : ToolResult()

    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error

    fun getOutput(): String = when (this) {
        is Success -> content
        is Error -> ""
    }

    fun getError(): String = when (this) {
        is Success -> ""
        is Error -> message
    }
}

/**
 * Represents a location that a tool will affect (file path, directory, etc.)
 */
@Serializable
data class ToolLocation(
    val path: String,
    val type: LocationType = LocationType.FILE
)

@Serializable
enum class LocationType {
    FILE,
    DIRECTORY,
    URL,
    OTHER
}

/**
 * Parameters for tool execution context
 */
data class ToolExecutionContext(
    val workingDirectory: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val timeout: Long = 30000L, // 30 seconds default
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Represents a validated and ready-to-execute tool call.
 * Similar to Gemini CLI's ToolInvocation interface.
 */
interface ToolInvocation<TParams : Any, TResult : ToolResult> {
    /**
     * The validated parameters for this specific invocation.
     */
    val params: TParams
    
    /**
     * The tool that created this invocation
     */
    val tool: ExecutableTool<TParams, TResult>
    
    /**
     * Gets a pre-execution description of the tool operation.
     */
    fun getDescription(): String
    
    /**
     * Determines what file system paths the tool will affect.
     */
    fun getToolLocations(): List<ToolLocation>
    
    /**
     * Executes the tool with the validated parameters.
     */
    suspend fun execute(context: ToolExecutionContext = ToolExecutionContext()): TResult
}

/**
 * Base implementation of ToolInvocation
 */
abstract class BaseToolInvocation<TParams : Any, TResult : ToolResult>(
    override val params: TParams,
    override val tool: ExecutableTool<TParams, TResult>
) : ToolInvocation<TParams, TResult> {
    
    override fun getToolLocations(): List<ToolLocation> = emptyList()
    
    override fun getDescription(): String = "${tool.name} with params: $params"
}

/**
 * A tool that can be executed with specific parameters.
 * Similar to Gemini CLI's DeclarativeTool concept.
 */
interface ExecutableTool<TParams : Any, TResult : ToolResult> : Tool {
    /**
     * Validates parameters and creates a tool invocation
     */
    fun createInvocation(params: TParams): ToolInvocation<TParams, TResult>
    
    /**
     * Gets the parameter class for this tool
     */
    fun getParameterClass(): String // We'll use string representation for KMP compatibility
}

/**
 * Base implementation of ExecutableTool
 */
abstract class BaseExecutableTool<TParams : Any, TResult : ToolResult> : ExecutableTool<TParams, TResult> {
    
    override fun createInvocation(params: TParams): ToolInvocation<TParams, TResult> {
        return createToolInvocation(params)
    }
    
    /**
     * Subclasses should implement this to create their specific invocation type
     */
    protected abstract fun createToolInvocation(params: TParams): ToolInvocation<TParams, TResult>
}
