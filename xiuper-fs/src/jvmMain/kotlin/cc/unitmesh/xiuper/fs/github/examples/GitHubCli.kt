package cc.unitmesh.xiuper.fs.github.examples

import cc.unitmesh.xiuper.fs.github.GitHubFsBackend
import cc.unitmesh.xiuper.fs.shell.ShellFsInterpreter
import kotlinx.coroutines.runBlocking

/**
 * Simple GitHub repository browser CLI.
 * 
 * Usage:
 * ```bash
 * # Without token (public repos only, rate limited)
 * ./gradlew :xiuper-fs:run
 * 
 * # With token (higher rate limits, private repos)
 * GITHUB_TOKEN=ghp_xxxxx ./gradlew :xiuper-fs:run
 * ```
 */
fun main(args: Array<String>) = runBlocking {
    println("╔═══════════════════════════════════════════════╗")
    println("║    GitHub Repository Filesystem Browser      ║")
    println("╚═══════════════════════════════════════════════╝")
    println()
    
    val token = System.getenv("GITHUB_TOKEN")
    if (token != null) {
        println("✅ Using GITHUB_TOKEN from environment")
    } else {
        println("⚠️  No GITHUB_TOKEN found")
        println("   Set GITHUB_TOKEN=ghp_xxxxx for higher rate limits")
    }
    println()
    
    val backend = GitHubFsBackend(token = token)
    val shell = ShellFsInterpreter(backend)
    
    // Interactive mode or example mode
    if (args.isNotEmpty()) {
        // Execute commands from arguments
        for (command in args) {
            executeCommand(shell, command)
        }
    } else {
        // Run examples
        runExamples(shell)
    }
}

private suspend fun runExamples(shell: ShellFsInterpreter) {
    println("Running examples...")
    println()
    
    // Example 1: List VS Code repository
    section("Example 1: List microsoft/vscode repository")
    executeCommand(shell, "ls /microsoft/vscode/main")
    
    // Example 2: Read package.json
    section("Example 2: Read package.json")
    executeCommand(shell, "cat /microsoft/vscode/main/package.json")
    
    // Example 3: Navigate and list
    section("Example 3: Navigate to src directory")
    executeCommand(shell, "cd /microsoft/vscode/main")
    executeCommand(shell, "pwd")
    executeCommand(shell, "ls src")
    
    // Example 4: Read README from React
    section("Example 4: Read React README")
    executeCommand(shell, "cat /facebook/react/main/README.md")
    
    // Example 5: Compare repositories
    section("Example 5: Compare popular repositories")
    compareRepositories(shell)
    
    println()
    println("╔═══════════════════════════════════════════════╗")
    println("║              Examples Complete                ║")
    println("╚═══════════════════════════════════════════════╝")
    println()
    println("Try your own commands:")
    println("  ./gradlew :xiuper-fs:run --args=\"ls /owner/repo/branch\"")
    println("  ./gradlew :xiuper-fs:run --args=\"cat /owner/repo/branch/file.md\"")
}

private suspend fun executeCommand(shell: ShellFsInterpreter, command: String) {
    println("$ $command")
    val result = shell.execute(command)
    
    if (result.exitCode == 0) {
        val lines = result.stdout.lines()
        if (lines.size > 20) {
            // Truncate long output
            lines.take(20).forEach { println(it) }
            println("... (${lines.size - 20} more lines)")
        } else {
            println(result.stdout)
        }
    } else {
        println("❌ Error: ${result.stderr}")
    }
    println()
}

private suspend fun compareRepositories(shell: ShellFsInterpreter) {
    val repos = listOf(
        "/microsoft/vscode/main" to "VS Code",
        "/facebook/react/main" to "React",
        "/vuejs/core/main" to "Vue.js",
        "/angular/angular/main" to "Angular"
    )
    
    println("📊 Repository Comparison:")
    println()
    
    for ((path, name) in repos) {
        print("$name: ")
        val result = shell.execute("ls $path")
        if (result.exitCode == 0) {
            val count = result.stdout.lines().filter { it.isNotBlank() }.size
            println("$count items")
        } else {
            println("❌ Failed to access")
        }
    }
}

private fun section(title: String) {
    println()
    println("─".repeat(50))
    println(title)
    println("─".repeat(50))
    println()
}
