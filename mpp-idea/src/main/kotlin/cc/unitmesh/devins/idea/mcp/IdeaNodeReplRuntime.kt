package cc.unitmesh.devins.idea.mcp

import cc.unitmesh.agent.config.ToolConfigFile
import cc.unitmesh.agent.mcp.McpServerConfig
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.io.path.name

internal object IdeaNodeReplMcpServer {
    const val SERVER_NAME: String = "node_repl"

    private val logger = Logger.getInstance(IdeaNodeReplMcpServer::class.java)

    suspend fun merge(project: Project, toolConfig: ToolConfigFile): ToolConfigFile {
        if (toolConfig.mcpServers.containsKey(SERVER_NAME)) {
            return toolConfig
        }

        val runtime = IdeaNodeReplRuntimeResolver().resolve()
        if (runtime == null) {
            logger.warn("Built-in node_repl MCP runtime was not found")
            return toolConfig
        }

        return mergeWithDefault(toolConfig, runtime.toMcpServerConfig(project.basePath))
    }

    internal fun mergeWithDefault(
        toolConfig: ToolConfigFile,
        defaultServerConfig: McpServerConfig
    ): ToolConfigFile {
        if (toolConfig.mcpServers.containsKey(SERVER_NAME)) {
            return toolConfig
        }

        return toolConfig.copy(
            mcpServers = linkedMapOf<String, McpServerConfig>().apply {
                put(SERVER_NAME, defaultServerConfig)
                putAll(toolConfig.mcpServers)
            }
        )
    }

    internal fun stripGeneratedDefault(toolConfig: ToolConfigFile): ToolConfigFile {
        val server = toolConfig.mcpServers[SERVER_NAME] ?: return toolConfig
        if (!isGeneratedDefault(server)) {
            return toolConfig
        }

        return toolConfig.copy(mcpServers = toolConfig.mcpServers - SERVER_NAME)
    }

    internal fun isGeneratedDefault(server: McpServerConfig): Boolean {
        val command = server.command?.replace('\\', '/') ?: return false
        val hasBuiltInCommand = command.endsWith("/node-repl/bin/node_repl") ||
            command.endsWith("/node-repl/bin/autodev-node-repl") ||
            command.endsWith("/node-repl/bin/node_repl.cmd") ||
            command.endsWith("/node-repl/bin/autodev-node-repl.cmd")

        if (!hasBuiltInCommand) {
            return false
        }

        val allowedEnvKeys = setOf("NODE_REPL_NODE_PATH", "NODE_REPL_NODE_MODULE_DIRS")
        return server.url == null &&
            server.args.isEmpty() &&
            !server.disabled &&
            server.env.orEmpty().keys.all { it in allowedEnvKeys } &&
            server.autoApprove == null &&
            server.requiresConfirmation == null &&
            server.timeout == null &&
            server.trust &&
            server.headers == null
    }
}

