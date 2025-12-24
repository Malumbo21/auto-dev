package cc.unitmesh.agent.artifact

import cc.unitmesh.agent.ArtifactAgent
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Simple utility to generate a test .unit file for verification
 * Run with: ./gradlew :mpp-core:generateTestUnit
 */
fun main(args: Array<String>) {
    runBlocking {
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
}
