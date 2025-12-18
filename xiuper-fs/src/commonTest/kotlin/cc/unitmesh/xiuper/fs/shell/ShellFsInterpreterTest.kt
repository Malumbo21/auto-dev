package cc.unitmesh.xiuper.fs.shell

import cc.unitmesh.xiuper.fs.memory.InMemoryFsBackend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShellFsInterpreterTest {
    
    @Test
    fun `mkdir creates directory`() = runTest {
        val backend = InMemoryFsBackend()
        val shell = ShellFsInterpreter(backend)
        
        val result = shell.execute("mkdir /projects")
        
        assertEquals(0, result.exitCode, "mkdir should succeed")
        assertEquals("", result.stderr, "No error expected")
    }
    
    @Test
    fun `echo writes to file`() = runTest {
        val backend = InMemoryFsBackend()
        val shell = ShellFsInterpreter(backend)
        
        shell.execute("mkdir /test")
        val result = shell.execute("echo Hello World > /test/hello.txt")
        
        assertEquals(0, result.exitCode, "echo with redirect should succeed")
        
        // Verify file was written
        val content = shell.execute("cat /test/hello.txt")
        assertEquals("Hello World", content.stdout)
    }
    
    @Test
    fun `cat reads file content`() = runTest {
        val backend = InMemoryFsBackend()
        val shell = ShellFsInterpreter(backend)
        
        // Setup
        shell.execute("mkdir /docs")
        shell.execute("echo Test Content > /docs/readme.txt")
        
        // Test cat
        val result = shell.execute("cat /docs/readme.txt")
        
        assertEquals(0, result.exitCode)
        assertEquals("Test Content", result.stdout)
    }
    
    @Test
    fun `ls lists directory contents`() = runTest {
        val backend = InMemoryFsBackend()
        val shell = ShellFsInterpreter(backend)
        
        // Setup
        shell.execute("mkdir /data")
        shell.execute("echo content1 > /data/file1.txt")
        shell.execute("echo content2 > /data/file2.txt")
        
        // Test ls
        val result = shell.execute("ls /data")
        
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("file1.txt"))
        assertTrue(result.stdout.contains("file2.txt"))
    }
    
    @Test
    fun `cd changes working directory`() = runTest {
        val backend = InMemoryFsBackend()
        val shell = ShellFsInterpreter(backend)
        
        shell.execute("mkdir /workspace")
        shell.execute("cd /workspace")
        
        val pwd = shell.execute("pwd")
        assertEquals("/workspace", pwd.stdout)
    }
    
    @Test
    fun `pwd prints working directory`() = runTest {
        val backend = InMemoryFsBackend()
        val shell = ShellFsInterpreter(backend)
        
        val result = shell.execute("pwd")
        
        assertEquals(0, result.exitCode)
        assertEquals("/", result.stdout)
    }
    
    @Test
    fun `relative paths work with working directory`() = runTest {
        val backend = InMemoryFsBackend()
        val shell = ShellFsInterpreter(backend)
        
        // Setup
        shell.execute("mkdir /projects")
        shell.execute("cd /projects")
        shell.execute("mkdir subdir")
        shell.execute("echo test > file.txt")
        
        // Test relative paths
        val lsResult = shell.execute("ls .")
        assertTrue(lsResult.stdout.contains("subdir"))
        assertTrue(lsResult.stdout.contains("file.txt"))
        
        val catResult = shell.execute("cat file.txt")
        assertEquals("test", catResult.stdout)
    }
    
    @Test
    fun `cp copies file`() = runTest {
        val backend = InMemoryFsBackend()
        val shell = ShellFsInterpreter(backend)
        
        // Setup
        shell.execute("mkdir /src")
        shell.execute("echo original > /src/original.txt")
        
        // Test cp
        val result = shell.execute("cp /src/original.txt /src/copy.txt")
        assertEquals(0, result.exitCode)
        
        // Verify copy
        val content = shell.execute("cat /src/copy.txt")
        assertEquals("original", content.stdout)
    }
    
    @Test
    fun `mv moves file`() = runTest {
        val backend = InMemoryFsBackend()
        val shell = ShellFsInterpreter(backend)
        
        // Setup
        shell.execute("mkdir /temp")
        shell.execute("echo data > /temp/old.txt")
        
        // Test mv
        val result = shell.execute("mv /temp/old.txt /temp/new.txt")
        assertEquals(0, result.exitCode)
        
        // Verify move
        val newContent = shell.execute("cat /temp/new.txt")
        assertEquals("data", newContent.stdout)
        
        // Old file should not exist
        val oldRead = shell.execute("cat /temp/old.txt")
        assertEquals(1, oldRead.exitCode)
    }
    
    @Test
    fun `rm removes file`() = runTest {
        val backend = InMemoryFsBackend()
        val shell = ShellFsInterpreter(backend)
        
        // Setup
        shell.execute("mkdir /trash")
        shell.execute("echo delete me > /trash/file.txt")
        
        // Test rm
        val result = shell.execute("rm /trash/file.txt")
        assertEquals(0, result.exitCode)
        
        // Verify deletion
        val readResult = shell.execute("cat /trash/file.txt")
        assertEquals(1, readResult.exitCode)
    }
    
    @Test
    fun `command not found returns error`() = runTest {
        val backend = InMemoryFsBackend()
        val shell = ShellFsInterpreter(backend)
        
        val result = shell.execute("nonexistent command")
        
        assertEquals(127, result.exitCode)
        assertTrue(result.stderr.contains("command not found"))
    }
    
    @Test
    fun `missing operand returns error`() = runTest {
        val backend = InMemoryFsBackend()
        val shell = ShellFsInterpreter(backend)
        
        val mkdirResult = shell.execute("mkdir")
        assertEquals(1, mkdirResult.exitCode)
        assertTrue(mkdirResult.stderr.contains("missing operand"))
        
        val catResult = shell.execute("cat")
        assertEquals(1, catResult.exitCode)
        assertTrue(catResult.stderr.contains("missing"))
    }
    
    @Test
    fun `backend switch works`() = runTest {
        val backend1 = InMemoryFsBackend()
        val backend2 = InMemoryFsBackend()
        val shell = ShellFsInterpreter(backend1)
        
        // Write to backend1
        shell.execute("mkdir /data")
        shell.execute("echo backend1 > /data/test.txt")
        
        // Switch to backend2
        shell.switchBackend(backend2)
        
        // backend2 should be empty
        val result = shell.execute("cat /data/test.txt")
        assertEquals(1, result.exitCode)
        
        // Write to backend2
        shell.execute("mkdir /data")
        shell.execute("echo backend2 > /data/test.txt")
        
        val content = shell.execute("cat /data/test.txt")
        assertEquals("backend2", content.stdout)
    }
    
    @Test
    fun `complex workflow`() = runTest {
        val backend = InMemoryFsBackend()
        val shell = ShellFsInterpreter(backend)
        
        // Create project structure
        shell.execute("mkdir /project")
        shell.execute("cd /project")
        shell.execute("mkdir src")
        shell.execute("mkdir docs")
        
        // Create files
        shell.execute("echo fun main() {} > src/Main.kt")
        shell.execute("echo # README > docs/README.md")
        
        // List structure
        val lsRoot = shell.execute("ls /project")
        assertTrue(lsRoot.stdout.contains("src"))
        assertTrue(lsRoot.stdout.contains("docs"))
        
        val lsSrc = shell.execute("ls src")
        assertTrue(lsSrc.stdout.contains("Main.kt"))
        
        // Copy file
        shell.execute("cp src/Main.kt src/Main.backup.kt")
        
        val lsSrcAfter = shell.execute("ls src")
        assertTrue(lsSrcAfter.stdout.contains("Main.backup.kt"))
        
        // Clean up
        shell.execute("rm src/Main.backup.kt")
        
        val lsSrcFinal = shell.execute("ls src")
        assertTrue(!lsSrcFinal.stdout.contains("Main.backup.kt"))
    }
}
