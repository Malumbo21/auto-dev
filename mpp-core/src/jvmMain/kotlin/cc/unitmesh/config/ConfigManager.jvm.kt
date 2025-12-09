package cc.unitmesh.config

import cc.unitmesh.agent.config.ToolConfigFile
import cc.unitmesh.agent.mcp.McpServerConfig
import cc.unitmesh.llm.NamedModelConfig
import cc.unitmesh.yaml.YamlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * JVM implementation of ConfigManager
 * Uses java.io.File for file operations
 * 
 * Note: Uses mutex to prevent concurrent access to config files,
 * which can cause YAML corruption like "issueTracker: nullTracker: null"
 */
actual object ConfigManager {
    private val homeDir = System.getProperty("user.home")
    private val configDir = File(homeDir, ".autodev")
    private val configFile = File(configDir, "config.yaml")
    private val toolConfigFile = File(configDir, "mcp.json")
    
    // Mutex to prevent concurrent read-modify-write race conditions
    private val configMutex = Mutex()
    private val toolConfigMutex = Mutex()

    // Simple YAML serializer (we'll use a simple format that's both JSON and YAML compatible)
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

    actual suspend fun load(): AutoDevConfigWrapper =
        withContext(Dispatchers.IO) {
            configMutex.withLock {
                try {
                    if (!configFile.exists()) {
                        return@withLock createEmpty()
                    }

                    val content = configFile.readText()

                    // Parse YAML-like content (simplified parser)
                    val configFileData = parseYamlConfig(content)

                    AutoDevConfigWrapper(configFileData)
                } catch (e: Exception) {
                    println("Error loading config: ${e.message}")
                    createEmpty()
                }
            }
        }

    actual suspend fun save(configFile: ConfigFile) =
        withContext(Dispatchers.IO) {
            configMutex.withLock {
                try {
                    // Ensure directory exists
                    configDir.mkdirs()

                    // Convert to YAML format
                    val yamlContent = toYaml(configFile)
                    this@ConfigManager.configFile.writeText(yamlContent)
                } catch (e: Exception) {
                    println("Error saving config: ${e.message}")
                    throw e
                }
            }
        }

    actual suspend fun saveConfig(
        config: NamedModelConfig,
        setActive: Boolean
    ) {
        // Use internal load/save to avoid nested mutex locks
        withContext(Dispatchers.IO) {
            configMutex.withLock {
                val wrapper = loadInternal()
                val configFileData = wrapper.configFile

                val existingIndex = configFileData.configs.indexOfFirst { it.name == config.name }

                val updatedConfigs =
                    if (existingIndex >= 0) {
                        configFileData.configs.toMutableList().apply { set(existingIndex, config) }
                    } else {
                        configFileData.configs + config
                    }

                val updatedConfigFile =
                    configFileData.copy(
                        active = if (setActive) config.name else configFileData.active,
                        configs = updatedConfigs
                    )

                saveInternal(updatedConfigFile)
            }
        }
    }

    /**
     * Generate a unique configuration name by appending -1, -2, etc. if the name already exists
     *
     * @param baseName The desired configuration name
     * @param existingNames List of existing configuration names
     * @return A unique name (either baseName or baseName-1, baseName-2, etc.)
     */
    actual fun generateUniqueConfigName(baseName: String, existingNames: List<String>): String {
        if (baseName !in existingNames) {
            return baseName
        }

        var counter = 1
        var uniqueName = "$baseName-$counter"
        while (uniqueName in existingNames) {
            counter++
            uniqueName = "$baseName-$counter"
        }
        return uniqueName
    }

    actual suspend fun deleteConfig(name: String) {
        withContext(Dispatchers.IO) {
            configMutex.withLock {
                val wrapper = loadInternal()
                val configFileData = wrapper.configFile

                val updatedConfigs = configFileData.configs.filter { it.name != name }
                val updatedActive =
                    if (configFileData.active == name && updatedConfigs.isNotEmpty()) {
                        updatedConfigs.first().name
                    } else {
                        configFileData.active
                    }

                saveInternal(configFileData.copy(active = updatedActive, configs = updatedConfigs))
            }
        }
    }

    actual suspend fun setActive(name: String) {
        withContext(Dispatchers.IO) {
            configMutex.withLock {
                val wrapper = loadInternal()
                val configFileData = wrapper.configFile

                if (configFileData.configs.none { it.name == name }) {
                    throw IllegalArgumentException("Configuration '$name' not found")
                }

                saveInternal(configFileData.copy(active = name))
            }
        }
    }

    actual fun getConfigPath(): String = configFile.absolutePath

    actual suspend fun exists(): Boolean =
        withContext(Dispatchers.IO) {
            configFile.exists()
        }

    actual suspend fun saveMcpServers(mcpServers: Map<String, McpServerConfig>) {
        withContext(Dispatchers.IO) {
            configMutex.withLock {
                val wrapper = loadInternal()
                val configFileData = wrapper.configFile

                val updatedConfigFile = configFileData.copy(mcpServers = mcpServers)
                saveInternal(updatedConfigFile)
            }
        }
    }
    
    actual suspend fun saveRemoteServer(remoteServer: RemoteServerConfig) {
        withContext(Dispatchers.IO) {
            configMutex.withLock {
                val wrapper = loadInternal()
                val configFileData = wrapper.configFile

                val updatedConfigFile = configFileData.copy(remoteServer = remoteServer)
                saveInternal(updatedConfigFile)
            }
        }
    }

    actual suspend fun loadToolConfig(): ToolConfigFile =
        withContext(Dispatchers.IO) {
            toolConfigMutex.withLock {
                try {
                    if (!toolConfigFile.exists()) {
                        return@withLock ToolConfigFile.default()
                    }

                    val content = toolConfigFile.readText()
                    json.decodeFromString<ToolConfigFile>(content)
                } catch (e: Exception) {
                    println("Error loading tool config: ${e.message}")
                    ToolConfigFile.default()
                }
            }
        }

    actual suspend fun saveToolConfig(toolConfig: ToolConfigFile) =
        withContext(Dispatchers.IO) {
            toolConfigMutex.withLock {
                try {
                    // Ensure directory exists
                    configDir.mkdirs()

                    // Write JSON
                    val jsonContent = json.encodeToString(ToolConfigFile.serializer(), toolConfig)
                    toolConfigFile.writeText(jsonContent)
                } catch (e: Exception) {
                    println("Error saving tool config: ${e.message}")
                    throw e
                }
            }
        }

    actual fun getToolConfigPath(): String = toolConfigFile.absolutePath

    private fun createEmpty(): AutoDevConfigWrapper {
        return AutoDevConfigWrapper(ConfigFile(active = "", configs = emptyList()))
    }
    
    /**
     * Internal load without mutex - must be called from within configMutex.withLock
     */
    private fun loadInternal(): AutoDevConfigWrapper {
        try {
            if (!configFile.exists()) {
                return createEmpty()
            }

            val content = configFile.readText()
            val configFileData = parseYamlConfig(content)
            return AutoDevConfigWrapper(configFileData)
        } catch (e: Exception) {
            println("Error loading config: ${e.message}")
            return createEmpty()
        }
    }
    
    /**
     * Internal save without mutex - must be called from within configMutex.withLock
     */
    private fun saveInternal(configFileData: ConfigFile) {
        try {
            // Ensure directory exists
            configDir.mkdirs()

            // Convert to YAML format
            val yamlContent = toYaml(configFileData)
            this@ConfigManager.configFile.writeText(yamlContent)
        } catch (e: Exception) {
            println("Error saving config: ${e.message}")
            throw e
        }
    }

    /**
     * Parse YAML config file using YamlUtils
     */
    private fun parseYamlConfig(content: String): ConfigFile {
        // Try to parse as JSON first (for MCP config compatibility)
        try {
            if (content.trim().startsWith("{")) {
                return json.decodeFromString<ConfigFile>(content)
            }
        } catch (e: Exception) {
            // Fall through to YAML parsing
        }

        // Use YamlUtils for proper YAML parsing
        return YamlUtils.loadAs<ConfigFile>(content, kotlinx.serialization.serializer())
    }

    private fun toYaml(configFile: ConfigFile): String {
        return YamlUtils.dump(configFile, kotlinx.serialization.serializer())
    }
    
    actual suspend fun saveLastWorkspace(name: String, path: String) {
        withContext(Dispatchers.IO) {
            configMutex.withLock {
                val wrapper = loadInternal()
                val configFileData = wrapper.configFile
                
                val updatedConfigFile = configFileData.copy(
                    lastWorkspace = WorkspaceInfo(name = name, path = path)
                )
                saveInternal(updatedConfigFile)
            }
        }
    }
    
    actual suspend fun getLastWorkspace(): WorkspaceInfo? {
        return withContext(Dispatchers.IO) {
            configMutex.withLock {
                val wrapper = loadInternal()
                wrapper.getLastWorkspace()
            }
        }
    }
    
    actual suspend fun saveIssueTracker(issueTracker: IssueTrackerConfig) {
        withContext(Dispatchers.IO) {
            configMutex.withLock {
                val wrapper = loadInternal()
                val configFileData = wrapper.configFile
                
                val updatedConfigFile = configFileData.copy(issueTracker = issueTracker)
                saveInternal(updatedConfigFile)
            }
        }
    }
    
    actual suspend fun getIssueTracker(): IssueTrackerConfig {
        return withContext(Dispatchers.IO) {
            configMutex.withLock {
                val wrapper = loadInternal()
                wrapper.getIssueTracker()
            }
        }
    }
    
    actual fun getKcefInstallDir(): String {
        val kcefDir = File(configDir, "kcef-bundle")
        return kcefDir.absolutePath
    }
}

