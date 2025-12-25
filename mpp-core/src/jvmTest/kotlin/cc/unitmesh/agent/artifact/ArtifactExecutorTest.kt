package cc.unitmesh.agent.artifact

import cc.unitmesh.agent.ArtifactAgent
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ArtifactExecutorTest {

    private fun createTempDir(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "artifact-executor-test-$name-${System.currentTimeMillis()}")
        dir.mkdirs()
        return dir
    }

    @Test
    fun nodeJsArtifactShouldHaveCorrectPackageJson() = runBlocking {
        val artifact = ArtifactAgent.Artifact(
            identifier = "nodejs-test",
            type = ArtifactAgent.Artifact.ArtifactType.NODEJS,
            title = "Node.js Test App",
            content = """
                console.log('Hello from Node.js!');
            """.trimIndent()
        )

        val bundle = ArtifactBundle.fromArtifact(
            artifact = artifact,
            conversationHistory = emptyList(),
            modelInfo = null
        ).copy(
            dependencies = mapOf("express" to "^4.18.2")
        )

        // Verify bundle type
        assertTrue(bundle.type == ArtifactType.NODEJS, "Bundle type should be NODEJS")
        assertTrue(bundle.mainContent.contains("Hello from Node.js"), "Should contain Node.js code")

        // Verify package.json generation
        val packageJson = bundle.generatePackageJson()
        assertTrue(packageJson.contains("\"type\": \"module\""), "Should have module type")
        assertTrue(packageJson.contains("\"main\": \"index.js\""), "Should have index.js as main")
        assertTrue(packageJson.contains("\"express\""), "Should contain express dependency")
        assertTrue(packageJson.contains("\"start\": \"node index.js\""), "Should have start script")
    }

    @Test
    fun nodeJsArtifactShouldPackAndExtractCorrectly() = runBlocking {
        val tempDir = createTempDir("nodejs-pack")
        try {
            val artifact = ArtifactAgent.Artifact(
                identifier = "nodejs-pack-test",
                type = ArtifactAgent.Artifact.ArtifactType.NODEJS,
                title = "Node.js Pack Test",
                content = """
                    import express from 'express';
                    const app = express();
                    app.get('/', (req, res) => res.json({ message: 'Hello' }));
                    app.listen(3000);
                """.trimIndent()
            )

            val bundle = ArtifactBundle.fromArtifact(
                artifact = artifact,
                conversationHistory = emptyList(),
                modelInfo = null
            ).copy(
                dependencies = mapOf("express" to "^4.18.2")
            )

            // Pack bundle
            val outputFile = File(tempDir, "nodejs-test.unit")
            val packer = ArtifactBundlePacker()
            val packResult = packer.pack(bundle, outputFile.absolutePath)

            assertTrue(packResult is PackResult.Success, "Pack should succeed")

            // Extract to directory
            val extractDir = File(tempDir, "extracted")
            val extractResult = packer.extractToDirectory(outputFile.absolutePath, extractDir.absolutePath)

            assertTrue(extractResult is PackResult.Success, "Extract should succeed")

            // Verify extracted files
            val indexJs = File(extractDir, "index.js")
            val packageJson = File(extractDir, "package.json")

            assertTrue(indexJs.exists(), "index.js should exist")
            assertTrue(packageJson.exists(), "package.json should exist")

            // Verify content
            val indexContent = indexJs.readText()
            assertTrue(indexContent.contains("express"), "index.js should contain express")

            val packageContent = packageJson.readText()
            assertTrue(packageContent.contains("\"express\""), "package.json should contain express")
            assertTrue(packageContent.contains("\"type\": \"module\""), "package.json should have module type")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun artifactExecutorShouldExtractUnitFile() = runBlocking {
        val tempDir = createTempDir("executor-extract")
        try {
            // Create a simple Node.js artifact
            val artifact = ArtifactAgent.Artifact(
                identifier = "executor-test",
                type = ArtifactAgent.Artifact.ArtifactType.NODEJS,
                title = "Executor Test",
                content = """
                    console.log('Test output');
                """.trimIndent()
            )

            val bundle = ArtifactBundle.fromArtifact(
                artifact = artifact,
                conversationHistory = emptyList(),
                modelInfo = null
            )

            // Pack bundle
            val unitFile = File(tempDir, "test.unit")
            val packer = ArtifactBundlePacker()
            val packResult = packer.pack(bundle, unitFile.absolutePath)

            assertTrue(packResult is PackResult.Success, "Pack should succeed")

            // Test extraction (without actual execution, as it requires npm/node)
            val extractDir = File(tempDir, "extracted")
            val extractResult = packer.extractToDirectory(unitFile.absolutePath, extractDir.absolutePath)

            assertTrue(extractResult is PackResult.Success, "Extract should succeed")

            // Verify files exist
            val indexJs = File(extractDir, "index.js")
            val packageJson = File(extractDir, "package.json")

            assertTrue(indexJs.exists(), "index.js should exist after extraction")
            assertTrue(packageJson.exists(), "package.json should exist after extraction")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun nodeJsArtifactTypeShouldBeRecognized() {
        // Test ArtifactType enum
        val nodejsType = ArtifactType.NODEJS
        assertTrue(nodejsType.extension == "js", "NODEJS extension should be 'js'")
        assertTrue(nodejsType.mimeType == "application/autodev.artifacts.nodejs", "NODEJS mime type should match")

        // Test fromExtension
        val fromExt = ArtifactType.fromExtension("js")
        assertTrue(fromExt == ArtifactType.NODEJS, "Should recognize .js extension as NODEJS")

        // Test getMainFileName
        val bundle = ArtifactBundle(
            id = "test",
            name = "Test",
            description = "Test",
            type = ArtifactType.NODEJS,
            mainContent = "console.log('test');",
            context = ArtifactContext()
        )
        assertTrue(bundle.getMainFileName() == "index.js", "Main file name should be index.js")
    }
}

