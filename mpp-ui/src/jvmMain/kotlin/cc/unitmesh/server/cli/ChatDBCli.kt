package cc.unitmesh.server.cli

import cc.unitmesh.agent.chatdb.ChatDBTask
import cc.unitmesh.agent.chatdb.ChatDBAgent
import cc.unitmesh.agent.config.McpToolConfigService
import cc.unitmesh.agent.config.ToolConfigFile
import cc.unitmesh.agent.database.DatabaseConfig
import cc.unitmesh.agent.tool.filesystem.DefaultToolFileSystem
import cc.unitmesh.llm.KoogLLMService
import cc.unitmesh.llm.LLMProviderType
import cc.unitmesh.llm.ModelConfig
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * JVM CLI for testing ChatDBAgent - Text2SQL Agent
 *
 * Usage:
 * ```bash
 * ./gradlew :mpp-ui:runChatDBCli \
 *   -PdbHost=localhost \
 *   -PdbPort=3306 \
 *   -PdbName=testdb \
 *   -PdbUser=root \
 *   -PdbPassword=prisma \
 *   -PdbQuery="Show me the top 10 customers by order amount"
 * ```
 */
object ChatDBCli {

    @JvmStatic
    fun main(args: Array<String>) {
        println("=".repeat(80))
        println("AutoDev ChatDB Agent CLI (Text2SQL)")
        println("=".repeat(80))

        // Parse database connection arguments
        val dbHost = System.getProperty("dbHost") ?: args.getOrNull(0) ?: "localhost"
        val dbPort = System.getProperty("dbPort")?.toIntOrNull() ?: args.getOrNull(1)?.toIntOrNull() ?: 3306
        val dbName = System.getProperty("dbName") ?: args.getOrNull(2) ?: run {
            System.err.println("Usage: -PdbName=<database> -PdbQuery=<query> [-PdbHost=localhost] [-PdbPort=3306] [-PdbUser=root] [-PdbPassword=]")
            return
        }
        val dbUser = System.getProperty("dbUser") ?: args.getOrNull(3) ?: "root"
        val dbPassword = System.getProperty("dbPassword") ?: args.getOrNull(4) ?: ""
        val dbDialect = System.getProperty("dbDialect") ?: args.getOrNull(5) ?: "MariaDB"

        val query = System.getProperty("dbQuery") ?: args.getOrNull(6) ?: run {
            System.err.println("Usage: -PdbName=<database> -PdbQuery=<query> [-PdbHost=localhost] [-PdbPort=3306] [-PdbUser=root] [-PdbPassword=]")
            return
        }

        val generateVisualization = System.getProperty("generateVisualization")?.toBoolean() ?: true
        val maxRows = System.getProperty("maxRows")?.toIntOrNull() ?: 100

        println("🗄️  Database: $dbDialect://$dbHost:$dbPort/$dbName")
        println("👤 User: $dbUser")
        println("📝 Query: $query")
        println("📊 Generate Visualization: $generateVisualization")
        println("📏 Max Rows: $maxRows")
        println()

        runBlocking {
            var agent: ChatDBAgent? = null
            try {
                val startTime = System.currentTimeMillis()

                // Load LLM configuration from ~/.autodev/config.yaml
                val configFile = File(System.getProperty("user.home"), ".autodev/config.yaml")
                if (!configFile.exists()) {
                    System.err.println("❌ Configuration file not found: ${configFile.absolutePath}")
                    System.err.println("   Please create ~/.autodev/config.yaml with your LLM configuration")
                    return@runBlocking
                }

                val yamlContent = configFile.readText()
                val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))
                val config = yaml.decodeFromString(AutoDevConfig.serializer(), yamlContent)

                val activeName = config.active
                val activeConfig = config.configs.find { it.name == activeName }

                if (activeConfig == null) {
                    System.err.println("❌ Active configuration '$activeName' not found in config.yaml")
                    System.err.println("   Available configs: ${config.configs.map { it.name }.joinToString(", ")}")
                    return@runBlocking
                }

                println("📝 Using LLM config: ${activeConfig.name} (${activeConfig.provider}/${activeConfig.model})")

                // Convert provider string to LLMProviderType
                val providerType = when (activeConfig.provider.lowercase()) {
                    "openai" -> LLMProviderType.OPENAI
                    "anthropic" -> LLMProviderType.ANTHROPIC
                    "google" -> LLMProviderType.GOOGLE
                    "deepseek" -> LLMProviderType.DEEPSEEK
                    "ollama" -> LLMProviderType.OLLAMA
                    "openrouter" -> LLMProviderType.OPENROUTER
                    "glm" -> LLMProviderType.GLM
                    "qwen" -> LLMProviderType.QWEN
                    "kimi" -> LLMProviderType.KIMI
                    else -> LLMProviderType.CUSTOM_OPENAI_BASE
                }

                val llmService = KoogLLMService(
                    ModelConfig(
                        provider = providerType,
                        modelName = activeConfig.model,
                        apiKey = activeConfig.apiKey,
                        temperature = activeConfig.temperature ?: 0.7,
                        maxTokens = activeConfig.maxTokens ?: 4096,
                        baseUrl = activeConfig.baseUrl ?: ""
                    )
                )

                // Create database configuration
                val databaseConfig = DatabaseConfig(
                    host = dbHost,
                    port = dbPort,
                    databaseName = dbName,
                    username = dbUser,
                    password = dbPassword,
                    dialect = dbDialect
                )

                val renderer = CodingCliRenderer()
                val mcpConfigService = McpToolConfigService(ToolConfigFile())
                val projectPath = System.getProperty("user.dir")

                println("🧠 Creating ChatDBAgent...")
                agent = ChatDBAgent(
                    projectPath = projectPath,
                    llmService = llmService,
                    databaseConfigs = mapOf(dbName to databaseConfig),
                    maxIterations = 10,
                    renderer = renderer,
                    fileSystem = DefaultToolFileSystem(projectPath),
                    mcpToolConfigService = mcpConfigService,
                    enableLLMStreaming = true
                )

                println("✅ Agent created")
                println()
                println("🚀 Executing query...")
                println()

                val task = ChatDBTask(
                    query = query,
                    maxRows = maxRows,
                    generateVisualization = generateVisualization
                )

                val result = agent.execute(task) { progress ->
                    println("   $progress")
                }

                val totalTime = System.currentTimeMillis() - startTime

                println()
                println("=".repeat(80))
                println("📊 Result:")
                println("=".repeat(80))
                println(result.content)
                println()

                if (result.success) {
                    println("✅ Query completed successfully")
                } else {
                    println("❌ Query failed")
                }
                println("⏱️  Total time: ${totalTime}ms")
                println("🔄 Revision attempts: ${result.metadata["revisionAttempts"] ?: "0"}")
                println("📈 Has visualization: ${result.metadata["hasVisualization"] ?: "false"}")

            } catch (e: Exception) {
                System.err.println("❌ Error: ${e.message}")
                e.printStackTrace()
            } finally {
                // Close database connection
                agent?.close()
            }
        }
    }
}

