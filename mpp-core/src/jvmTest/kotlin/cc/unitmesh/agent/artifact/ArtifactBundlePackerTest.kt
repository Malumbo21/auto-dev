package cc.unitmesh.agent.artifact

import cc.unitmesh.agent.ArtifactAgent
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtifactBundlePackerTest {

    private fun createTempDir(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "artifact-test-$name-${System.currentTimeMillis()}")
        dir.mkdirs()
        return dir
    }

    @Test
    fun packShouldCreateValidZipFile() = runBlocking {
        val tempDir = createTempDir("pack")
        try {
            // Create a test artifact
            val artifact = ArtifactAgent.Artifact(
                identifier = "test-artifact",
                type = ArtifactAgent.Artifact.ArtifactType.HTML,
                title = "Test HTML Page",
                content = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>Test</title></head>
                    <body><h1>Hello World</h1></body>
                    </html>
                """.trimIndent()
            )

            // Create bundle
            val bundle = ArtifactBundle.fromArtifact(artifact)

            // Pack to file
            val outputFile = File(tempDir, "test.unit")
            val packer = ArtifactBundlePacker()
            val result = packer.pack(bundle, outputFile.absolutePath)

            // Verify result
            assertTrue(result is PackResult.Success, "Pack should succeed: $result")
            assertTrue(outputFile.exists(), "Output file should exist")
            assertTrue(outputFile.length() > 0, "Output file should not be empty")

            // Verify it's a valid ZIP file
            ZipFile(outputFile).use { zip ->
                val entries = zip.entries().toList()
                println("ZIP entries: ${entries.map { it.name }}")

                // Should contain expected files
                assertTrue(entries.any { it.name == "ARTIFACT.md" }, "Should contain ARTIFACT.md")
                assertTrue(entries.any { it.name == "package.json" }, "Should contain package.json")
                assertTrue(entries.any { it.name == "index.html" }, "Should contain index.html")
                assertTrue(entries.any { it.name == ".artifact/context.json" }, "Should contain context.json")

                // Verify content
                val htmlEntry = entries.find { it.name == "index.html" }!!
                val htmlContent = zip.getInputStream(htmlEntry).bufferedReader().readText()
                assertTrue(htmlContent.contains("Hello World"), "HTML should contain content")
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun unpackShouldRestoreBundleFromZip() = runBlocking {
        val tempDir = createTempDir("unpack")
        try {
            // Create and pack a bundle
            val originalArtifact = ArtifactAgent.Artifact(
                identifier = "roundtrip-test",
                type = ArtifactAgent.Artifact.ArtifactType.HTML,
                title = "Roundtrip Test",
                content = "<html><body>Roundtrip Content</body></html>"
            )
            val originalBundle = ArtifactBundle.fromArtifact(originalArtifact)

            val outputFile = File(tempDir, "roundtrip.unit")
            val packer = ArtifactBundlePacker()

            // Pack
            val packResult = packer.pack(originalBundle, outputFile.absolutePath)
            assertTrue(packResult is PackResult.Success, "Pack should succeed: $packResult")

            // Unpack
            val unpackResult = packer.unpack(outputFile.absolutePath)
            assertTrue(unpackResult is UnpackResult.Success, "Unpack should succeed: $unpackResult")

            val restoredBundle = (unpackResult as UnpackResult.Success).bundle

            // Verify restored data
            assertEquals(originalBundle.name, restoredBundle.name)
            assertEquals(originalBundle.type, restoredBundle.type)
            assertTrue(restoredBundle.mainContent.contains("Roundtrip Content"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun packedFileCanBeUnzippedWithStandardTools() = runBlocking {
        val tempDir = createTempDir("unzip")
        // Don't delete this one to allow manual inspection
        val artifact = ArtifactAgent.Artifact(
            identifier = "unzip-test",
            type = ArtifactAgent.Artifact.ArtifactType.HTML,
            title = "Unzip Test",
            content = "<html><body>Test</body></html>"
        )
        val bundle = ArtifactBundle.fromArtifact(artifact)

        val outputFile = File(tempDir, "unzip-test.unit")
        val packer = ArtifactBundlePacker()
        packer.pack(bundle, outputFile.absolutePath)

        // Verify file starts with ZIP magic bytes (PK)
        val magicBytes = outputFile.inputStream().use { it.readNBytes(2) }
        assertEquals(0x50, magicBytes[0].toInt() and 0xFF, "First byte should be 'P'")
        assertEquals(0x4B, magicBytes[1].toInt() and 0xFF, "Second byte should be 'K'")

        println("File path: ${outputFile.absolutePath}")
        println("File size: ${outputFile.length()} bytes")
        println("Magic bytes: ${magicBytes.joinToString(" ") { String.format("%02X", it) }}")

        // Verify it can be opened as a ZipFile (this is the real test)
        ZipFile(outputFile).use { zip ->
            val entries = zip.entries().toList()
            println("ZIP contains ${entries.size} entries: ${entries.map { it.name }}")
            assertTrue(entries.isNotEmpty(), "ZIP should contain entries")
        }

        // Also test with unzip command if available
        try {
            val process = ProcessBuilder("unzip", "-l", outputFile.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            println("unzip -l output:\n$output")
            assertEquals(0, exitCode, "unzip command should succeed")
        } catch (e: Exception) {
            println("unzip command not available: ${e.message}")
        }

        // Keep the file for manual inspection
        println("\n📦 Test file kept at: ${outputFile.absolutePath}")
        println("   To inspect: unzip -l ${outputFile.absolutePath}")
        println("   To extract: unzip ${outputFile.absolutePath} -d /tmp/extracted")
    }
}
