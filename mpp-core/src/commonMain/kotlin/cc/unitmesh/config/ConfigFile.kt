package cc.unitmesh.config

import cc.unitmesh.agent.mcp.McpServerConfig
import cc.unitmesh.agent.AgentType
import cc.unitmesh.llm.ModelConfig
import cc.unitmesh.llm.NamedModelConfig
import kotlinx.serialization.Serializable

/**
 * Root configuration file structure
 *
 * Maps to ~/.autodev/config.yaml format:
 * ```yaml
 * active: default
 * configs:
 *   - name: default
 *     provider: openai
 *     apiKey: sk-...
 *     model: gpt-4
 *   - name: work
 *     provider: anthropic
 *     apiKey: sk-ant-...
 *     model: claude-3-opus
 * mcpServers:
 *   filesystem:
 *     command: npx
 *     args: ["-y", "@modelcontextprotocol/server-filesystem", "/path/to/allowed/files"]
 * remoteServer:
 *   url: "http://localhost:8080"
 *   enabled: false
 *   useServerConfig: false
 * acpAgents:
 *   kimi:
 *     name: "Kimi CLI"
 *     command: "kimi"
 *     args: "--acp"
 *     env: ""
 *   claude:
 *     name: "Claude Code"
 *     command: "claude"
 *     args: "-p --output-format stream-json --input-format stream-json"
 *     env: ""
 *   auggie:
 *     name: "Auggie"
 *     command: "auggie"
 *     args: "--acp"
 *     env: "AUGGIE_API_KEY=xxx"
 * activeAcpAgent: auggie
 * ```
 */
@Serializable
public data class ConfigFile(
    val active: String = "",
    val configs: List<NamedModelConfig> = emptyList(),
    val mcpServers: Map<String, McpServerConfig>? = emptyMap(),
    val language: String? = "en", // Language preference: "en" or "zh"
    val remoteServer: RemoteServerConfig? = null,
    val agentType: String? = "Local", // "Local" or "Remote" - which agent mode to use
    val lastWorkspace: WorkspaceInfo? = null, // Last opened workspace information
    val issueTracker: IssueTrackerConfig? = null, // Issue tracker configuration
    val cloudStorage: CloudStorageConfig? = null, // Cloud storage for multimodal image upload
    val acpAgents: Map<String, AcpAgentConfig>? = null, // ACP agent configurations
    val activeAcpAgent: String? = null // Currently active ACP agent key
)

/**
 * Remote server configuration
 */
@Serializable
data class RemoteServerConfig(
    val url: String = "http://localhost:8080",
    val enabled: Boolean = false,
    val useServerConfig: Boolean = false // Whether to use server's LLM config instead of local
)

/**
 * Last opened workspace information
 */
@Serializable
data class WorkspaceInfo(
    val name: String,
    val path: String
)

/**
 * Issue tracker configuration
 * Supports GitHub, GitLab, Jira, etc.
 */
@Serializable
data class IssueTrackerConfig(
    val type: String = "github", // "github", "gitlab", "jira", etc.
    val token: String = "",
    val repoOwner: String = "", // For GitHub/GitLab
    val repoName: String = "",  // For GitHub/GitLab
    val serverUrl: String = "",  // For GitLab/Jira (e.g., "https://gitlab.com", "https://jira.company.com")
    val enabled: Boolean = false
) {
    fun isConfigured(): Boolean {
        return when (type.lowercase()) {
            "github" -> token.isNotBlank() && repoOwner.isNotBlank() && repoName.isNotBlank()
            "gitlab" -> token.isNotBlank() && repoOwner.isNotBlank() && repoName.isNotBlank() && serverUrl.isNotBlank()
            "jira" -> token.isNotBlank() && serverUrl.isNotBlank()
            else -> false
        }
    }
}

