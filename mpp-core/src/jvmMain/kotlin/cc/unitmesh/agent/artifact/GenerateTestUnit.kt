package cc.unitmesh.agent.artifact

import cc.unitmesh.agent.ArtifactAgent
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Simple utility to generate test .unit files for verification
 * Run with: ./gradlew :mpp-core:generateTestUnit
 * 
 * Usage:
 *   - No args: Creates HTML demo
 *   - "express": Creates Express.js Node.js application
 */
fun main(args: Array<String>) {
    runBlocking {
        val artifactType = args.getOrNull(0) ?: "html"
        
        when (artifactType.lowercase()) {
            "express", "nodejs", "node" -> {
                createExpressJsUnit()
            }
            else -> {
                createHtmlDemo()
            }
        }
    }
}

private suspend fun createHtmlDemo() {
    val artifact = ArtifactAgent.Artifact(
        identifier = "demo-artifact",
        type = ArtifactAgent.Artifact.ArtifactType.HTML,
        title = "Demo HTML Page",
        content = """<!DOCTYPE html>
<html>
<head>
    <title>AutoDev Unit Demo</title>
    <style>
        body { font-family: sans-serif; padding: 2rem; }
        h1 { color: #2563eb; }
    </style>
</head>
<body>
    <h1>Hello AutoDev Unit!</h1>
    <p>This is a demo artifact bundled as a .unit file.</p>
</body>
</html>"""
    )

    val bundle = ArtifactBundle.fromArtifact(artifact)
    val outputPath = "/tmp/demo.unit"

    val packer = ArtifactBundlePacker()
    val result = packer.pack(bundle, outputPath)

    when (result) {
        is PackResult.Success -> {
            val file = File(result.outputPath)
            println("✅ Successfully created: ${result.outputPath}")
            println("   File size: ${file.length()} bytes")
            println()
            println("To verify with unzip:")
            println("   unzip -l ${result.outputPath}")
            println()
            println("To extract:")
            println("   unzip ${result.outputPath} -d /tmp/demo-extracted")
        }
        is PackResult.Error -> {
            println("❌ Error: ${result.message}")
            result.cause?.printStackTrace()
        }
    }
}

private suspend fun createExpressJsUnit() {
    println("Creating Express.js test .unit file...")

    // Create Express.js application code
    val expressAppCode = """
import express from 'express';

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());

app.get('/', (req, res) => {
    res.json({
        message: 'Hello from Express.js!',
        timestamp: new Date().toISOString(),
        version: '1.0.0'
    });
});

app.get('/api/health', (req, res) => {
    res.json({ status: 'ok', uptime: process.uptime() });
});

app.post('/api/echo', (req, res) => {
    res.json({
        received: req.body,
        timestamp: new Date().toISOString()
    });
});

app.listen(PORT, () => {
    console.log(`🚀 Express.js server running on http://localhost:${'$'}{PORT}`);
    console.log(`📡 Health check: http://localhost:${'$'}{PORT}/api/health`);
    console.log(`📝 Echo endpoint: POST http://localhost:${'$'}{PORT}/api/echo`);
});
""".trimIndent()

    // Create bundle with Express.js artifact
    val artifact = ArtifactAgent.Artifact(
        identifier = "express-test-app",
        type = ArtifactAgent.Artifact.ArtifactType.NODEJS,
        title = "Express.js Test Application",
        content = expressAppCode
    )

    val bundle = ArtifactBundle.fromArtifact(
        artifact = artifact,
        conversationHistory = emptyList(),
        modelInfo = null
    ).copy(
        dependencies = mapOf("express" to "^4.18.2")
    )

    val outputPath = "/tmp/express-test-app.unit"
    val packer = ArtifactBundlePacker()
    
    when (val result = packer.pack(bundle, outputPath)) {
        is PackResult.Success -> {
            val file = File(result.outputPath)
            println("✅ Created Express.js .unit file: ${result.outputPath}")
            println("   File size: ${file.length()} bytes")
            println()
            println("📦 Bundle contains:")
            println("   - index.js (Express.js application)")
            println("   - package.json (with express dependency)")
            println("   - ARTIFACT.md (metadata)")
            println("   - .artifact/context.json (context)")
            println()
            println("To test execution:")
            println("   1. Load the .unit file in AutoDev")
            println("   2. Click the play button to execute")
            println("   3. The server will start on http://localhost:3000")
            println()
            println("To test manually:")
            println("   unzip ${result.outputPath} -d /tmp/express-extracted")
            println("   cd /tmp/express-extracted")
            println("   npm install")
            println("   node index.js")
        }
        is PackResult.Error -> {
            println("❌ Failed to create .unit file: ${result.message}")
            result.cause?.printStackTrace()
        }
    }
}
