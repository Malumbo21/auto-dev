package cc.unitmesh.agent.artifact

import cc.unitmesh.agent.ArtifactAgent
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for Node.js artifact bundle creation and validation
 */
class NodeJsArtifactBundleTest {

    private fun createTempDir(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "nodejs-artifact-test-$name-${System.currentTimeMillis()}")
        dir.mkdirs()
        return dir
    }

    @Test
    fun nodeJsBundleShouldContainCorrectFiles() = runBlocking {
        val tempDir = createTempDir("nodejs-files")
        try {
            val artifact = ArtifactAgent.Artifact(
                identifier = "express-app",
                type = ArtifactAgent.Artifact.ArtifactType.NODEJS,
                title = "Express.js Application",
                content = """
                    import express from 'express';
                    const app = express();
                    app.get('/', (req, res) => res.json({ message: 'Hello' }));
                    app.listen(3000, () => console.log('Server running'));
                """.trimIndent()
            )

            val bundle = ArtifactBundle.fromArtifact(
                artifact = artifact,
                conversationHistory = emptyList(),
                modelInfo = null
            ).copy(
                dependencies = mapOf("express" to "^4.18.2")
            )

            val outputFile = File(tempDir, "express-app.unit")
            val packer = ArtifactBundlePacker()
            val result = packer.pack(bundle, outputFile.absolutePath)

            assertTrue(result is PackResult.Success, "Pack should succeed")

            // Verify ZIP contents
            ZipFile(outputFile).use { zip ->
                val entries = zip.entries().toList()
                val entryNames = entries.map { it.name }

                // Should contain all required files
                assertTrue(entryNames.contains("index.js"), "Should contain index.js")
                assertTrue(entryNames.contains("package.json"), "Should contain package.json")
                assertTrue(entryNames.contains("ARTIFACT.md"), "Should contain ARTIFACT.md")
                assertTrue(entryNames.contains(".artifact/context.json"), "Should contain context.json")

                // Verify index.js content
                val indexEntry = entries.find { it.name == "index.js" }!!
                val indexContent = zip.getInputStream(indexEntry).bufferedReader().readText()
                assertTrue(indexContent.contains("express"), "index.js should contain express import")
                assertTrue(indexContent.contains("app.listen"), "index.js should contain app.listen")

                // Verify package.json content
                val packageEntry = entries.find { it.name == "package.json" }!!
                val packageContent = zip.getInputStream(packageEntry).bufferedReader().readText()
                assertTrue(packageContent.contains("\"type\": \"module\""), "package.json should have module type")
                assertTrue(packageContent.contains("\"express\""), "package.json should contain express dependency")
                assertTrue(packageContent.contains("\"start\": \"node index.js\""), "package.json should have start script")
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun nodeJsBundleShouldRoundtripCorrectly() = runBlocking {
        val tempDir = createTempDir("nodejs-roundtrip")
        try {
            val originalArtifact = ArtifactAgent.Artifact(
                identifier = "roundtrip-nodejs",
                type = ArtifactAgent.Artifact.ArtifactType.NODEJS,
                title = "Roundtrip Node.js",
                content = """
                    console.log('Roundtrip test');
                    export default function() { return 'test'; }
                """.trimIndent()
            )

            val originalBundle = ArtifactBundle.fromArtifact(
                artifact = originalArtifact,
                conversationHistory = emptyList(),
                modelInfo = null
            ).copy(
                dependencies = mapOf("express" to "^4.18.2")
            )

            // Pack
            val outputFile = File(tempDir, "roundtrip.unit")
            val packer = ArtifactBundlePacker()
            val packResult = packer.pack(originalBundle, outputFile.absolutePath)
            assertTrue(packResult is PackResult.Success, "Pack should succeed")

            // Unpack
            val unpackResult = packer.unpack(outputFile.absolutePath)
            assertTrue(unpackResult is UnpackResult.Success, "Unpack should succeed")

            val restoredBundle = (unpackResult as UnpackResult.Success).bundle

            // Verify restored data
            assertEquals(originalBundle.name, restoredBundle.name, "Name should match")
            assertEquals(originalBundle.type, restoredBundle.type, "Type should be NODEJS")
            assertEquals(ArtifactType.NODEJS, restoredBundle.type, "Type should be NODEJS")
            assertTrue(restoredBundle.mainContent.contains("Roundtrip test"), "Content should match")
            assertTrue(restoredBundle.dependencies.containsKey("express"), "Dependencies should match")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun nodeJsArtifactMdShouldContainCorrectInstructions() = runBlocking {
        val artifact = ArtifactAgent.Artifact(
            identifier = "nodejs-md-test",
            type = ArtifactAgent.Artifact.ArtifactType.NODEJS,
            title = "Node.js MD Test",
            content = "console.log('test');"
        )

        val bundle = ArtifactBundle.fromArtifact(
            artifact = artifact,
            conversationHistory = emptyList(),
            modelInfo = null
        )

        val artifactMd = bundle.generateArtifactMd()

        // Should contain Node.js specific instructions
        assertTrue(artifactMd.contains("npm install"), "Should contain npm install instruction")
        assertTrue(artifactMd.contains("node index.js"), "Should contain node index.js instruction")
        assertTrue(artifactMd.contains("type: nodejs"), "Should contain nodejs type")
    }

    @Test
    fun nodeJsPackageJsonShouldHaveCorrectStructure() {
        val bundle = ArtifactBundle(
            id = "test-nodejs",
            name = "Test Node.js App",
            description = "Test",
            type = ArtifactType.NODEJS,
            mainContent = "console.log('test');",
            dependencies = mapOf("express" to "^4.18.2", "cors" to "^2.8.5"),
            context = ArtifactContext()
        )

        val packageJson = bundle.generatePackageJson()

        // Verify structure
        assertTrue(packageJson.contains("\"name\": \"test-nodejs\""), "Should have name")
        assertTrue(packageJson.contains("\"type\": \"module\""), "Should have module type")
        assertTrue(packageJson.contains("\"main\": \"index.js\""), "Should have main entry")
        assertTrue(packageJson.contains("\"start\": \"node index.js\""), "Should have start script")
        assertTrue(packageJson.contains("\"setup\": \"npm install\""), "Should have setup script")
        assertTrue(packageJson.contains("\"express\": \"^4.18.2\""), "Should contain express dependency")
        assertTrue(packageJson.contains("\"cors\": \"^2.8.5\""), "Should contain cors dependency")
        assertTrue(packageJson.contains("\"node\": \">=18\""), "Should have node engine requirement")
    }
}

