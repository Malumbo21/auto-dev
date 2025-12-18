package cc.unitmesh.xiuper.fs.shell

import cc.unitmesh.xiuper.fs.FsBackend

/**
 * Shell interpreter for filesystem operations using POSIX-like commands.
 * 
 * Provides a familiar command-line interface to interact with any FsBackend:
 * - InMemoryBackend
 * - DbFsBackend
 * - RestFsBackend
 * - McpBackend
 * 
 * Usage:
 * ```kotlin
 * val backend = InMemoryBackend()
 * val shell = ShellFsInterpreter(backend)
 * 
 * // Create directory
 * shell.execute("mkdir /projects")
 * 
 * // Write file
 * shell.execute("echo 'Hello World' > /projects/hello.txt")
 * 
 * // List files
 * val result = shell.execute("ls /projects")
 * println(result.stdout)  // hello.txt
 * 
 * // Read file
 * val content = shell.execute("cat /projects/hello.txt")
 * println(content.stdout)  // Hello World
 * ```
 * 
 * @see cc.unitmesh.xiuper.fs.FsBackend
 */
class ShellFsInterpreter(
    private var currentBackend: FsBackend,
    initialWorkingDirectory: String = "/"
) {
    private var workingDirectory: String = initialWorkingDirectory
    private val commands = mutableMapOf<String, ShellCommand>()
    private val environment = mutableMapOf<String, String>()
    
    init {
        registerBuiltinCommands()
    }
    
    /**
     * Execute a shell command
     * 
     * Supported commands:
     * - ls [path]: List directory contents
     * - cat <file>: Read file contents
     * - echo <text> [> file]: Write text to stdout or file
     * - mkdir <path>: Create directory
     * - rm <path>: Remove file or directory
     * - cd <path>: Change working directory
     * - pwd: Print working directory
     * - cp <source> <dest>: Copy file
     * - mv <source> <dest>: Move/rename file
     * 
     * @param commandLine Shell command string
     * @return Result with exit code, stdout, and stderr
     */
    suspend fun execute(commandLine: String): ShellResult {
        if (commandLine.isBlank()) {
            return ShellResult(0, "", "")
        }
        
        val parsed = parseCommand(commandLine)
        val command = commands[parsed.name]
            ?: return ShellResult(127, "", "xiuper-shell: command not found: ${parsed.name}")
        
        val context = ShellContext(
            backend = currentBackend,
            workingDirectory = workingDirectory,
            environment = environment
        )
        
        return try {
            command.execute(parsed.args, context)
        } catch (e: Exception) {
            ShellResult(1, "", e.message ?: "Unknown error")
        }
    }
    
    /**
     * Switch to a different backend
     */
    fun switchBackend(backend: FsBackend) {
        currentBackend = backend
    }
    
    /**
     * Get current backend
     */
    fun getCurrentBackend(): FsBackend = currentBackend
    
    /**
     * Get current working directory
     */
    fun getWorkingDirectory(): String = workingDirectory
    
    /**
     * Set working directory (used by CdCommand)
     */
    internal fun setWorkingDirectory(path: String) {
        workingDirectory = path
    }
    
    /**
     * Register a custom command
     */
    fun registerCommand(command: ShellCommand) {
        commands[command.name] = command
    }
    
    private fun registerBuiltinCommands() {
        registerCommand(LsCommand())
        registerCommand(CatCommand())
        registerCommand(EchoCommand())
        registerCommand(MkdirCommand())
        registerCommand(RmCommand())
        registerCommand(CdCommand(this))
        registerCommand(PwdCommand())
        registerCommand(CpCommand())
        registerCommand(MvCommand())
    }
    
    private fun parseCommand(line: String): ParsedCommand {
        // Simple parsing: split by whitespace, handle quotes later
        val parts = line.trim().split(Regex("\\s+"))
        return ParsedCommand(
            name = parts[0],
            args = parts.drop(1)
        )
    }
}

/**
 * Resolve relative path to absolute path
 */
internal fun resolvePath(path: String, workingDirectory: String): String {
    return when {
        path.startsWith("/") -> path
        path == "." -> workingDirectory
        path == ".." -> {
            val parts = workingDirectory.split("/").filter { it.isNotEmpty() }
            if (parts.isEmpty()) "/" else "/" + parts.dropLast(1).joinToString("/")
        }
        else -> {
            val wd = if (workingDirectory.endsWith("/")) workingDirectory else "$workingDirectory/"
            wd + path
        }
    }.let { normalized ->
        // Normalize // to /
        normalized.replace(Regex("/+"), "/")
    }
}
