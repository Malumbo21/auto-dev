package cc.unitmesh.xiuper.fs.mcp

import cc.unitmesh.xiuper.fs.*

/**
 * iOS implementation of MCP Backend.
 * MCP SDK is not available on iOS, so this is a stub implementation.
 */
actual interface McpBackend : FsBackend {
    actual val isAvailable: Boolean
}

/**
 * iOS MCP backend stub implementation.
 * Always returns isAvailable = false since MCP SDK doesn't support iOS.
 */
class IosMcpBackend : McpBackend {
    override val isAvailable: Boolean = false
    
    override suspend fun stat(path: FsPath): FsStat {
        throw FsException(FsErrorCode.ENOTSUP, "MCP is not available on iOS")
    }
    
    override suspend fun list(path: FsPath): List<FsEntry> {
        throw FsException(FsErrorCode.ENOTSUP, "MCP is not available on iOS")
    }
    
    override suspend fun read(path: FsPath, options: ReadOptions): ReadResult {
        throw FsException(FsErrorCode.ENOTSUP, "MCP is not available on iOS")
    }
    
    override suspend fun write(path: FsPath, content: ByteArray, options: WriteOptions): WriteResult {
        throw FsException(FsErrorCode.ENOTSUP, "MCP is not available on iOS")
    }
    
    override suspend fun delete(path: FsPath) {
        throw FsException(FsErrorCode.ENOTSUP, "MCP is not available on iOS")
    }
    
    override suspend fun mkdir(path: FsPath) {
        throw FsException(FsErrorCode.ENOTSUP, "MCP is not available on iOS")
    }
}

/**
 * Create an iOS MCP backend.
 * Always returns null since MCP is not supported on iOS.
 */
actual suspend fun createMcpBackend(serverConfig: McpServerConfig): McpBackend? {
    return null // MCP not supported on iOS
}