/**
 * Cloud storage configuration for multimodal image upload.
 * Currently supports Tencent COS (Cloud Object Storage).
 * 
 * Example config.yaml:
 * ```yaml
 * cloudStorage:
 *   provider: tencent-cos
 *   secretId: AKIDxxxxxxxxxx
 *   secretKey: xxxxxxxxxx
 *   bucket: autodev-images-1234567890
 *   region: ap-beijing
 * ```
 */
@Serializable
data class CloudStorageConfig(
    val provider: String = "tencent-cos", // Currently only "tencent-cos" is supported
    val secretId: String = "",
    val secretKey: String = "",
    val bucket: String = "",
    val region: String = "ap-beijing",
    val enabled: Boolean = true
) {
    fun isConfigured(): Boolean {
        return provider == "tencent-cos" &&
            secretId.isNotBlank() &&
            secretKey.isNotBlank() &&
            bucket.isNotBlank() &&
            region.isNotBlank()
    }
}

/**
 * ACP (Agent Client Protocol) agent configuration.
 *
 * Defines an external ACP-compliant agent that can be spawned as a child process
 * and communicated with via JSON-RPC over stdio.
 *
 * Supported agents:
 * - **Kimi CLI**: Chinese AI agent with strong coding capabilities
 * - **Claude CLI**: Anthropic's Claude Code agent
 * - **Auggie**: Augment Code's AI agent with ACP support
 * - **Gemini CLI**: Google's Gemini agent (when available)
 *
 * Example config.yaml:
 * ```yaml
 * acpAgents:
 *   kimi:
 *     name: "Kimi CLI"
 *     command: "kimi"
 *     args: "--acp"
 *     env: "KIMI_API_KEY=xxx"
 *   claude:
 *     name: "Claude Code"
 *     command: "claude"
 *     args: "-p --output-format stream-json --input-format stream-json"
 *     env: ""
 *   auggie:
 *     name: "Auggie"
 *     command: "auggie"
 *     args: "--acp"
 *     env: "AUGGIE_API_KEY=xxx"
 * activeAcpAgent: auggie
 * ```
 */
@Serializable
data class AcpAgentConfig(
    val name: String = "",
    val command: String = "",
    val args: String = "", // Space-separated arguments (e.g., "--acp --verbose")
    val env: String = "" // Environment variables, one per line: "KEY=VALUE"
) {
    fun isConfigured(): Boolean {
        return command.isNotBlank()
    }

    fun getArgsList(): List<String> {
        return args.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
    }

    fun getEnvMap(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        env.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val idx = trimmed.indexOf('=')
            if (idx <= 0) return@forEach
            val key = trimmed.substring(0, idx).trim()
            val value = trimmed.substring(idx + 1).trim()
            result[key] = value
        }
        return result
    }
}

/**
 * Configuration file wrapper with utility methods
 */
class AutoDevConfigWrapper(val configFile: ConfigFile) {
    fun getActiveConfig(): NamedModelConfig? {
        if (configFile.active.isEmpty() || configFile.configs.isEmpty()) {
            return null
        }

        return configFile.configs.find { it.name == configFile.active }
            ?: configFile.configs.firstOrNull()
    }

    fun getAllConfigs(): List<NamedModelConfig> = configFile.configs

    fun getActiveName(): String = configFile.active

    fun isValid(): Boolean {
        val active = getActiveConfig() ?: return false

        if (active.provider.equals("ollama", ignoreCase = true)) {
            return active.model.isNotEmpty()
        }

        return active.provider.isNotEmpty() &&
            active.apiKey.isNotEmpty() &&
            active.model.isNotEmpty()
    }

    fun getActiveModelConfig(): ModelConfig? {
        return getActiveConfig()?.toModelConfig()
    }

    /**
     * Get a model config by provider name (case-insensitive).
     * Useful for getting config for a specific provider like GLM or QWEN.
     */
    fun getModelConfigByProvider(providerName: String): ModelConfig? {
        return configFile.configs
            .find { it.provider.equals(providerName, ignoreCase = true) }
            ?.toModelConfig()
    }

