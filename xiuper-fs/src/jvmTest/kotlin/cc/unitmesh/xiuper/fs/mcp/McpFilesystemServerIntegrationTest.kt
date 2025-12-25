package cc.unitmesh.xiuper.fs.mcp

import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class McpFilesystemServerIntegrationTest {

    @Test
    fun `server-filesystem works with JvmMcpBackend`() = kotlinx.coroutines.test.runTest {
        if (System.getenv("RUN_MCP_INTEGRATION_TESTS") != "true") {
            println("Skipping MCP integration test (set RUN_MCP_INTEGRATION_TESTS=true to enable)")
            return@runTest
        }

        val hasNpx = withContext(Dispatchers.IO) {
            runCatching { ProcessBuilder("npx", "--version").start().waitFor() == 0 }.getOrDefault(false)
        }
        if (!hasNpx) {
            println("Skipping MCP integration test (npx not available)")
            return@runTest
        }

        val tmpDir = Files.createTempDirectory("xiuper-mcp-fs-")
        val allowedRoot = tmpDir.resolve("allowed").createDirectories()
        val helloFile = allowedRoot.resolve("hello.txt")
        helloFile.writeText("hello from mcp")

        val process = withContext(Dispatchers.IO) {
            ProcessBuilder(
                "npx",
                "-y",
                "@modelcontextprotocol/server-filesystem",
                allowedRoot.absolutePathString(),
            ).redirectErrorStream(true).start()
        }

        val client = Client(clientInfo = Implementation(name = "XiuperFs-MCP-Test", version = "1.0.0"))

        try {
            withTimeout(120_000) {
                withContext(Dispatchers.IO) {
                    val input = process.inputStream.asSource().buffered()
                    val output = process.outputStream.asSink().buffered()
                    val transport = StdioClientTransport(input, output)

                    client.connect(transport)

                    val backend = JvmMcpBackend(client)

                    // Validate tools are discoverable
                    val toolsDir = backend.list(cc.unitmesh.xiuper.fs.FsPath.of("/tools"))
                    assertTrue(toolsDir.isNotEmpty(), "Expected MCP server to expose tools")

                    // Validate we can execute list_directory
                    backend.write(
                        cc.unitmesh.xiuper.fs.FsPath.of("/tools/list_directory/args"),
                        buildJsonObject {
                            put("path", allowedRoot.absolutePathString())
                        }.toString().encodeToByteArray(),
                        cc.unitmesh.xiuper.fs.WriteOptions()
                    )
                    val listResult = backend.write(
                        cc.unitmesh.xiuper.fs.FsPath.of("/tools/list_directory/run"),
                        ByteArray(0),
                        cc.unitmesh.xiuper.fs.WriteOptions()
                    )
                    assertTrue(listResult.ok, "list_directory should succeed")

                    // Validate we can execute read_file and get content back
                    backend.write(
                        cc.unitmesh.xiuper.fs.FsPath.of("/tools/read_file/args"),
                        buildJsonObject {
                            put("path", helloFile.absolutePathString())
                        }.toString().encodeToByteArray(),
                        cc.unitmesh.xiuper.fs.WriteOptions()
                    )
                    val readResult = backend.write(
                        cc.unitmesh.xiuper.fs.FsPath.of("/tools/read_file/run"),
                        ByteArray(0),
                        cc.unitmesh.xiuper.fs.WriteOptions()
                    )
                    assertTrue(readResult.ok, "read_file should succeed")
                    assertNotNull(readResult.message)
                }
            }
        } finally {
            runCatching { withContext(Dispatchers.IO) { client.close() } }
            runCatching { withContext(Dispatchers.IO) { process.destroy() } }
            runCatching {
                withContext(Dispatchers.IO) {
                    if (process.isAlive) process.destroyForcibly()
                }
            }
        }
    }
}