internal class IdeaNodeReplRuntimeResolver(
    private val env: Map<String, String> = System.getenv(),
    private val homeDir: Path = Paths.get(System.getProperty("user.home")),
    private val platform: NodeRuntimePlatform? = NodeRuntimePlatform.current(),
    private val installer: NodeRuntimeInstaller = OfficialNodeRuntimeInstaller(),
) {
    suspend fun resolve(): NodeReplRuntimeLocation? = withContext(Dispatchers.IO) {
        resolveBlocking()
    }

    internal fun resolveBlocking(): NodeReplRuntimeLocation? {
        val platform = platform ?: return commandOverrideLocation()
        commandOverrideLocation()?.let { return it }

        val roots = candidateRuntimeRoots()
        for (root in roots) {
            runtimeFromRoot(root, platform)?.let { return it }
        }

        val installableRoot = roots.firstOrNull { hasNodeReplServer(it) } ?: return null
        return installNodeForRuntime(installableRoot, platform)
    }

    internal fun candidateRuntimeRoots(): List<Path> {
        return buildList {
            env["AUTODEV_NODE_REPL_ROOT"]?.takeIf { it.isNotBlank() }?.let {
                add(Paths.get(it))
            }
            pluginRuntimeRoot()?.let { add(it) }
            add(cacheRuntimeRoot())
        }.map { it.toAbsolutePath().normalize() }.distinct()
    }

    private fun commandOverrideLocation(): NodeReplRuntimeLocation? {
        val command = env["AUTODEV_NODE_REPL_COMMAND"]?.takeIf { it.isNotBlank() } ?: return null
        return NodeReplRuntimeLocation(command = Paths.get(command).toAbsolutePath().normalize())
    }

    private fun pluginRuntimeRoot(): Path? {
        val descriptor = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID)) ?: return null
        return descriptor.pluginPath.resolve("node-repl")
    }

    private fun cacheRuntimeRoot(): Path {
        return homeDir.resolve(".cache").resolve("autodev-runtimes").resolve("node-repl-runtime")
    }

    private fun runtimeFromRoot(root: Path, platform: NodeRuntimePlatform): NodeReplRuntimeLocation? {
        if (!hasNodeReplServer(root)) {
            return null
        }

        val command = nodeReplCommand(root) ?: return null
        val nodePath = packagedNodePath(root, platform).takeIf { Files.isExecutable(it) }
        if (nodePath == null && env["AUTODEV_NODE_REPL_ALLOW_SYSTEM_NODE"] != "1") {
            return null
        }
        val moduleDirs = nodeModuleDirs(root, platform)

        return NodeReplRuntimeLocation(
            command = command,
            nodePath = nodePath,
            nodeModuleDirs = moduleDirs,
        )
    }

    private fun hasNodeReplServer(root: Path): Boolean {
        return Files.isRegularFile(root.resolve("dist/jsMain/typescript/node-repl/server.js"))
    }

    private fun nodeReplCommand(root: Path): Path? {
        val candidates = listOf(
            root.resolve("bin/node_repl"),
            root.resolve("bin/autodev-node-repl"),
            root.resolve("bin/node_repl.cmd"),
            root.resolve("bin/autodev-node-repl.cmd"),
        )
        return candidates.firstOrNull { Files.isRegularFile(it) && (Files.isExecutable(it) || it.name.endsWith(".cmd")) }
    }

    private fun packagedNodePath(root: Path, platform: NodeRuntimePlatform): Path {
        val executable = if (platform.isWindows) "node.exe" else "node"
        return root.resolve("vendor/node/${platform.packagePlatformKey}/bin/$executable")
    }

    private fun nodeModuleDirs(root: Path, platform: NodeRuntimePlatform): List<Path> {
        return listOf(
            root.resolve("vendor/node_modules"),
            root.resolve("vendor/node/${platform.packagePlatformKey}/lib/node_modules"),
        ).filter { Files.isDirectory(it) }
    }

    private fun installNodeForRuntime(root: Path, platform: NodeRuntimePlatform): NodeReplRuntimeLocation? {
        if (env["AUTODEV_NODE_REPL_SKIP_INSTALL"] == "1") {
            return null
        }

        val command = nodeReplCommand(root) ?: return null
        val targetNodeRoot = cacheRuntimeRoot().resolve("vendor/node/${platform.packagePlatformKey}")

        val nodePath = try {
            installer.installNode(targetNodeRoot, platform)
        } catch (e: Exception) {
            Logger.getInstance(IdeaNodeReplRuntimeResolver::class.java)
                .warn("Failed to install Node.js for node_repl MCP runtime: ${e.message}")
            return null
        }

        return NodeReplRuntimeLocation(
            command = command,
            nodePath = nodePath,
            nodeModuleDirs = nodeModuleDirs(root, platform) + listOfNotNull(
                targetNodeRoot.resolve("lib/node_modules").takeIf { Files.isDirectory(it) }
            ),
        )
    }

    private companion object {
        private const val PLUGIN_ID = "cc.unitmesh.devins.idea"
    }
}