    /**
     * Get all configs for a specific provider.
     */
    fun getConfigsByProvider(providerName: String): List<NamedModelConfig> {
        return configFile.configs.filter { 
            it.provider.equals(providerName, ignoreCase = true) 
        }
    }

    fun getMcpServers(): Map<String, McpServerConfig> {
        return configFile.mcpServers ?: emptyMap()
    }

    fun getEnabledMcpServers(): Map<String, McpServerConfig> {
        return configFile.mcpServers?.filter { !it.value.disabled && it.value.validate() } ?: emptyMap()
    }

    fun getRemoteServer(): RemoteServerConfig {
        return configFile.remoteServer ?: RemoteServerConfig()
    }

    fun isRemoteMode(): Boolean {
        return configFile.remoteServer?.enabled == true
    }

    fun getAgentType(): AgentType {
        return AgentType.fromString(configFile.agentType ?: "Local")
    }

    fun getLastWorkspace(): WorkspaceInfo? {
        return configFile.lastWorkspace
    }
    
    fun getIssueTracker(): IssueTrackerConfig {
        return configFile.issueTracker ?: IssueTrackerConfig()
    }

    fun getLanguage(): String? {
        return configFile.language.takeIf { it?.isNotEmpty() == true }
    }
    
    fun getCloudStorage(): CloudStorageConfig {
        return configFile.cloudStorage ?: CloudStorageConfig()
    }

    fun isCloudStorageConfigured(): Boolean {
        return configFile.cloudStorage?.isConfigured() == true
    }

    fun getAcpAgents(): Map<String, AcpAgentConfig> {
        return configFile.acpAgents ?: emptyMap()
    }

    fun getActiveAcpAgent(): AcpAgentConfig? {
        val key = configFile.activeAcpAgent ?: return null
        return configFile.acpAgents?.get(key)
    }

    fun getActiveAcpAgentKey(): String? {
        return configFile.activeAcpAgent
    }

    companion object {
        /**
         * Save agent type preference to config file
         *
         * This updates the agentType field in the config file and persists it.
         */
        suspend fun saveAgentTypePreference(agentType: String) {
            try {
                val currentConfig = ConfigManager.load()
                val updatedConfig = currentConfig.configFile.copy(agentType = agentType)
                ConfigManager.save(updatedConfig)
                println("Agent type preference saved: $agentType")
            } catch (e: Exception) {
                println("Failed to save agent type preference: ${e.message}")
                throw e
            }
        }

        /**
         * Save ACP agent configurations to config file.
         *
         * @param acpAgents Map of agent key to AcpAgentConfig
         * @param activeKey The key of the currently active ACP agent (null to keep unchanged)
         */
        suspend fun saveAcpAgents(
            acpAgents: Map<String, AcpAgentConfig>,
            activeKey: String? = null
        ) {
            try {
                val currentConfig = ConfigManager.load()
                val updatedConfig = currentConfig.configFile.copy(
                    acpAgents = acpAgents,
                    activeAcpAgent = activeKey ?: currentConfig.configFile.activeAcpAgent
                )
                ConfigManager.save(updatedConfig)
                println("ACP agent configurations saved: ${acpAgents.keys}")
            } catch (e: Exception) {
                println("Failed to save ACP agent configurations: ${e.message}")
                throw e
            }
        }

        /**
         * Save the active ACP agent key.
         */
        suspend fun saveActiveAcpAgent(key: String) {
            try {
                val currentConfig = ConfigManager.load()
                val updatedConfig = currentConfig.configFile.copy(activeAcpAgent = key)
                ConfigManager.save(updatedConfig)
                println("Active ACP agent saved: $key")
            } catch (e: Exception) {
                println("Failed to save active ACP agent: ${e.message}")
                throw e
            }
        }
    }
}

