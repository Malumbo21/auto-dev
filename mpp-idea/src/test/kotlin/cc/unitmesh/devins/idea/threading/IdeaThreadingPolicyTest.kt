package cc.unitmesh.devins.idea.threading

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class IdeaThreadingPolicyTest {
    private val mainSourceRoot = File("src/main/kotlin")

    @Test
    fun `main compose code uses IDEA coroutine wrappers`() {
        val forbiddenApi = Regex("\\b(LaunchedEffect|rememberCoroutineScope|collectAsState)\\s*\\(")
        val allowedFiles = setOf("cc/unitmesh/devins/idea/compose/IdeaComposeEffects.kt")

        val violations = mainSourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.relativeTo(mainSourceRoot).invariantPath() in allowedFiles }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (line.isCommentLine()) {
                        null
                    } else if (forbiddenApi.containsMatchIn(line)) {
                        "${file.relativeTo(mainSourceRoot).invariantPath()}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
            .toList()

        assertTrue(
            violations.isEmpty(),
            "Use IdeaLaunchedEffect, rememberIdeaCoroutineScope, or manual collection instead:\n" +
                violations.joinToString("\n")
        )
    }

    @Test
    fun `tool window factory does not bind scopes to compose main dispatcher`() {
        val factoryFile = File(
            mainSourceRoot,
            "cc/unitmesh/devins/idea/toolwindow/IdeaAgentToolWindowFactory.kt"
        )

        val violations = factoryFile.readLines()
            .mapIndexedNotNull { index, line ->
                if (!line.isCommentLine() && line.contains("Dispatchers.Main")) {
                    "${factoryFile.relativeTo(mainSourceRoot).invariantPath()}:${index + 1}: ${line.trim()}"
                } else {
                    null
                }
            }

        assertTrue(
            violations.isEmpty(),
            "Use IntelliJ-owned project scopes instead of Dispatchers.Main:\n" +
                violations.joinToString("\n")
        )
    }

    private fun String.isCommentLine(): Boolean {
        val trimmed = trimStart()
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
    }

    private fun File.invariantPath(): String = path.replace(File.separatorChar, '/')
}
