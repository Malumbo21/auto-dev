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
        // Note: We no longer include "type": "module" to support both CommonJS and ES modules
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
            // Note: We no longer include "type": "module" to support both CommonJS and ES modules
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

    @Test
    fun selectBestArtifactShouldSkipPackageJson() {
        // Simulate AI generating two artifacts: package.json and code
        val packageJsonArtifact = ArtifactAgent.Artifact(
            identifier = "express-hello-world",
            type = ArtifactAgent.Artifact.ArtifactType.NODEJS,
            title = "Express.js Hello World",
            content = """
                {
                  "name": "express-hello-world",
                  "version": "1.0.0",
                  "dependencies": {
                    "express": "^4.18.2"
                  }
                }
            """.trimIndent()
        )

        val codeArtifact = ArtifactAgent.Artifact(
            identifier = "express-hello-world-app",
            type = ArtifactAgent.Artifact.ArtifactType.NODEJS,
            title = "Express.js App",
            content = """
                const express = require('express');
                const app = express();
                app.get('/', (req, res) => res.json({ message: 'Hello World!' }));
                app.listen(3000, () => console.log('Server running'));
            """.trimIndent()
        )

        // Test selectBestArtifact
        val artifacts = listOf(packageJsonArtifact, codeArtifact)
        val selected = ArtifactBundle.selectBestArtifact(artifacts)

        assertTrue(selected != null, "Should select an artifact")
        assertTrue(selected!!.identifier == "express-hello-world-app", "Should select code artifact, not package.json")
        assertTrue(selected.content.contains("const express"), "Selected artifact should contain actual code")
    }

    @Test
    fun selectBestArtifactShouldReturnSingleArtifact() {
        val singleArtifact = ArtifactAgent.Artifact(
            identifier = "single-app",
            type = ArtifactAgent.Artifact.ArtifactType.NODEJS,
            title = "Single App",
            content = "console.log('Hello');"
        )

        val selected = ArtifactBundle.selectBestArtifact(listOf(singleArtifact))
        assertTrue(selected != null, "Should return single artifact")
        assertTrue(selected!!.identifier == "single-app", "Should be the same artifact")
    }

    @Test
    fun selectBestArtifactShouldHandleEmptyList() {
        val selected = ArtifactBundle.selectBestArtifact(emptyList())
        assertTrue(selected == null, "Should return null for empty list")
    }

    @Test
    fun nodeJsArtifactShouldAutoDetectDependencies() {
        val codeWithRequire = """
            const express = require('express');
            const cors = require('cors');
            const path = require('path'); // built-in, should be ignored
            const fs = require('fs'); // built-in, should be ignored
            
            const app = express();
            app.use(cors());
            app.listen(3000);
        """.trimIndent()

        val artifact = ArtifactAgent.Artifact(
            identifier = "deps-test",
            type = ArtifactAgent.Artifact.ArtifactType.NODEJS,
            title = "Deps Test",
            content = codeWithRequire
        )

        val bundle = ArtifactBundle.fromArtifact(artifact)

        // Should detect express and cors, but not path and fs (built-ins)
        assertTrue(bundle.dependencies.containsKey("express"), "Should detect express dependency")
        assertTrue(bundle.dependencies.containsKey("cors"), "Should detect cors dependency")
        assertTrue(!bundle.dependencies.containsKey("path"), "Should not include path (built-in)")
        assertTrue(!bundle.dependencies.containsKey("fs"), "Should not include fs (built-in)")

        // Verify package.json contains dependencies
        val packageJson = bundle.generatePackageJson()
        assertTrue(packageJson.contains("\"express\""), "package.json should contain express")
        assertTrue(packageJson.contains("\"cors\""), "package.json should contain cors")
    }

    @Test
    fun nodeJsArtifactShouldAutoDetectImportDependencies() {
        val codeWithImport = """
            import express from 'express';
            import { Router } from 'express';
            import axios from 'axios';
            import path from 'path'; // built-in
            
            const app = express();
            const router = Router();
        """.trimIndent()

        val artifact = ArtifactAgent.Artifact(
            identifier = "import-test",
            type = ArtifactAgent.Artifact.ArtifactType.NODEJS,
            title = "Import Test",
            content = codeWithImport
        )

        val bundle = ArtifactBundle.fromArtifact(artifact)

        assertTrue(bundle.dependencies.containsKey("express"), "Should detect express from import")
        assertTrue(bundle.dependencies.containsKey("axios"), "Should detect axios from import")
        assertTrue(!bundle.dependencies.containsKey("path"), "Should not include path (built-in)")
    }
}

