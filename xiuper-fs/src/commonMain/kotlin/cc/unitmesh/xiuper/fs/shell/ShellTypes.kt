package cc.unitmesh.xiuper.fs.shell

import cc.unitmesh.xiuper.fs.FsBackend

/**
 * Context for shell command execution
 */
data class ShellContext(
    val backend: FsBackend,
    val workingDirectory: String = "/",
    val environment: MutableMap<String, String> = mutableMapOf(),
    val stdin: String? = null
)

/**
 * Result of shell command execution
 */
data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

/**
 * Interface for shell commands
 */
interface ShellCommand {
    val name: String
    val description: String
    
    suspend fun execute(args: List<String>, context: ShellContext): ShellResult
}

/**
 * Parsed command with arguments
 */
internal data class ParsedCommand(
    val name: String,
    val args: List<String>
)
