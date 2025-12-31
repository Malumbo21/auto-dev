package cc.unitmesh.devins.command

import cc.unitmesh.devins.filesystem.ProjectFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ClaudeSkillCommand
 */
class ClaudeSkillCommandTest {
    
    /**
     * Mock file system for testing
     */
    class MockFileSystem(
        private val projectPath: String = "/test/project",
        private val files: Map<String, String> = emptyMap(),
        private val directories: Set<String> = emptySet()
    ) : ProjectFileSystem {
        override fun getProjectPath(): String = projectPath
        
        override fun readFile(path: String): String? = files[path]
        
        override fun readFileAsBytes(path: String): ByteArray? = files[path]?.encodeToByteArray()
        
        override fun writeFile(path: String, content: String): Boolean = true
        
        override fun exists(path: String): Boolean = files.containsKey(path) || directories.contains(path)
        
        override fun isDirectory(path: String): Boolean = directories.contains(path)
        
        override fun listFiles(path: String, pattern: String?): List<String> {
            // Return directory entries that are direct children of the path
            val prefix = if (path.endsWith("/")) path else "$path/"
            val entries = mutableSetOf<String>()
            
            // Add directories
            directories.filter { it.startsWith(prefix) && it != path }.forEach { dir ->
                val relativePath = dir.removePrefix(prefix)
                val firstPart = relativePath.split("/").firstOrNull()
                if (firstPart != null && !firstPart.contains("/")) {
                    entries.add(firstPart)
                }
            }
            
            // Add files
            files.keys.filter { it.startsWith(prefix) }.forEach { file ->
                val relativePath = file.removePrefix(prefix)
                val firstPart = relativePath.split("/").firstOrNull()
                if (firstPart != null && !firstPart.contains("/")) {
                    entries.add(firstPart)
                }
            }
            
            return entries.toList()
        }
        
        override fun searchFiles(pattern: String, maxDepth: Int, maxResults: Int): List<String> = emptyList()
        
        override fun resolvePath(relativePath: String): String = "$projectPath/$relativePath"
    }
    
    @Test
    fun testClaudeSkillCommandProperties() {
        val skill = ClaudeSkillCommand(
            skillName = "pdf",
            description = "Handle PDF operations",
            template = "Process PDF: \$ARGUMENTS",
            skillPath = "/test/skills/pdf"
        )
        
        assertEquals("pdf", skill.skillName)
        assertEquals("Handle PDF operations", skill.description)
        assertEquals("skill.pdf", skill.fullCommandName)
        assertEquals("/test/skills/pdf", skill.skillPath)
    }
    
    @Test
    fun testFindBySkillName() {
        val skills = listOf(
            ClaudeSkillCommand("pdf", "PDF skill", "template1", "/path1"),
            ClaudeSkillCommand("code-review", "Code review skill", "template2", "/path2"),
            ClaudeSkillCommand("test", "Test skill", "template3", "/path3")
        )
        
        val found = ClaudeSkillCommand.findBySkillName(skills, "code-review")
        assertNotNull(found)
        assertEquals("code-review", found.skillName)
        assertEquals("skill.code-review", found.fullCommandName)
        
        val notFound = ClaudeSkillCommand.findBySkillName(skills, "nonexistent")
        assertNull(notFound)
    }
    
    @Test
    fun testFindByFullName() {
        val skills = listOf(
            ClaudeSkillCommand("pdf", "PDF skill", "template1", "/path1"),
            ClaudeSkillCommand("code-review", "Code review skill", "template2", "/path2")
        )
        
        val found = ClaudeSkillCommand.findByFullName(skills, "skill.pdf")
        assertNotNull(found)
        assertEquals("pdf", found.skillName)
        
        val notFound = ClaudeSkillCommand.findByFullName(skills, "skill.nonexistent")
        assertNull(notFound)
    }
    
    @Test
    fun testLoadFromProjectRoot() {
        val skillTemplate = """
---
name: test-skill
description: A test skill for unit testing
---

# Test Skill

Process: ${'$'}ARGUMENTS
        """.trimIndent()
        
        val fileSystem = MockFileSystem(
            projectPath = "/test/project",
            files = mapOf(
                "/test/project/test-skill/SKILL.md" to skillTemplate
            ),
            directories = setOf(
                "/test/project",
                "/test/project/test-skill"
            )
        )
        
        val skills = ClaudeSkillCommand.loadFromProjectRoot(fileSystem)
        
        assertEquals(1, skills.size)
        assertEquals("test-skill", skills[0].skillName)
        assertEquals("A test skill for unit testing", skills[0].description)
        assertEquals("skill.test-skill", skills[0].fullCommandName)
    }
    
    @Test
    fun testLoadSkillWithoutFrontmatter() {
        val skillTemplate = """
# Simple Skill

Just some content without frontmatter.
        """.trimIndent()
        
        val fileSystem = MockFileSystem(
            projectPath = "/test/project",
            files = mapOf(
                "/test/project/simple-skill/SKILL.md" to skillTemplate
            ),
            directories = setOf(
                "/test/project",
                "/test/project/simple-skill"
            )
        )
        
        val skills = ClaudeSkillCommand.loadFromProjectRoot(fileSystem)
        
        assertEquals(1, skills.size)
        // Should use directory name as skill name
        assertEquals("simple-skill", skills[0].skillName)
        // Should use default description
        assertEquals("Claude Skill: simple-skill", skills[0].description)
    }
}

