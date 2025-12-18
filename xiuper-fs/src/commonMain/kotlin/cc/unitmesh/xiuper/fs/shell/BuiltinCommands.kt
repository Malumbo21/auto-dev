package cc.unitmesh.xiuper.fs.shell

import cc.unitmesh.xiuper.fs.*

/**
 * List directory contents
 */
class LsCommand : ShellCommand {
    override val name = "ls"
    override val description = "List directory contents"
    
    override suspend fun execute(args: List<String>, context: ShellContext): ShellResult {
        val pathStr = resolvePath(args.lastOrNull() ?: ".", context.workingDirectory)
        
        return try {
            val entries = context.backend.list(FsPath.of(pathStr))
            val output = entries.joinToString("\n") { it.name }
            ShellResult(0, output, "")
        } catch (e: Exception) {
            ShellResult(1, "", "ls: cannot access '$pathStr': ${e.message}")
        }
    }
}

/**
 * Read file contents
 */
class CatCommand : ShellCommand {
    override val name = "cat"
    override val description = "Read file contents"
    
    override suspend fun execute(args: List<String>, context: ShellContext): ShellResult {
        if (args.isEmpty()) {
            return ShellResult(1, "", "cat: missing file operand")
        }
        
        val pathStr = resolvePath(args[0], context.workingDirectory)
        
        return try {
            val result = context.backend.read(FsPath.of(pathStr), ReadOptions())
            ShellResult(0, result.bytes.decodeToString(), "")
        } catch (e: Exception) {
            ShellResult(1, "", "cat: $pathStr: ${e.message}")
        }
    }
}

/**
 * Write text to stdout or file
 */
class EchoCommand : ShellCommand {
    override val name = "echo"
    override val description = "Write text to stdout or file"
    
    override suspend fun execute(args: List<String>, context: ShellContext): ShellResult {
        // Check for redirection: echo "text" > file
        val redirectIndex = args.indexOf(">")
        
        return if (redirectIndex != -1 && redirectIndex < args.size - 1) {
            val text = args.subList(0, redirectIndex).joinToString(" ")
            val pathStr = resolvePath(args[redirectIndex + 1], context.workingDirectory)
            
            try {
                context.backend.write(FsPath.of(pathStr), text.encodeToByteArray(), WriteOptions())
                ShellResult(0, "", "")
            } catch (e: Exception) {
                ShellResult(1, "", "echo: cannot write to '$pathStr': ${e.message}")
            }
        } else {
            val text = args.joinToString(" ")
            ShellResult(0, text, "")
        }
    }
}

/**
 * Create directory
 */
class MkdirCommand : ShellCommand {
    override val name = "mkdir"
    override val description = "Create directory"
    
    override suspend fun execute(args: List<String>, context: ShellContext): ShellResult {
        if (args.isEmpty()) {
            return ShellResult(1, "", "mkdir: missing operand")
        }
        
        val pathStr = resolvePath(args[0], context.workingDirectory)
        
        return try {
            context.backend.mkdir(FsPath.of(pathStr))
            ShellResult(0, "", "")
        } catch (e: Exception) {
            ShellResult(1, "", "mkdir: cannot create directory '$pathStr': ${e.message}")
        }
    }
}

/**
 * Remove file or directory
 */
class RmCommand : ShellCommand {
    override val name = "rm"
    override val description = "Remove file or directory"
    
    override suspend fun execute(args: List<String>, context: ShellContext): ShellResult {
        if (args.isEmpty()) {
            return ShellResult(1, "", "rm: missing operand")
        }
        
        val pathStr = resolvePath(args[0], context.workingDirectory)
        
        return try {
            context.backend.delete(FsPath.of(pathStr))
            ShellResult(0, "", "")
        } catch (e: Exception) {
            ShellResult(1, "", "rm: cannot remove '$pathStr': ${e.message}")
        }
    }
}

/**
 * Change working directory
 */
class CdCommand(private val interpreter: ShellFsInterpreter) : ShellCommand {
    override val name = "cd"
    override val description = "Change working directory"
    
    override suspend fun execute(args: List<String>, context: ShellContext): ShellResult {
        val pathStr = resolvePath(args.firstOrNull() ?: "/", context.workingDirectory)
        
        return try {
            // Verify directory exists
            context.backend.list(FsPath.of(pathStr))
            interpreter.setWorkingDirectory(pathStr)
            ShellResult(0, "", "")
        } catch (e: Exception) {
            ShellResult(1, "", "cd: $pathStr: ${e.message}")
        }
    }
}

/**
 * Print working directory
 */
class PwdCommand : ShellCommand {
    override val name = "pwd"
    override val description = "Print working directory"
    
    override suspend fun execute(args: List<String>, context: ShellContext): ShellResult {
        return ShellResult(0, context.workingDirectory, "")
    }
}

/**
 * Copy file
 */
class CpCommand : ShellCommand {
    override val name = "cp"
    override val description = "Copy file"
    
    override suspend fun execute(args: List<String>, context: ShellContext): ShellResult {
        if (args.size < 2) {
            return ShellResult(1, "", "cp: missing file operand")
        }
        
        val sourceStr = resolvePath(args[0], context.workingDirectory)
        val destStr = resolvePath(args[1], context.workingDirectory)
        
        return try {
            val result = context.backend.read(FsPath.of(sourceStr), ReadOptions())
            context.backend.write(FsPath.of(destStr), result.bytes, WriteOptions())
            ShellResult(0, "", "")
        } catch (e: Exception) {
            ShellResult(1, "", "cp: cannot copy '$sourceStr' to '$destStr': ${e.message}")
        }
    }
}

/**
 * Move/rename file
 */
class MvCommand : ShellCommand {
    override val name = "mv"
    override val description = "Move/rename file"
    
    override suspend fun execute(args: List<String>, context: ShellContext): ShellResult {
        if (args.size < 2) {
            return ShellResult(1, "", "mv: missing file operand")
        }
        
        val sourceStr = resolvePath(args[0], context.workingDirectory)
        val destStr = resolvePath(args[1], context.workingDirectory)
        
        return try {
            val result = context.backend.read(FsPath.of(sourceStr), ReadOptions())
            context.backend.write(FsPath.of(destStr), result.bytes, WriteOptions())
            context.backend.delete(FsPath.of(sourceStr))
            ShellResult(0, "", "")
        } catch (e: Exception) {
            ShellResult(1, "", "mv: cannot move '$sourceStr' to '$destStr': ${e.message}")
        }
    }
}