internal data class NodeReplRuntimeLocation(
    val command: Path,
    val nodePath: Path? = null,
    val nodeModuleDirs: List<Path> = emptyList(),
) {
    fun toMcpServerConfig(projectBasePath: String?): McpServerConfig {
        val env = linkedMapOf<String, String>()
        nodePath?.let { env["NODE_REPL_NODE_PATH"] = it.toString() }
        if (nodeModuleDirs.isNotEmpty()) {
            env["NODE_REPL_NODE_MODULE_DIRS"] = nodeModuleDirs.joinToString(File.pathSeparator) { it.toString() }
        }

        return McpServerConfig(
            command = command.toString(),
            env = env.takeIf { it.isNotEmpty() },
            trust = true,
            cwd = projectBasePath ?: System.getProperty("user.home"),
        )
    }
}

internal data class NodeRuntimePlatform(
    val packagePlatformKey: String,
    val nodeArchivePlatform: String,
    val isWindows: Boolean,
) {
    companion object {
        fun current(
            osName: String = System.getProperty("os.name"),
            osArch: String = System.getProperty("os.arch")
        ): NodeRuntimePlatform? {
            val os = osName.lowercase(Locale.US)
            val arch = osArch.lowercase(Locale.US)
            val normalizedArch = when (arch) {
                "aarch64", "arm64" -> "arm64"
                "x86_64", "amd64" -> "x64"
                else -> return null
            }

            return when {
                os.contains("mac") || os.contains("darwin") -> NodeRuntimePlatform(
                    packagePlatformKey = "darwin-$normalizedArch",
                    nodeArchivePlatform = "darwin-$normalizedArch",
                    isWindows = false,
                )
                os.contains("linux") -> NodeRuntimePlatform(
                    packagePlatformKey = "linux-$normalizedArch",
                    nodeArchivePlatform = "linux-$normalizedArch",
                    isWindows = false,
                )
                os.contains("windows") -> NodeRuntimePlatform(
                    packagePlatformKey = "win32-$normalizedArch",
                    nodeArchivePlatform = "win-$normalizedArch",
                    isWindows = true,
                )
                else -> null
            }
        }
    }
}

internal interface NodeRuntimeInstaller {
    fun installNode(targetRoot: Path, platform: NodeRuntimePlatform): Path
}

