@file:JsExport

package cc.unitmesh.llm

import cc.unitmesh.devins.filesystem.EmptyFileSystem
import cc.unitmesh.devins.filesystem.ProjectFileSystem
import cc.unitmesh.devins.llm.Message
import cc.unitmesh.devins.llm.MessageRole
import cc.unitmesh.devins.completion.CompletionManager
import cc.unitmesh.devins.completion.CompletionContext
import cc.unitmesh.devins.completion.CompletionTriggerType
import cc.unitmesh.devins.completion.CompletionItem
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.promise
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.Promise

/**
 * JavaScript-friendly wrapper for KoogLLMService
 * This class is exported to JavaScript and provides a simpler API
 */
@JsExport
class JsKoogLLMService(config: JsModelConfig) {
    private val kotlinConfig: ModelConfig
    private val service: KoogLLMService
    
    init {
        // Convert string provider to LLMProviderType
        val provider = when (config.providerName.uppercase()) {
            "OPENAI" -> LLMProviderType.OPENAI
            "ANTHROPIC" -> LLMProviderType.ANTHROPIC
            "GOOGLE" -> LLMProviderType.GOOGLE
            "DEEPSEEK" -> LLMProviderType.DEEPSEEK
            "OLLAMA" -> LLMProviderType.OLLAMA
            "OPENROUTER" -> LLMProviderType.OPENROUTER
            else -> throw IllegalArgumentException("Unknown provider: ${config.providerName}")
        }
        
        kotlinConfig = ModelConfig(
            provider = provider,
            modelName = config.modelName,
            apiKey = config.apiKey,
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            baseUrl = config.baseUrl
        )
        
        service = KoogLLMService(kotlinConfig)
    }
    
    /**
     * Stream a prompt and return a Promise that resolves when streaming completes
     * @param userPrompt The user's prompt
     * @param historyMessages Previous conversation messages
     * @param onChunk Callback for each chunk of text received
     * @param onError Callback for errors
     * @param onComplete Callback when streaming completes
     */
    @JsName("streamPrompt")
    fun streamPrompt(
        userPrompt: String,
        historyMessages: Array<JsMessage> = emptyArray(),
        onChunk: (String) -> Unit,
        onError: ((Throwable) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ): Promise<Unit> {
        return Promise { resolve, reject ->
            GlobalScope.launch {
                try {
                    val messages = historyMessages.map { it.toKotlinMessage() }
                    service.streamPrompt(userPrompt, EmptyFileSystem(), messages)
                        .catch { error ->
                            onError?.invoke(error)
                            reject(error)
                        }
                        .collect { chunk ->
                            onChunk(chunk)
                        }
                    onComplete?.invoke()
                    resolve(Unit)
                } catch (e: Throwable) {
                    onError?.invoke(e)
                    reject(e)
                }
            }
        }
    }
    
    // Note: suspend functions cannot be exported to JS directly
    // They need to be called from Kotlin coroutines
    // JavaScript code should use streamPrompt() instead
    
    companion object {
        /**
         * Create a service with validation
         */
        @JsName("create")
        fun create(config: JsModelConfig): JsKoogLLMService {
            return JsKoogLLMService(config)
        }
    }
}

/**
 * JavaScript-friendly model configuration
 * Uses string for provider to avoid enum export issues
 */
@JsExport
data class JsModelConfig(
    val providerName: String,  // "OPENAI", "ANTHROPIC", "GOOGLE", "DEEPSEEK", "OLLAMA", "OPENROUTER"
    val modelName: String,
    val apiKey: String = "",
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096,
    val baseUrl: String = ""
) {
    fun toKotlinConfig(): ModelConfig {
        val provider = when (providerName.uppercase()) {
            "OPENAI" -> LLMProviderType.OPENAI
            "ANTHROPIC" -> LLMProviderType.ANTHROPIC
            "GOOGLE" -> LLMProviderType.GOOGLE
            "DEEPSEEK" -> LLMProviderType.DEEPSEEK
            "OLLAMA" -> LLMProviderType.OLLAMA
            "OPENROUTER" -> LLMProviderType.OPENROUTER
            else -> throw IllegalArgumentException("Unknown provider: $providerName")
        }
        
        return ModelConfig(
            provider = provider,
            modelName = modelName,
            apiKey = apiKey,
            temperature = temperature,
            maxTokens = maxTokens,
            baseUrl = baseUrl
        )
    }
}

/**
 * JavaScript-friendly message
 */
@JsExport
data class JsMessage(
    val role: String,  // "user", "assistant", or "system"
    val content: String
) {
    fun toKotlinMessage(): Message {
        val messageRole = when (role.lowercase()) {
            "user" -> MessageRole.USER
            "assistant" -> MessageRole.ASSISTANT
            "system" -> MessageRole.SYSTEM
            else -> MessageRole.USER
        }
        return Message(messageRole, content)
    }
}

/**
 * JavaScript-friendly result wrapper
 */
@JsExport
data class JsResult(
    val success: Boolean,
    val value: String,
    val error: String?
)

/**
 * Helper object to get available models for a provider
 */
@JsExport
object JsModelRegistry {
    @JsName("getAvailableModels")
    fun getAvailableModels(providerName: String): Array<String> {
        val provider = when (providerName.uppercase()) {
            "OPENAI" -> LLMProviderType.OPENAI
            "ANTHROPIC" -> LLMProviderType.ANTHROPIC
            "GOOGLE" -> LLMProviderType.GOOGLE
            "DEEPSEEK" -> LLMProviderType.DEEPSEEK
            "OLLAMA" -> LLMProviderType.OLLAMA
            "OPENROUTER" -> LLMProviderType.OPENROUTER
            else -> throw IllegalArgumentException("Unknown provider: $providerName")
        }
        return ModelRegistry.getAvailableModels(provider).toTypedArray()
    }
    
