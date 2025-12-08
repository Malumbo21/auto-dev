package cc.unitmesh.devins.idea.tool

import cc.unitmesh.agent.tool.ExecutableTool
import cc.unitmesh.devti.provider.toolchain.ToolchainFunctionProvider
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking

/**
 * ToolProvider implementation for IntelliJ IDEA.
 * Collects all ToolchainFunctionProvider extensions and wraps them as ExecutableTools.
 *
 * This bridges the gap between IDEA's extension point system and mpp-core's tool registry,
 * allowing IDEA-specific tools (like database, knowledge, component view, etc.) to be
 * used by CodingAgent through the unified ToolOrchestrator.
 *
 * @param project The IntelliJ Project context required by ToolchainFunctionProvider
 */
class IdeaToolProvider(private val project: Project) {

    /**
     * Provide all IDEA tools from ToolchainFunctionProvider extensions.
     * This method does not require ToolDependencies as IDEA tools use Project context instead.
     */
    fun provideTools(): List<ExecutableTool<*, *>> {
        val providers = ToolchainFunctionProvider.all()
        val tools = mutableListOf<ExecutableTool<*, *>>()

        for (provider in providers) {
            try {
                // Get tool infos from the provider
                val toolInfos = runBlocking { provider.toolInfos(project) }

                // Create an adapter for each tool
                for (agentTool in toolInfos) {
                    val adapter = ToolchainFunctionAdapter(
                        provider = provider,
                        project = project,
                        agentTool = agentTool
                    )
                    tools.add(adapter)
                }

                // If no toolInfos, try to create tools from funcNames
                if (toolInfos.isEmpty()) {
                    val funcNames = runBlocking { provider.funcNames() }
                    for (funcName in funcNames) {
                        // Check if this provider is applicable for this function
                        val isApplicable = runBlocking { provider.isApplicable(project, funcName) }
                        if (isApplicable) {
                            val agentTool = cc.unitmesh.devti.agent.tool.AgentTool(
                                name = funcName,
                                description = "IDEA tool: $funcName",
                                example = "/$funcName"
                            )
                            val adapter = ToolchainFunctionAdapter(
                                provider = provider,
                                project = project,
                                agentTool = agentTool
                            )
                            tools.add(adapter)
                        }
                    }
                }
            } catch (e: Exception) {
                // Log and continue with other providers
                println("Warning: Failed to load tools from ${provider::class.simpleName}: ${e.message}")
            }
        }

        return tools
    }

    companion object {
        /**
         * Create an IdeaToolProvider for the given project.
         */
        fun create(project: Project): IdeaToolProvider {
            return IdeaToolProvider(project)
        }
    }
}

