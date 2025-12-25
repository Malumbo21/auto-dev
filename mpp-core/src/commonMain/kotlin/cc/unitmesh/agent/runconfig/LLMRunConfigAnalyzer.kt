package cc.unitmesh.agent.runconfig

import cc.unitmesh.agent.logging.getLogger
import cc.unitmesh.agent.tool.filesystem.DefaultToolFileSystem
import cc.unitmesh.agent.tool.filesystem.ToolFileSystem
import cc.unitmesh.llm.KoogLLMService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * LLM-based Run Config Analyzer - Uses LLM streaming to analyze project and generate run configurations.
 * 
 * This analyzer:
 * 1. Gathers project context (file structure, config files)
 * 2. Sends a streaming request to LLM
 * 3. Parses JSON response to extract run configs
 * 
 * Benefits over static analysis:
 * - Handles complex/unconventional project structures
 * - Can understand multi-module projects
 * - Provides intelligent suggestions based on project context
 */
class LLMRunConfigAnalyzer(
    private val projectPath: String,
    private val fileSystem: ToolFileSystem = DefaultToolFileSystem(projectPath = projectPath),
    private val llmService: KoogLLMService
) {
    private val logger = getLogger("LLMRunConfigAnalyzer")
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
    /**
     * Analyze project with streaming - yields progress chunks then final configs.
     * 
     * @return Flow of AnalysisEvent - either Progress (LLM reasoning) or Complete (parsed configs)
     */
    fun analyzeStreaming(): Flow<AnalysisEvent> = flow {
        emit(AnalysisEvent.Progress("Gathering project context..."))
        
        // Gather project context
        val context = gatherProjectContext()
        emit(AnalysisEvent.Progress("Found ${context.files.size} relevant files"))
        
        // Build prompt
        val prompt = buildAnalysisPrompt(context)
        emit(AnalysisEvent.Progress("Analyzing with AI...\n"))
        
        // Stream LLM response
        val responseBuffer = StringBuilder()
        val streamedContent = StringBuilder()
        var inJsonBlock = false
        
        try {
            llmService.streamPrompt(prompt, compileDevIns = false).collect { chunk ->
                responseBuffer.append(chunk)
                
                // Check if we're entering JSON block
                if (chunk.contains("```json") || chunk.contains("```JSON")) {
                    inJsonBlock = true
                }
                
                // Only emit visible text (not JSON)
                if (!inJsonBlock) {
                    // Clean up thinking tags for display
                    val displayChunk = chunk
                        .replace("<think>", "")
                        .replace("</think>", "")
                        .replace("<reasoning>", "")
                        .replace("</reasoning>", "")
                    
                    if (displayChunk.isNotBlank()) {
                        streamedContent.append(displayChunk)
                        emit(AnalysisEvent.Progress(displayChunk))
                    }
                }
                
                // Check if JSON block ended
                if (inJsonBlock && chunk.contains("```") && !chunk.contains("```json")) {
                    // JSON block might have ended, but keep inJsonBlock true to avoid further text
                }
            }
            
            // Parse final response
            val fullResponse = responseBuffer.toString()
            logger.debug { "LLM response:\n$fullResponse" }
            
            val configs = parseRunConfigs(fullResponse)
            
            if (configs.isNotEmpty()) {
                emit(AnalysisEvent.Progress("\n✓ Found ${configs.size} run configurations"))
                emit(AnalysisEvent.Complete(configs))
            } else {
                emit(AnalysisEvent.Error("Could not parse run configurations from AI response"))
            }
            
        } catch (e: Exception) {
            logger.error { "LLM analysis failed: ${e.message}" }
            emit(AnalysisEvent.Error("Analysis failed: ${e.message}"))
        }
    }
    
    /**
     * Analyze project (non-streaming, for simpler use cases)
     */
    suspend fun analyze(onProgress: (String) -> Unit = {}): List<RunConfig> {
        val configs = mutableListOf<RunConfig>()
        
        analyzeStreaming().collect { event ->
            when (event) {
                is AnalysisEvent.Progress -> onProgress(event.message)
                is AnalysisEvent.Complete -> configs.addAll(event.configs)
                is AnalysisEvent.Error -> onProgress("Error: ${event.message}")
            }
        }
        
        return configs
    }
    
    /**
     * Gather project context for LLM analysis
     */
    private suspend fun gatherProjectContext(): ProjectContext {
        val files = mutableListOf<String>()
        val configContents = mutableMapOf<String, String>()
        
        // Important config files to read
        val configFiles = listOf(
            "package.json",
            "build.gradle.kts", "build.gradle",
            "settings.gradle.kts", "settings.gradle",
            "pom.xml",
            "Cargo.toml",
            "go.mod",
            "pyproject.toml", "setup.py", "requirements.txt",
            "Makefile",
            "docker-compose.yml", "docker-compose.yaml",
            "Dockerfile",
            ".github/workflows/ci.yml"
        )
        
        // Get top-level file list
        try {
            val topFiles = fileSystem.listFiles(projectPath)
                .filter { !it.startsWith(".") && it != "node_modules" && it != "build" && it != "target" }
                .take(30)
            files.addAll(topFiles)
        } catch (e: Exception) {
            logger.warn { "Failed to list files: ${e.message}" }
        }
        
        // Read relevant config files
        for (configFile in configFiles) {
            val path = "$projectPath/$configFile"
            if (fileSystem.exists(path)) {
                try {
                    val content = fileSystem.readFile(path)
                    if (content != null) {
                        // Truncate large files
                        val truncated = if (content.length > 2000) {
                            content.take(2000) + "\n... (truncated)"
                        } else {
                            content
                        }
                        configContents[configFile] = truncated
                    }
                } catch (e: Exception) {
                    logger.warn { "Failed to read $configFile: ${e.message}" }
                }
            }
        }
        
        return ProjectContext(files, configContents)
    }
    
    /**
     * Build the analysis prompt for LLM
     */
    private fun buildAnalysisPrompt(context: ProjectContext): String {
        return buildString {
            appendLine("You are a project analysis expert. Analyze this project and generate run configurations.")
            appendLine()
            appendLine("## Project Files")
            appendLine(context.files.joinToString("\n") { "- $it" })
            appendLine()
            
            if (context.configContents.isNotEmpty()) {
                appendLine("## Configuration Files")
                context.configContents.forEach { (name, content) ->
                    appendLine()
                    appendLine("### $name")
                    appendLine("```")
                    appendLine(content)
                    appendLine("```")
                }
                appendLine()
            }
            
            appendLine("## Task")
            appendLine("Based on this project structure, identify all available run configurations.")
            appendLine("Consider: start commands, dev/watch modes, test commands, build commands, lint/format, deploy, clean, install dependencies.")
            appendLine()
            appendLine("## Output Format")
            appendLine("First, briefly explain what type of project this is and what commands you found.")
            appendLine("Then output a JSON array with the run configurations:")
            appendLine()
            appendLine("```json")
            appendLine("[")
            appendLine("  {")
            appendLine("    \"name\": \"Display name (e.g., 'Start Dev Server')\",")
            appendLine("    \"command\": \"The shell command to run (e.g., 'npm run dev')\",")
            appendLine("    \"type\": \"RUN|DEV|TEST|BUILD|LINT|DEPLOY|CLEAN|INSTALL|CUSTOM\",")
            appendLine("    \"description\": \"Brief description of what this command does\",")
            appendLine("    \"workingDir\": \".\" // Optional, relative to project root")
            appendLine("  }")
            appendLine("]")
            appendLine("```")
            appendLine()
            appendLine("Important:")
            appendLine("- Include the most useful commands (max 10)")
            appendLine("- Mark the primary 'run' command as type RUN")
            appendLine("- Use correct commands for the detected package manager (npm/yarn/pnpm)")
            appendLine("- For Gradle, use './gradlew' if wrapper exists")
        }
    }
    
    /**
     * Parse run configs from LLM response
     */
    private fun parseRunConfigs(response: String): List<RunConfig> {
        // Extract JSON block from response
        val jsonPattern = Regex("```json\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val match = jsonPattern.find(response)
        
        val jsonStr = match?.groupValues?.get(1)?.trim()
            ?: run {
                // Try to find raw JSON array
                val arrayPattern = Regex("\\[\\s*\\{[\\s\\S]*?}\\s*]")
                arrayPattern.find(response)?.value
            }
        
        if (jsonStr.isNullOrBlank()) {
            logger.warn { "No JSON found in LLM response" }
            return emptyList()
        }
        
        return try {
            val suggestions = json.decodeFromString<List<LLMRunConfigSuggestion>>(jsonStr)
            suggestions.mapIndexed { index, suggestion ->
                RunConfig(
                    id = "ai-${suggestion.name.lowercase().replace(Regex("[^a-z0-9]"), "-")}-$index",
                    name = suggestion.name,
                    type = parseRunConfigType(suggestion.type),
                    command = suggestion.command,
                    workingDir = suggestion.workingDir ?: ".",
                    description = suggestion.description ?: "",
                    source = RunConfigSource.AI_GENERATED,
                    isDefault = index == 0 && parseRunConfigType(suggestion.type) == RunConfigType.RUN
                )
            }
        } catch (e: Exception) {
            logger.error { "Failed to parse JSON: ${e.message}\nJSON: $jsonStr" }
            emptyList()
        }
    }
    
    private fun parseRunConfigType(type: String?): RunConfigType {
        return try {
            type?.uppercase()?.let { RunConfigType.valueOf(it) } ?: RunConfigType.CUSTOM
        } catch (e: Exception) {
            RunConfigType.CUSTOM
        }
    }
}

/**
 * Events emitted during analysis
 */
sealed class AnalysisEvent {
    /** Progress update with message */
    data class Progress(val message: String) : AnalysisEvent()
    
    /** Analysis complete with configs */
    data class Complete(val configs: List<RunConfig>) : AnalysisEvent()
    
    /** Analysis failed */
    data class Error(val message: String) : AnalysisEvent()
}

/**
 * Project context gathered for LLM analysis
 */
private data class ProjectContext(
    val files: List<String>,
    val configContents: Map<String, String>
)

/**
 * LLM suggestion structure for parsing
 */
@Serializable
private data class LLMRunConfigSuggestion(
    val name: String,
    val command: String,
    val type: String? = null,
    val description: String? = null,
    val workingDir: String? = null
)

