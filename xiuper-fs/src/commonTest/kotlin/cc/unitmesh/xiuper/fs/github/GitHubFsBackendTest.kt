package cc.unitmesh.xiuper.fs.github

import cc.unitmesh.xiuper.fs.shell.ShellFsInterpreter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubFsBackendTest {
    
    @Test
    fun `list root repository contents`() = runTest {
        val backend = GitHubFsBackend()
        val shell = ShellFsInterpreter(backend)
        
        // List root of microsoft/vscode main branch
        val result = shell.execute("ls /microsoft/vscode/main")
        
        assertEquals(0, result.exitCode, "Should successfully list repository contents")
        assertTrue(result.stdout.contains("README.md"), "Should contain README.md")
        assertTrue(result.stdout.contains("package.json"), "Should contain package.json")
    }
    
    @Test
    fun `read file from repository`() = runTest {
        val backend = GitHubFsBackend()
        val shell = ShellFsInterpreter(backend)
        
        // Read package.json from microsoft/vscode
        val result = shell.execute("cat /microsoft/vscode/main/package.json")
        
        assertEquals(0, result.exitCode, "Should successfully read file")
        assertTrue(result.stdout.contains("\"name\""), "Should contain JSON content")
    }
    
    @Test
    fun `list subdirectory`() = runTest {
        val backend = GitHubFsBackend()
        val shell = ShellFsInterpreter(backend)
        
        // List src directory
        val result = shell.execute("ls /microsoft/vscode/main/src")
        
        assertEquals(0, result.exitCode, "Should successfully list subdirectory")
        assertTrue(result.stdout.isNotEmpty(), "Should have contents")
    }
    
    @Test
    fun `navigate with cd and pwd`() = runTest {
        val backend = GitHubFsBackend()
        val shell = ShellFsInterpreter(backend)
        
        // Change to repository directory
        shell.execute("cd /microsoft/vscode/main")
        
        val pwd = shell.execute("pwd")
        assertEquals("/microsoft/vscode/main", pwd.stdout)
        
        // List current directory
        val ls = shell.execute("ls .")
        assertTrue(ls.stdout.contains("README.md"))
    }
    
    @Test
    fun `error on nonexistent path`() = runTest {
        val backend = GitHubFsBackend()
        val shell = ShellFsInterpreter(backend)
        
        val result = shell.execute("cat /nonexistent/repo/main/file.txt")
        
        assertEquals(1, result.exitCode, "Should fail on nonexistent path")
        assertTrue(result.stderr.contains("not found"), "Should indicate file not found")
    }
    
    @Test
    fun `write operations fail on read-only backend`() = runTest {
        val backend = GitHubFsBackend()
        val shell = ShellFsInterpreter(backend)
        
        val result = shell.execute("echo 'test' > /microsoft/vscode/main/test.txt")
        
        assertEquals(1, result.exitCode, "Should fail on write to read-only backend")
        assertTrue(result.stderr.contains("read-only"), "Should indicate read-only error")
    }
}
