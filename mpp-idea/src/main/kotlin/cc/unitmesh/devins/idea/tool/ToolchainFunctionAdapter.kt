package cc.unitmesh.devins.idea.tool

import cc.unitmesh.agent.tool.*
import cc.unitmesh.agent.tool.schema.DeclarativeToolSchema
import cc.unitmesh.agent.tool.schema.SchemaProperty
import cc.unitmesh.agent.tool.schema.SchemaPropertyBuilder
import cc.unitmesh.agent.tool.schema.ToolCategory
import cc.unitmesh.devti.agent.tool.AgentTool
import cc.unitmesh.devti.provider.toolchain.ToolchainFunctionProvider
import com.intellij.openapi.project.Project

/**
 * Parameters for ToolchainFunctionProvider execution.
 * Uses a generic Map to support various parameter types from different providers.
 */
data class ToolchainFunctionParams(
    val prop: String = "",
    val args: List<Any> = emptyList(),
    val allVariables: Map<String, Any?> = emptyMap()
)

/**
 * Schema for ToolchainFunctionProvider tools.
 * Provides a generic schema that accepts prop, args, and variables.
 */
class ToolchainFunctionSchema(
    private val toolDescription: String,
    private val example: String = ""
) : DeclarativeToolSchema(
    description = toolDescription,
    properties = mapOf(
        "prop" to SchemaPropertyBuilder.string(
            description = "Property or sub-command for the tool",
            required = false
        ),
        "args" to SchemaProperty(
            type = "array",
            description = "Arguments to pass to the tool",
            required = false,
            items = SchemaProperty(type = "string", description = "Argument value")
        ),
        "allVariables" to SchemaProperty(
            type = "object",
            description = "Additional variables for the tool execution",
            required = false,
            additionalProperties = true
        )
    )
) {
    override fun getExampleUsage(toolName: String): String {
        return if (example.isNotEmpty()) example else "/$toolName"
    }
}

/**
 * Adapter that wraps a ToolchainFunctionProvider as an ExecutableTool.
 * This allows IDEA-specific tools to be used in mpp-core's ToolOrchestrator.
 *
 * @param provider The ToolchainFunctionProvider to wrap
 * @param project The IntelliJ Project context
 * @param agentTool The AgentTool metadata from the provider
 */
class ToolchainFunctionAdapter(
    private val provider: ToolchainFunctionProvider,
    private val project: Project,
    private val agentTool: AgentTool
) : BaseExecutableTool<ToolchainFunctionParams, ToolResult>() {

    override val name: String = agentTool.name
    override val description: String = agentTool.description

    override val metadata: ToolMetadata = ToolMetadata(
        displayName = agentTool.name.replace("_", " ").replaceFirstChar { it.uppercase() },
        tuiEmoji = "🔧",
        composeIcon = "extension",
        category = ToolCategory.Utility,
        schema = ToolchainFunctionSchema(agentTool.description, agentTool.example)
    )

    override fun getParameterClass(): String = ToolchainFunctionParams::class.simpleName ?: "ToolchainFunctionParams"

    override fun createToolInvocation(params: ToolchainFunctionParams): ToolInvocation<ToolchainFunctionParams, ToolResult> {
        return ToolchainFunctionInvocation(params, this, provider, project, agentTool.name)
    }
}

/**
 * ToolInvocation implementation for ToolchainFunctionProvider.
 */
class ToolchainFunctionInvocation(
    override val params: ToolchainFunctionParams,
    override val tool: ExecutableTool<ToolchainFunctionParams, ToolResult>,
    private val provider: ToolchainFunctionProvider,
    private val project: Project,
    private val funcName: String
) : ToolInvocation<ToolchainFunctionParams, ToolResult> {

    override fun getDescription(): String {
        return "Execute ${tool.name} with prop='${params.prop}'"
    }

    override fun getToolLocations(): List<ToolLocation> = emptyList()

    override suspend fun execute(context: ToolExecutionContext): ToolResult {
        return try {
            val result = provider.execute(
                project = project,
                prop = params.prop,
                args = params.args,
                allVariables = params.allVariables,
                commandName = funcName
            )

            // Convert the result to ToolResult
            when (result) {
                is String -> ToolResult.Success(result)
                is ToolResult -> result
                else -> ToolResult.Success(result.toString())
            }
        } catch (e: Exception) {
            ToolResult.Error(
                message = "Failed to execute ${tool.name}: ${e.message}",
                errorType = "EXECUTION_ERROR",
                metadata = mapOf("exception" to (e::class.simpleName ?: "Unknown"))
            )
        }
    }
}

