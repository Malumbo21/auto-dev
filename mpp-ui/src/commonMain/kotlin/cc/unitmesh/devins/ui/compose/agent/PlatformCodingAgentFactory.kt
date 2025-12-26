package cc.unitmesh.devins.ui.compose.agent

import cc.unitmesh.agent.CodingAgent
import cc.unitmesh.agent.config.McpToolConfigService
import cc.unitmesh.agent.render.CodingAgentRenderer
import cc.unitmesh.llm.LLMService

/**
 * Platform-specific factory for creating CodingAgent instances
 * with platform-specific file system implementations
 */
expect fun createPlatformCodingAgent(
    projectPath: String,
    llmService: LLMService,
    maxIterations: Int,
    renderer: CodingAgentRenderer,
    mcpToolConfigService: McpToolConfigService
): CodingAgent
