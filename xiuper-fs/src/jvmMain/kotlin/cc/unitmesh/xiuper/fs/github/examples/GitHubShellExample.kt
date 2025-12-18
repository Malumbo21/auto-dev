package cc.unitmesh.xiuper.fs.github.examples

import cc.unitmesh.xiuper.fs.github.GitHubFsBackend
import cc.unitmesh.xiuper.fs.shell.ShellFsInterpreter
import kotlinx.coroutines.runBlocking

/**
 * Interactive GitHub repository browser using shell commands.
 * 
 * Run this example to explore GitHub repositories through familiar shell commands.
 * 
 * Usage:
 * ```bash
 * ./gradlew :xiuper-fs:jvmRun
 * ```
 * 
 * Then try commands like:
 * - ls /microsoft/vscode/main
 * - cat /microsoft/vscode/main/README.md
 * - cd /facebook/react/main
 * - ls .
 */
fun main() = runBlocking {
    println("🚀 GitHub Filesystem Shell")
    println("Navigate GitHub repositories using shell commands!")
    println()
    
    val token = System.getenv("GITHUB_TOKEN")
    if (token != null) {
        println("✅ Using GITHUB_TOKEN from environment")
    } else {
        println("⚠️  No GITHUB_TOKEN found. Using public API (rate limited)")
    }
    println()
    
    val backend = GitHubFsBackend(token = token)
    val shell = ShellFsInterpreter(backend)
    
    // Example 1: Explore VS Code
    println("═══════════════════════════════════════════════")
    println("Example 1: Exploring microsoft/vscode")
    println("═══════════════════════════════════════════════")
    
    executeAndPrint(shell, "ls /microsoft/vscode/main")
    println()
    
    executeAndPrint(shell, "cat /microsoft/vscode/main/package.json")
    println()
    
    // Example 2: Navigate with cd
    println("═══════════════════════════════════════════════")
    println("Example 2: Navigation with cd and pwd")
    println("═══════════════════════════════════════════════")
    
    executeAndPrint(shell, "cd /microsoft/vscode/main/src")
    executeAndPrint(shell, "pwd")
    executeAndPrint(shell, "ls .")
    println()
    
    // Example 3: Read README
    println("═══════════════════════════════════════════════")
    println("Example 3: Reading README.md")
    println("═══════════════════════════════════════════════")
    
    executeAndPrint(shell, "cd /facebook/react/main")
    executeAndPrint(shell, "cat README.md", maxLines = 20)
    println()
    
    // Example 4: Multiple repositories
    println("═══════════════════════════════════════════════")
    println("Example 4: Comparing Multiple Repositories")
    println("═══════════════════════════════════════════════")
    
    val repos = listOf(
        "/microsoft/vscode/main",
        "/facebook/react/main",
        "/vuejs/core/main"
    )
    
    for (repo in repos) {
        println("\n📦 Repository: $repo")
        val result = shell.execute("ls $repo")
        if (result.exitCode == 0) {
            val files = result.stdout.split("\n").take(5)
            files.forEach { println("  - $it") }
            if (result.stdout.split("\n").size > 5) {
                println("  ... and more")
            }
        }
    }
    
    println()
    println("═══════════════════════════════════════════════")
    println("✨ Try it yourself!")
    println("═══════════════════════════════════════════════")
    println("Commands you can try:")
    println("  ls /owner/repo/branch")
    println("  cat /owner/repo/branch/file.md")
    println("  cd /owner/repo/branch/directory")
    println("  pwd")
    println()
    println("Popular repositories to explore:")
    println("  - /microsoft/vscode/main")
    println("  - /facebook/react/main")
    println("  - /vuejs/core/main")
    println("  - /angular/angular/main")
    println("  - /tensorflow/tensorflow/master")
    println("  - /kubernetes/kubernetes/master")
}

private suspend fun executeAndPrint(
    shell: ShellFsInterpreter,
    command: String,
    maxLines: Int? = null
) {
    println("$ $command")
    val result = shell.execute(command)
    
    if (result.exitCode == 0) {
        val output = if (maxLines != null) {
            result.stdout.lines().take(maxLines).joinToString("\n")
        } else {
            result.stdout
        }
        println(output)
        if (maxLines != null && result.stdout.lines().size > maxLines) {
            println("... (${result.stdout.lines().size - maxLines} more lines)")
        }
    } else {
        println("Error: ${result.stderr}")
    }
}

/**
 * Repository analysis example
 */
suspend fun analyzeRepositoryExample() {
    val backend = GitHubFsBackend()
    val shell = ShellFsInterpreter(backend)
    
    println("📊 Repository Analysis")
    println()
    
    val repoPath = "/microsoft/vscode/main"
    shell.execute("cd $repoPath")
    
    // Count files by type
    val files = shell.execute("ls .").stdout.split("\n")
    
    val stats = files.groupingBy { file ->
        when {
            file.endsWith(".md") -> "Markdown"
            file.endsWith(".json") -> "JSON"
            file.endsWith(".js") || file.endsWith(".ts") -> "Code"
            file.endsWith(".yml") || file.endsWith(".yaml") -> "YAML"
            else -> "Other"
        }
    }.eachCount()
    
    println("File Statistics for $repoPath:")
    stats.forEach { (type, count) ->
        println("  $type: $count files")
    }
}

/**
 * Search for content across repositories
 */
suspend fun searchContentExample(pattern: String) {
    val backend = GitHubFsBackend()
    val shell = ShellFsInterpreter(backend)
    
    println("🔍 Searching for: $pattern")
    println()
    
    val repos = listOf(
        "/microsoft/vscode/main",
        "/facebook/react/main"
    )
    
    for (repo in repos) {
        println("Searching in $repo...")
        
        // Read README
        val readme = shell.execute("cat $repo/README.md")
        if (readme.exitCode == 0 && readme.stdout.contains(pattern, ignoreCase = true)) {
            println("  ✅ Found in README.md")
            
            // Show context
            val lines = readme.stdout.lines()
            lines.forEachIndexed { index, line ->
                if (line.contains(pattern, ignoreCase = true)) {
                    val start = maxOf(0, index - 1)
                    val end = minOf(lines.size, index + 2)
                    println("  Context:")
                    lines.subList(start, end).forEach { println("    $it") }
                }
            }
        }
        println()
    }
}

/**
 * Documentation index generator
 */
suspend fun generateDocsIndexExample() {
    val backend = GitHubFsBackend()
    val shell = ShellFsInterpreter(backend)
    
    println("📚 Documentation Index Generator")
    println()
    
    val repoPath = "/microsoft/vscode/main"
    shell.execute("cd $repoPath")
    
    // Find all markdown files in root
    val files = shell.execute("ls .").stdout.split("\n")
    val mdFiles = files.filter { it.endsWith(".md") }
    
    println("# Documentation Index")
    println()
    
    for (file in mdFiles) {
        val content = shell.execute("cat $file")
        if (content.exitCode == 0) {
            // Extract first heading
            val firstHeading = content.stdout.lines()
                .firstOrNull { it.startsWith("#") }
                ?.trim()
                ?: file
            
            println("- [$firstHeading]($file)")
        }
    }
}
