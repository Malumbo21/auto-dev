package cc.unitmesh.xiuper.fs.mcp

import cc.unitmesh.xiuper.fs.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.Resource
import kotlinx.serialization.json.*

/**
 * Model Context Protocol (MCP) Backend.
 * 
 * Maps MCP resources and tools to filesystem operations using the official Kotlin MCP SDK.
 * This implementation is cross-platform and works on JVM, JS, iOS, and Android.
 * 
 * Based on: mpp-core/src/commonMain/kotlin/cc/unitmesh/agent/mcp/McpClientManager.kt
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
interface McpBackend : FsBackend {
    /**
     * Get the underlying MCP client for advanced operations.
     */
    val mcpClient: Client
}



/**
 * Default MCP backend implementation using Kotlin MCP SDK.
 */
class DefaultMcpBackend(
    override val mcpClient: Client
) : McpBackend {
    private var resourceCache: List<Resource>? = null
    private var toolCache: List<Tool>? = null
    private val toolArgsCache = mutableMapOf<String, JsonObject>()
    
    override suspend fun stat(path: FsPath): FsStat {
        return when {
            path.value == "/" || path.value == "/resources" || path.value == "/tools" -> 
                FsStat(path, isDirectory = true)
            
            path.value.startsWith("/resources/") && !path.value.endsWith("/") -> {
                val uri = path.value.removePrefix("/resources/")
                val resource = getResource(uri)
                FsStat(
                    path = path,
                    isDirectory = false,
                    size = null,
                    mime = resource.mimeType
                )
            }
            
            path.value.startsWith("/tools/") -> {
                val parts = path.value.removePrefix("/tools/").split("/")
                when (parts.size) {
                    1 -> FsStat(path, isDirectory = true) // /tools/{name}/
                    2 -> when (parts[1]) {
                        "args", "run" -> FsStat(path, isDirectory = false)
                        else -> throw FsException(FsErrorCode.ENOENT, "Unknown tool file: ${parts[1]}")
                    }
                    else -> throw FsException(FsErrorCode.ENOENT, "Invalid tool path")
                }
            }
            
            else -> throw FsException(FsErrorCode.ENOENT, "Path not found: ${path.value}")
        }
    }
    
    override suspend fun list(path: FsPath): List<FsEntry> {
        return when (path.value) {
            "/", "" -> listOf(
                FsEntry.Directory("resources"),
                FsEntry.Directory("tools")
            )
            
            "/resources" -> listResources().map { resource ->
                FsEntry.File(
                    name = encodeUri(resource.uri),
                    size = null,
                    mime = resource.mimeType
                )
            }
            
            "/tools" -> listTools().map { tool ->
                FsEntry.Directory(tool.name)
            }
            
            else -> {
                if (path.value.startsWith("/tools/")) {
                    val toolName = path.value.removePrefix("/tools/").trim('/')
                    if (listTools().any { it.name == toolName }) {
                        listOf(
                            FsEntry.Special("args", FsEntry.SpecialKind.ToolArgs),
                            FsEntry.Special("run", FsEntry.SpecialKind.ToolRun)
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
                readResource(decodeUri(uri))
            }
            
            path.value.startsWith("/tools/") -> {
                val parts = path.value.removePrefix("/tools/").split("/")
                if (parts.size == 2 && parts[1] == "args") {
                    val toolName = parts[0]
                    val args = toolArgsCache[toolName] ?: JsonObject(emptyMap())
                    ReadResult(
                        bytes = args.toString().encodeToByteArray(),
                        contentType = "application/json"
                    )
                } else {
                    throw FsException(FsErrorCode.ENOTSUP, "Cannot read tool run file")
                }
            }
            
            else -> throw FsException(FsErrorCode.ENOENT, "Path not found: ${path.value}")
        }
    }
    
    override suspend fun write(path: FsPath, content: ByteArray, options: WriteOptions): WriteResult {
        return when {
            path.value.startsWith("/tools/") -> {
                val parts = path.value.removePrefix("/tools/").split("/")
                if (parts.size == 2) {
                    val toolName = parts[0]
                    when (parts[1]) {
                        "args" -> {
                            // Write tool arguments
                            val args = Json.parseToJsonElement(content.decodeToString()) as? JsonObject
                                ?: throw FsException(FsErrorCode.EINVAL, "Invalid JSON object")
                            toolArgsCache[toolName] = args
                            WriteResult(ok = true)
                        }
                        "run" -> {
                            // Execute tool
                            val args = toolArgsCache[toolName] ?: JsonObject(emptyMap())
                            executeTool(toolName, args)
                        }
                        else -> throw FsException(FsErrorCode.ENOTSUP, "Unknown tool file: ${parts[1]}")
                    }
                } else {
                    throw FsException(FsErrorCode.EINVAL, "Invalid tool path")
                }
            }
            
            else -> throw FsException(FsErrorCode.ENOTSUP, "Write not supported for path: ${path.value}")
        }
    }
    
    override suspend fun delete(path: FsPath) {
        throw FsException(FsErrorCode.ENOTSUP, "Delete not supported in MCP backend")
    }
    
    override suspend fun mkdir(path: FsPath) {
        throw FsException(FsErrorCode.ENOTSUP, "Mkdir not supported in MCP backend")
    }
    
    // Helper methods
    
    private suspend fun listResources(): List<Resource> {
        if (resourceCache == null) {
            try {
                val result = mcpClient.listResources()
                resourceCache = result?.resources ?: emptyList()
            } catch (e: Exception) {
                throw FsException(FsErrorCode.EIO, "Failed to list MCP resources: ${e.message}")
            }
        }
        return resourceCache ?: emptyList()
    }
    
    private suspend fun getResource(uri: String): Resource {
        return listResources().find { it.uri == uri }
            ?: throw FsException(FsErrorCode.ENOENT, "Resource not found: $uri")
    }
    
    private suspend fun readResource(uri: String): ReadResult {
        try {
            val result = mcpClient.readResource(
                io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest(uri = uri)
            )
            
            if (result?.contents?.isEmpty() != false) {
                throw FsException(FsErrorCode.ENOENT, "Resource has no contents")
            }
            
            val firstContent = result.contents[0]
            val text = when (firstContent) {
                is io.modelcontextprotocol.kotlin.sdk.TextResourceContents -> firstContent.text
                is io.modelcontextprotocol.kotlin.sdk.BlobResourceContents -> firstContent.blob
                else -> throw FsException(FsErrorCode.EIO, "Unknown content type")
            }
            
            return ReadResult(
                bytes = text.encodeToByteArray(),
                contentType = firstContent.mimeType
            )
        } catch (e: Exception) {
            throw FsException(FsErrorCode.EIO, "Failed to read resource: ${e.message}")
        }
    }
    
    private suspend fun listTools(): List<Tool> {
        if (toolCache == null) {
            try {
                val result = mcpClient.listTools()
                toolCache = result?.tools ?: emptyList()
            } catch (e: Exception) {
                throw FsException(FsErrorCode.EIO, "Failed to list MCP tools: ${e.message}")
            }
        }
        return toolCache ?: emptyList()
    }
    
    private suspend fun executeTool(name: String, arguments: JsonObject): WriteResult {
        try {
            val args = arguments.mapValues { it.value }
            val result = mcpClient.callTool(name, arguments = args, compatibility = true, options = null)
            val message = result?.content?.firstOrNull()?.toString() ?: "Tool executed successfully"
            val isError = result?.isError ?: false
            return WriteResult(ok = !isError, message = message)
        } catch (e: Exception) {
            return WriteResult(ok = false, message = "Tool execution failed: ${e.message}")
        }
    }
    
    private fun encodeUri(uri: String): String {
        return uri.replace("/", "_")
    }
    
    private fun decodeUri(encoded: String): String {
        return encoded.replace("_", "/")
    }
}