internal class OfficialNodeRuntimeInstaller(
    private val nodeVersion: String = "24.14.0",
    private val nodeBaseUri: URI = URI("https://nodejs.org/dist/"),
) : NodeRuntimeInstaller {
    override fun installNode(targetRoot: Path, platform: NodeRuntimePlatform): Path {
        val nodeExecutable = targetRoot.resolve("bin").resolve(if (platform.isWindows) "node.exe" else "node")
        if (Files.isExecutable(nodeExecutable)) {
            return nodeExecutable
        }

        val archiveName = archiveName(platform)
        val versionDir = "v$nodeVersion"
        val downloadBase = nodeBaseUri.resolve("$versionDir/")
        Files.createDirectories(targetRoot.parent)
        val tempRoot = Files.createTempDirectory(targetRoot.parent, "node-install-")

        try {
            val archivePath = tempRoot.resolve(archiveName)
            val checksums = readUri(downloadBase.resolve("SHASUMS256.txt"))
            val expectedSha256 = expectedSha256(checksums, archiveName)
                ?: error("Could not find SHA256 for $archiveName")

            download(downloadBase.resolve(archiveName), archivePath)
            val actualSha256 = sha256(archivePath)
            check(actualSha256.equals(expectedSha256, ignoreCase = true)) {
                "SHA256 mismatch for $archiveName"
            }

            val extractRoot = tempRoot.resolve("extract")
            Files.createDirectories(extractRoot)
            extractArchive(archivePath, extractRoot, platform)
            val extractedNodeRoot = extractRoot.resolve("node-v$nodeVersion-${platform.nodeArchivePlatform}")
            check(Files.isDirectory(extractedNodeRoot)) {
                "Node.js archive did not contain $extractedNodeRoot"
            }

            replaceDirectory(extractedNodeRoot, targetRoot)
            return nodeExecutable
        } finally {
            tempRoot.toFile().deleteRecursively()
        }
    }

    internal fun archiveName(platform: NodeRuntimePlatform): String {
        val extension = if (platform.isWindows) "zip" else "tar.xz"
        return "node-v$nodeVersion-${platform.nodeArchivePlatform}.$extension"
    }

    internal fun expectedSha256(checksums: String, archiveName: String): String? {
        return checksums.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .firstNotNullOfOrNull { line ->
                val parts = line.split(Regex("\\s+"), limit = 2)
                if (parts.size == 2 && parts[1] == archiveName) parts[0] else null
            }
    }

    internal fun isSafeArchiveEntry(entryName: String): Boolean {
        if (entryName.isBlank()) {
            return false
        }
        val normalized = entryName.replace('\\', '/')
        if (normalized.startsWith("/") || normalized.contains('\u0000')) {
            return false
        }
        val normalizedPath = Paths.get(normalized).normalize()
        return !normalizedPath.isAbsolute && !normalizedPath.startsWith("..")
    }

    private fun readUri(uri: URI): String {
        return uri.toURL().openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 60_000
        }.getInputStream().bufferedReader().use { it.readText() }
    }

    private fun download(uri: URI, target: Path) {
        uri.toURL().openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 120_000
        }.getInputStream().use { input ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun extractArchive(archivePath: Path, extractRoot: Path, platform: NodeRuntimePlatform) {
        if (platform.isWindows) {
            extractZip(archivePath, extractRoot)
            return
        }

        val entries = runProcess(listOf("tar", "-tf", archivePath.toString()), archivePath.parent)
            .lineSequence()
            .filter { it.isNotBlank() }
            .toList()
        check(entries.all(::isSafeArchiveEntry)) {
            "Node.js archive contains unsafe paths"
        }

        runProcess(listOf("tar", "-xf", archivePath.toString(), "-C", extractRoot.toString()), archivePath.parent)
    }

    private fun extractZip(archivePath: Path, extractRoot: Path) {
        java.util.zip.ZipInputStream(Files.newInputStream(archivePath)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                check(isSafeArchiveEntry(entry.name)) {
                    "Node.js archive contains unsafe paths"
                }

                val target = extractRoot.resolve(entry.name).normalize()
                check(target.startsWith(extractRoot)) {
                    "Node.js archive contains unsafe paths"
                }

                if (entry.isDirectory) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING)
                }
                zip.closeEntry()
            }
        }
    }

    private fun replaceDirectory(source: Path, target: Path) {
        Files.createDirectories(target.parent)
        val backup = target.resolveSibling("${target.fileName}.previous")
        if (Files.exists(backup)) {
            backup.toFile().deleteRecursively()
        }

        if (Files.exists(target)) {
            Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING)
        }

        try {
            moveDirectory(source, target)
            if (Files.exists(backup)) {
                backup.toFile().deleteRecursively()
            }
        } catch (e: Exception) {
            if (Files.exists(target)) {
                target.toFile().deleteRecursively()
            }
            if (Files.exists(backup)) {
                Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING)
            }
            throw e
        }
    }

    private fun moveDirectory(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun runProcess(command: List<String>, workingDir: Path): String {
        val process = ProcessBuilder(command)
            .directory(workingDir.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val completed = process.waitFor(5, TimeUnit.MINUTES)
        check(completed && process.exitValue() == 0) {
            "${command.joinToString(" ")} failed: $output"
        }
        return output
    }
}
