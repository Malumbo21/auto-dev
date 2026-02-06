package cc.unitmesh.agent.acp

import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.model.*
import kotlinx.serialization.json.JsonElement

/**
 * Internal client session operations used by ACP runtime.
 */
class AcpClientSessionOps(
    private val onSessionUpdate: (SessionUpdate) -> Unit,
    private val onPermissionRequest: (SessionUpdate.ToolCallUpdate, List<PermissionOption>) -> RequestPermissionResponse,
) : ClientSessionOperations {
    override suspend fun notify(notification: SessionUpdate, _meta: JsonElement?) {
        onSessionUpdate(notification)
    }

    override suspend fun requestPermissions(
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
        _meta: JsonElement?,
    ): RequestPermissionResponse {
        return onPermissionRequest(toolCall, permissions)
    }

    override suspend fun fsReadTextFile(
        path: String,
        line: UInt?,
        limit: UInt?,
        _meta: JsonElement?,
    ): ReadTextFileResponse {
        throw UnsupportedOperationException("ACP fs.read_text_file is not supported in this client")
    }

    override suspend fun fsWriteTextFile(
        path: String,
        content: String,
        _meta: JsonElement?,
    ): WriteTextFileResponse {
        throw UnsupportedOperationException("ACP fs.write_text_file is not supported in this client")
    }

    override suspend fun terminalCreate(
        command: String,
        args: List<String>,
        cwd: String?,
        env: List<EnvVariable>,
        outputByteLimit: ULong?,
        _meta: JsonElement?,
    ): CreateTerminalResponse {
        throw UnsupportedOperationException("ACP terminal.create is not supported in this client")
    }

    override suspend fun terminalOutput(
        terminalId: String,
        _meta: JsonElement?,
    ): TerminalOutputResponse {
        throw UnsupportedOperationException("ACP terminal.output is not supported in this client")
    }

    override suspend fun terminalRelease(
        terminalId: String,
        _meta: JsonElement?,
    ): ReleaseTerminalResponse {
        throw UnsupportedOperationException("ACP terminal.release is not supported in this client")
    }

    override suspend fun terminalWaitForExit(
        terminalId: String,
        _meta: JsonElement?,
    ): WaitForTerminalExitResponse {
        throw UnsupportedOperationException("ACP terminal.wait_for_exit is not supported in this client")
    }

    override suspend fun terminalKill(
        terminalId: String,
        _meta: JsonElement?,
    ): KillTerminalCommandResponse {
        throw UnsupportedOperationException("ACP terminal.kill is not supported in this client")
    }
}