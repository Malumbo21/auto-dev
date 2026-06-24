package cc.unitmesh.devins.idea.mcp

import cc.unitmesh.agent.config.ToolConfigFile
import cc.unitmesh.agent.mcp.McpServerConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdeaNodeReplRuntimeTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `default node repl server is merged unless user already configured it`() {
        val defaultServer = McpServerConfig(command = "/plugin/node-repl/bin/node_repl")
        val otherServer = McpServerConfig(command = "other")

        val merged = IdeaNodeReplMcpServer.mergeWithDefault(
            ToolConfigFile(mcpServers = mapOf("filesystem" to otherServer)),
            defaultServer,
        )

        assertEquals(defaultServer, merged.mcpServers[IdeaNodeReplMcpServer.SERVER_NAME])
        assertEquals(otherServer, merged.mcpServers["filesystem"])

        val userServer = McpServerConfig(command = "/custom/node_repl", disabled = true)
        val userConfig = ToolConfigFile(mcpServers = mapOf(IdeaNodeReplMcpServer.SERVER_NAME to userServer))
        assertEquals(userConfig, IdeaNodeReplMcpServer.mergeWithDefault(userConfig, defaultServer))
    }

    @Test
    fun `generated node repl server is stripped before saving but user override is kept`() {
        val generated = McpServerConfig(
            command = "/plugin/node-repl/bin/node_repl",
            env = mapOf("NODE_REPL_NODE_PATH" to "/plugin/node-repl/vendor/node/darwin-arm64/bin/node"),
            trust = true,
            cwd = "/project",
        )
        val otherServer = McpServerConfig(command = "other")
        val stripped = IdeaNodeReplMcpServer.stripGeneratedDefault(
            ToolConfigFile(mcpServers = mapOf(IdeaNodeReplMcpServer.SERVER_NAME to generated, "other" to otherServer))
        )

        assertFalse(IdeaNodeReplMcpServer.SERVER_NAME in stripped.mcpServers)
        assertEquals(otherServer, stripped.mcpServers["other"])

        val disabledByUser = generated.copy(disabled = true)
        val userConfig = ToolConfigFile(mcpServers = mapOf(IdeaNodeReplMcpServer.SERVER_NAME to disabledByUser))
        assertEquals(userConfig, IdeaNodeReplMcpServer.stripGeneratedDefault(userConfig))
    }

    @Test
    fun `resolver uses explicit runtime root with packaged node and modules`() {
        val platform = NodeRuntimePlatform.current("Mac OS X", "aarch64")
        assertNotNull(platform)
        val root = createNodeReplRoot(platform, includeNode = true)

        val resolver = IdeaNodeReplRuntimeResolver(
            env = mapOf("AUTODEV_NODE_REPL_ROOT" to root.toString()),
            homeDir = tempDir.resolve("home"),
            platform = platform,
            installer = FailingInstaller,
        )

        val location = resolver.resolveBlocking()
        assertNotNull(location)

        val config = location.toMcpServerConfig("/project")
        assertEquals(root.resolve("bin/node_repl").toString(), config.command)
        assertEquals("/project", config.cwd)
        assertEquals(root.resolve("vendor/node/${platform.packagePlatformKey}/bin/node").toString(), config.env?.get("NODE_REPL_NODE_PATH"))

        val moduleDirs = config.env?.get("NODE_REPL_NODE_MODULE_DIRS")?.split(File.pathSeparator).orEmpty()
        assertTrue(root.resolve("vendor/node_modules").toString() in moduleDirs)
        assertTrue(root.resolve("vendor/node/${platform.packagePlatformKey}/lib/node_modules").toString() in moduleDirs)
    }

    @Test
    fun `resolver installs managed node when runtime root has server but no packaged node`() {
        val platform = NodeRuntimePlatform.current("Linux", "x86_64")
        assertNotNull(platform)
        val root = createNodeReplRoot(platform, includeNode = false)
        val installer = RecordingInstaller()

        val resolver = IdeaNodeReplRuntimeResolver(
            env = mapOf("AUTODEV_NODE_REPL_ROOT" to root.toString()),
            homeDir = tempDir.resolve("home"),
            platform = platform,
            installer = installer,
        )

        val location = resolver.resolveBlocking()
        assertNotNull(location)

        val expectedInstallRoot = tempDir
            .resolve("home/.cache/autodev-runtimes/node-repl-runtime/vendor/node/${platform.packagePlatformKey}")
        assertEquals(expectedInstallRoot, installer.installedTargets.single())
        assertEquals(expectedInstallRoot.resolve("bin/node"), location.nodePath)
    }

    @Test
    fun `platform keys match packaged runtime and official node archives`() {
        assertEquals(
            NodeRuntimePlatform("darwin-arm64", "darwin-arm64", false),
            NodeRuntimePlatform.current("Mac OS X", "aarch64"),
        )
        assertEquals(
            NodeRuntimePlatform("linux-x64", "linux-x64", false),
            NodeRuntimePlatform.current("Linux", "amd64"),
        )
        assertEquals(
            NodeRuntimePlatform("win32-x64", "win-x64", true),
            NodeRuntimePlatform.current("Windows 11", "x86_64"),
        )
        assertNull(NodeRuntimePlatform.current("Solaris", "sparc"))
    }

    @Test
    fun `official installer helpers parse checksum and reject unsafe archive entries`() {
        val installer = OfficialNodeRuntimeInstaller(nodeVersion = "24.14.0")
        val platform = NodeRuntimePlatform("darwin-arm64", "darwin-arm64", false)
        val archiveName = installer.archiveName(platform)

        assertEquals("node-v24.14.0-darwin-arm64.tar.xz", archiveName)
        assertEquals(
            "abc123",
            installer.expectedSha256("abc123  $archiveName\nbad  other.tar.xz", archiveName),
        )

        assertTrue(installer.isSafeArchiveEntry("node-v24/bin/node"))
        assertFalse(installer.isSafeArchiveEntry("/tmp/node"))
        assertFalse(installer.isSafeArchiveEntry("../node"))
        assertFalse(installer.isSafeArchiveEntry("node/../../escape"))
    }

    private fun createNodeReplRoot(platform: NodeRuntimePlatform, includeNode: Boolean): Path {
        val root = tempDir.resolve("runtime")
        val command = root.resolve("bin/node_repl")
        Files.createDirectories(command.parent)
        Files.writeString(command, "#!/usr/bin/env sh\n")
        command.toFile().setExecutable(true)

        val server = root.resolve("dist/jsMain/typescript/node-repl/server.js")
        Files.createDirectories(server.parent)
        Files.writeString(server, "console.log('node_repl')\n")

        Files.createDirectories(root.resolve("vendor/node_modules"))
        Files.createDirectories(root.resolve("vendor/node/${platform.packagePlatformKey}/lib/node_modules"))

        if (includeNode) {
            val node = root.resolve("vendor/node/${platform.packagePlatformKey}/bin/node")
            Files.createDirectories(node.parent)
            Files.writeString(node, "#!/usr/bin/env sh\n")
            node.toFile().setExecutable(true)
        }

        return root
    }

    private object FailingInstaller : NodeRuntimeInstaller {
        override fun installNode(targetRoot: Path, platform: NodeRuntimePlatform): Path {
            error("installer should not be called")
        }
    }

    private class RecordingInstaller : NodeRuntimeInstaller {
        val installedTargets = mutableListOf<Path>()

        override fun installNode(targetRoot: Path, platform: NodeRuntimePlatform): Path {
            installedTargets.add(targetRoot)
            val node = targetRoot.resolve("bin").resolve(if (platform.isWindows) "node.exe" else "node")
            Files.createDirectories(node.parent)
            Files.writeString(node, "#!/usr/bin/env sh\n")
            node.toFile().setExecutable(true)
            return node
        }
    }
}
