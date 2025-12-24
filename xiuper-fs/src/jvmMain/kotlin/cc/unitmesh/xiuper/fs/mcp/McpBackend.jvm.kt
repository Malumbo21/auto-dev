package cc.unitmesh.xiuper.fs.mcp

import cc.unitmesh.xiuper.fs.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.Resource
import kotlinx.serialization.json.*

/**
 * JVM implementation of MCP Backend using the official Kotlin MCP SDK.
 */
actual interface McpBackend : FsBackend {
    actual val isAvailable: Boolean
    val mcpClient: Client
}

/**
 * Default MCP backend implementation for JVM.
 */
class JvmMcpBackend(
    override val mcpClient: Client
) : McpBackend {
    override val isAvailable: Boolean = true
    private var resourceCache: List<Resource>? = null
    private var toolCache: List<Tool>? = null
    private val toolArgsCache = mutableMapOf<String, JsonObject>()
    
    override suspend fun stat(path: FsPath): FsStat {
        return when {
            path.value == "/" || path.value == "/resources" || path.value == "/tools" -> 
                FsStat(path, isDirectory = true)
            
            path.value.startsWith("/resources/") && !path.value.endsWith("/") -> {
                val uri = path.value.removePrefix("/resources/")
                val resources = getResources()
                val resource = resources.find { it.uri == uri }
                if (resource != null) {
                    FsStat(path, isDirectory = false, size = resource.name?.length?.toLong() ?: 0)
                } else {
                    throw FsException(FsErrorCode.ENOENT, "Resource not found: $uri")
                }
            }
            
            path.value.startsWith("/tools/") -> {
                val toolPath = path.value.removePrefix("/tools/")
                val parts = toolPath.split("/")
                if (parts.size == 1) {
                    // Tool directory
                    val toolName = parts[0]
                    val tools = getTools()
                    if (tools.any { it.name == toolName }) {
                        FsStat(path, isDirectory = true)
                    } else {
                        throw FsException(FsErrorCode.ENOENT, "Tool not found: $toolName")
                    }
                } else if (parts.size == 2) {
                    // Tool file (args or run)
                    val toolName = parts[0]
                    val fileName = parts[1]
                    val tools = getTools()
                    if (tools.any { it.name == toolName } && (fileName == "args" || fileName == "run")) {
                        FsStat(path, isDirectory = false)
                    } else {
                        throw FsException(FsErrorCode.ENOENT, "Tool file not found: ${path.value}")
                    }
                } else {
                    throw FsException(FsErrorCode.ENOENT, "Invalid tool path: ${path.value}")
                }
            }
            
            else -> throw FsException(FsErrorCode.ENOENT, "Path not found: ${path.value}")
        }
    }
    
    override suspend fun list(path: FsPath): List<FsEntry> {
        return when (path.value) {
            "/" -> listOf(
                FsEntry.Directory("resources"),
                FsEntry.Directory("tools")
            )
            
            "/resources" -> {
                val resources = getResources()
                resources.map { resource ->
                    FsEntry.File(resource.uri, size = resource.name?.length?.toLong())
                }
            }
            
            "/tools" -> {
                val tools = getTools()
                tools.map { tool ->
                    FsEntry.Directory(tool.name)
                }
            }
            
            else -> {
                if (path.value.startsWith("/tools/")) {
                    val toolName = path.value.removePrefix("/tools/")
                    if (!toolName.contains("/")) {
                        // List tool files
                        listOf(
                            FsEntry.File("args"),
                            FsEntry.File("run")
                        )
                    } else {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }
        }
    }
    
    override suspend fun read(path: FsPath, options: ReadOptions): ReadResult {
        return when {
            path.value.startsWith("/resources/") -> {
                val uri = path.value.removePrefix("/resources/")
                val result = mcpClient.readResource(
                    io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest(uri = uri)
                )
                
                result.contents.firstOrNull()?.let { content ->
                    // TODO: Fix MCP SDK content access - API structure needs investigation
                    val bytes = content.toString().toByteArray()
                    ReadResult(bytes = bytes)
                } ?: ReadResult(bytes = ByteArray(0))
            }
            
            path.value.endsWith("/args") -> {
                val toolName = path.value.removePrefix("/tools/").removeSuffix("/args")
                val args = toolArgsCache[toolName] ?: JsonObject(emptyMap())
                ReadResult(bytes = args.toString().toByteArray())
            }
            
            path.value.endsWith("/run") -> {
                // Return empty content for run files
                ReadResult(bytes = ByteArray(0))
            }
            
            else -> throw FsException(FsErrorCode.ENOENT, "Cannot read: ${path.value}")
        }
    }
    
    override suspend fun write(path: FsPath, content: ByteArray, options: WriteOptions): WriteResult {
        return when {
            path.value.endsWith("/args") -> {
                val toolName = path.value.removePrefix("/tools/").removeSuffix("/args")
                val jsonString = content.toString(Charsets.UTF_8)
                try {
                    val args = Json.parseToJsonElement(jsonString).jsonObject
                    toolArgsCache[toolName] = args
                    WriteResult(ok = true)
                } catch (e: Exception) {
                    throw FsException(FsErrorCode.EINVAL, "Invalid JSON arguments: ${e.message}")
                }
            }
            
            path.value.endsWith("/run") -> {
                val toolName = path.value.removePrefix("/tools/").removeSuffix("/run")
                val args = toolArgsCache[toolName] ?: JsonObject(emptyMap())
                
                try {
                    mcpClient.callTool(
                        io.modelcontextprotocol.kotlin.sdk.CallToolRequest(
                            name = toolName,
                            arguments = args
                        )
                    )
                    WriteResult(ok = true, message = "Tool executed successfully")
                } catch (e: Exception) {
                    throw FsException(FsErrorCode.EIO, "Tool execution failed: ${e.message}")
                }
            }
            
            else -> throw FsException(FsErrorCode.EACCES, "Cannot write to ${path.value}")
        }
    }
    
    override suspend fun delete(path: FsPath) {
        throw FsException(FsErrorCode.EACCES, "Delete not supported for MCP resources")
    }
    
    override suspend fun mkdir(path: FsPath) {
        throw FsException(FsErrorCode.EACCES, "Create directory not supported for MCP resources")
    }
    
    private suspend fun getResources(): List<Resource> {
        if (resourceCache == null) {
            val result = mcpClient.listResources()
            resourceCache = result.resources
        }
        return resourceCache ?: emptyList()
    }
    
    private suspend fun getTools(): List<Tool> {
        if (toolCache == null) {
            val result = mcpClient.listTools()
            toolCache = result.tools
        }
        return toolCache ?: emptyList()
    }
}

/**
 * Create a JVM MCP backend.
 */
actual suspend fun createMcpBackend(serverConfig: McpServerConfig): McpBackend? {
    return try {
        // TODO: Implement actual MCP client creation based on serverConfig
        // This would involve starting the MCP server process and connecting to it
        null // Placeholder for now
    } catch (e: Exception) {
        null
    }
}