    @JsName("getAllProviders")
    fun getAllProviders(): Array<String> {
        return arrayOf("OPENAI", "ANTHROPIC", "GOOGLE", "DEEPSEEK", "OLLAMA", "OPENROUTER")
    }
}

/**
 * JavaScript-friendly completion manager
 * Provides auto-completion for @agent, /command, $variable, etc.
 */
@JsExport
class JsCompletionManager {
    private val manager = CompletionManager()
    
    // Cache completions for insert operation
    private var cachedCompletions: List<CompletionItem> = emptyList()
    private var cachedContext: CompletionContext? = null
    
    /**
     * Initialize workspace for file path completion
     * @param workspacePath The root path of the workspace
     * @return Promise that resolves when workspace is initialized
     */
    @JsName("initWorkspace")
    fun initWorkspace(workspacePath: String): Promise<Boolean> {
        return GlobalScope.promise {
            try {
                cc.unitmesh.devins.workspace.WorkspaceManager.openWorkspace(
                    name = "CLI Workspace",
                    rootPath = workspacePath
                )
                true
            } catch (e: Exception) {
                console.error("Failed to initialize workspace: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Get completion suggestions based on text and cursor position
     * @param text Full input text
     * @param cursorPosition Current cursor position (0-indexed)
     * @return Array of completion items
     */
    @JsName("getCompletions")
    fun getCompletions(text: String, cursorPosition: Int): Array<JsCompletionItem> {
        // Look for the most recent trigger character before the cursor
        var triggerOffset = -1
        var triggerType: CompletionTriggerType? = null
        
        // Search backwards from cursor for a trigger character
        // Stop at whitespace or newline
        for (i in (cursorPosition - 1) downTo 0) {
            val char = text[i]
            when (char) {
                ' ', '\n' -> {
                    // Stop if we hit whitespace - no trigger found
                    break
                }
                '@' -> {
                    triggerOffset = i
                    triggerType = CompletionTriggerType.AGENT
                    break
                }
                '/' -> {
                    triggerOffset = i
                    triggerType = CompletionTriggerType.COMMAND
                    break
                }
                '$' -> {
                    triggerOffset = i
                    triggerType = CompletionTriggerType.VARIABLE
                    break
                }
                ':' -> {
                    // ':' is COMMAND_VALUE trigger, but only if not preceded by space
                    // Check if this is after a command (like "/read-file:")
                    triggerOffset = i
                    triggerType = CompletionTriggerType.COMMAND_VALUE
                    break
                }
            }
        }
        // No trigger found
        if (triggerOffset < 0 || triggerType == null) return emptyArray()
        
        // Extract query text (text after trigger up to cursor)
        val queryText = text.substring(triggerOffset + 1, cursorPosition)
        
        // Check if query is valid (no whitespace or newlines in the middle)
        // Exception: COMMAND_VALUE can have empty query (e.g., "/read-file:")
        if (triggerType != CompletionTriggerType.COMMAND_VALUE) {
            if (queryText.contains('\n') || queryText.contains(' ')) {
                return emptyArray()
            }
        }
        
        val context = CompletionContext(
            fullText = text,
            cursorPosition = cursorPosition,
            triggerType = triggerType,
            triggerOffset = triggerOffset,
            queryText = queryText
        )
        
        val items = manager.getFilteredCompletions(context)
        
        // Cache for later use in applyCompletion
        cachedCompletions = items
        cachedContext = context
        
        return items.mapIndexed { index, item -> item.toJsItem(triggerType, index) }.toTypedArray()
    }
    
    /**
     * Apply a completion by index (from the last getCompletions call)
     * This properly handles insert handlers and triggers next completion if needed
     * 
     * @param text Current full text
     * @param cursorPosition Current cursor position
     * @param completionIndex Index of the completion item to apply
     * @return Insert result with new text, cursor position, and trigger flag
     */
    @JsName("applyCompletion")
    fun applyCompletion(text: String, cursorPosition: Int, completionIndex: Int): JsInsertResult? {
        val context = cachedContext ?: return null
        if (completionIndex < 0 || completionIndex >= cachedCompletions.size) return null
        
        val item = cachedCompletions[completionIndex]
        val result = item.defaultInsert(text, cursorPosition)
        
        return JsInsertResult(
            newText = result.newText,
            newCursorPosition = result.newCursorPosition,
            shouldTriggerNextCompletion = result.shouldTriggerNextCompletion
        )
    }
    
    /**
     * Check if a character should trigger completion
     */
    @JsName("shouldTrigger")
    fun shouldTrigger(char: String): Boolean {
        if (char.isEmpty()) return false
        val c = char[0]
        return c in setOf('@', '/', '$', ':')
    }
    
    /**
     * Get supported trigger types
     */
    @JsName("getSupportedTriggers")
    fun getSupportedTriggers(): Array<String> {
        return arrayOf("@", "/", "$", ":")
    }
}

/**
 * JavaScript-friendly completion item
 */
@JsExport
data class JsCompletionItem(
    val text: String,
    val displayText: String,
    val description: String?,
    val icon: String?,
    val triggerType: String,  // "AGENT", "COMMAND", "VARIABLE", "COMMAND_VALUE"
    val index: Int  // Index for applyCompletion
)

/**
 * JavaScript-friendly insert result
 */
@JsExport
data class JsInsertResult(
    val newText: String,
    val newCursorPosition: Int,
    val shouldTriggerNextCompletion: Boolean
)

/**
 * JavaScript-friendly DevIns compiler wrapper
 * Compiles DevIns code (e.g., "/read-file:path") and returns the result
 */
@JsExport
class JsDevInsCompiler {
    
    /**
     * Compile DevIns source code and return the result
     * @param source DevIns source code (e.g., "解释代码 /read-file:build.gradle.kts")
     * @param variables Optional variables map
     * @return Promise with compilation result
     */
    @JsName("compile")
    fun compile(source: String, variables: dynamic = null): Promise<JsDevInsResult> {
        return GlobalScope.promise {
            try {
                val varsMap = if (variables != null && jsTypeOf(variables) == "object") {
                    val map = mutableMapOf<String, Any>()
                    js("for (var key in variables) { map[key] = variables[key]; }")
                    map
                } else {
                    emptyMap()
                }
                
                val result = cc.unitmesh.devins.compiler.DevInsCompilerFacade.compile(source, varsMap)
                
                JsDevInsResult(
                    success = result.isSuccess(),
                    output = result.output,
                    errorMessage = result.errorMessage,
                    hasCommand = result.statistics.commandCount > 0
                )
            } catch (e: Exception) {
                JsDevInsResult(
                    success = false,
                    output = "",
                    errorMessage = e.message ?: "Unknown error",
                    hasCommand = false
                )
            }
        }
    }
    
    /**
     * Compile DevIns source and return just the output string
     * @param source DevIns source code
     * @return Promise with output string
     */
    @JsName("compileToString")
    fun compileToString(source: String): Promise<String> {
        return GlobalScope.promise {
            try {
                cc.unitmesh.devins.compiler.DevInsCompilerFacade.compileToString(source)
            } catch (e: Exception) {
                throw e
            }
        }
    }
}

/**
 * JavaScript-friendly DevIns compilation result
 */
@JsExport
data class JsDevInsResult(
    val success: Boolean,
    val output: String,
    val errorMessage: String?,
    val hasCommand: Boolean
)

/**
 * Extension to convert CompletionItem to JsCompletionItem
 */
private fun CompletionItem.toJsItem(triggerType: CompletionTriggerType, index: Int): JsCompletionItem {
    val triggerTypeStr = when (triggerType) {
        CompletionTriggerType.AGENT -> "AGENT"
        CompletionTriggerType.COMMAND -> "COMMAND"
        CompletionTriggerType.VARIABLE -> "VARIABLE"
        CompletionTriggerType.COMMAND_VALUE -> "COMMAND_VALUE"
        CompletionTriggerType.CODE_FENCE -> "CODE_FENCE"
        CompletionTriggerType.NONE -> "NONE"
    }
    
    return JsCompletionItem(
        text = this.text,
        displayText = this.displayText,
        description = this.description,
        icon = this.icon,
        triggerType = triggerTypeStr,
        index = index
    )
}


