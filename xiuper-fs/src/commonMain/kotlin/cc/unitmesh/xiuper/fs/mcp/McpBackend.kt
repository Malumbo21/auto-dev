package cc.unitmesh.xiuper.fs.mcp

import cc.unitmesh.xiuper.fs.*

/**
 * Model Context Protocol (MCP) Backend.
 * 
 * Maps MCP resources and tools to filesystem operations.
 * Platform-specific implementations handle MCP SDK integration.
 * 
 * Directory structure:
 * ```
 * /resources/               -> MCP resources root
 * /resources/{uri}/         -> Individual resource (URI-encoded)
 * /tools/                   -> MCP tools root
 * /tools/{name}/args        -> Tool arguments (JSON)
 * /tools/{name}/run         -> Execute tool (write triggers execution)
 * ```
 */
expect interface McpBackend : FsBackend {
    /**
     * Check if MCP client is available on this platform.
     */
    val isAvailable: Boolean
}

/**
 * Create a platform-specific MCP backend.
 * Returns null if MCP is not supported on the current platform.
 */
expect suspend fun createMcpBackend(serverConfig: McpServerConfig): McpBackend?

/**
 * MCP server configuration.
 */
data class McpServerConfig(
    val name: String,
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap()
)