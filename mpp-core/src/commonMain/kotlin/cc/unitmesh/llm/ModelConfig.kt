package cc.unitmesh.llm

import kotlinx.serialization.Serializable

/**
 * LLM Provider types supported by Koog
 */
enum class LLMProviderType(val displayName: String) {
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    GOOGLE("Google"),
    DEEPSEEK("DeepSeek"),
    OLLAMA("Ollama"),
    OPENROUTER("OpenRouter"),
    GLM("GLM"),
    QWEN("Qwen"),
    KIMI("Kimi"),
    CUSTOM_OPENAI_BASE("custom-openai-base");

    companion object {
        fun fromDisplayName(name: String): LLMProviderType? {
            return entries.find { it.displayName == name }
        }
    }
}

/**
 * LLM 模型配置 - 只负责存储配置信息
 * 职责：
 * 1. 存储 LLM 连接配置（Provider、API Key、Base URL 等）
 * 2. 存储模型参数配置（temperature、maxTokens 等）
 * 3. 存储自定义请求/响应格式配置
 * 4. 验证配置有效性
 */
@Serializable
data class ModelConfig(
    val provider: LLMProviderType = LLMProviderType.DEEPSEEK,
    val modelName: String = "",
    val apiKey: String = "",
    val temperature: Double = 0.0,
    val maxTokens: Int = 128000,
    val baseUrl: String = "", // For custom endpoints like Ollama
    /**
     * Custom HTTP headers to include in requests
     * Example: {"X-Custom-Header": "value"}
     */
    val customHeaders: Map<String, String> = emptyMap(),
    /**
     * Custom fields to include in the request body
     * These will be merged with the standard request fields
     * Example: {"top_p": 0.9, "presence_penalty": 0.1}
     */
    val customBodyFields: Map<String, String> = emptyMap(),
    /**
     * JsonPath expression for extracting content from streaming response
     * Default: "$.choices[0].delta.content" (OpenAI format)
     * Empty string means use provider's default format
     */
    val responseContentPath: String = "",
    /**
     * JsonPath expression for extracting reasoning content (for thinking models)
     * Example: "$.choices[0].delta.reasoning_content" (DeepSeek format)
     * Empty string means no reasoning content extraction
     */
    val reasoningContentPath: String = "",
    /**
     * Whether to use streaming response
     */
    val stream: Boolean = true
) {
    /**
     * 验证配置是否有效
     */
    fun isValid(): Boolean {
        return when (provider) {
            LLMProviderType.OLLAMA ->
                modelName.isNotEmpty() && baseUrl.isNotEmpty()
            LLMProviderType.GLM, LLMProviderType.QWEN, LLMProviderType.KIMI, LLMProviderType.CUSTOM_OPENAI_BASE ->
                apiKey.isNotEmpty() && modelName.isNotEmpty() && baseUrl.isNotEmpty()
            else ->
                apiKey.isNotEmpty() && modelName.isNotEmpty()
        }
    }

    /**
     * Check if this config has custom request/response format
     */
    fun hasCustomFormat(): Boolean {
        return customHeaders.isNotEmpty() ||
               customBodyFields.isNotEmpty() ||
               responseContentPath.isNotEmpty()
    }

    /**
     * Get the effective response content path
     * Returns the custom path if set, otherwise returns the default OpenAI format
     */
    fun getEffectiveResponseContentPath(): String {
        return responseContentPath.ifEmpty { DEFAULT_RESPONSE_CONTENT_PATH }
    }

    /**
     * Get the effective response content path for non-streaming response
     */
    fun getEffectiveNonStreamResponseContentPath(): String {
        return if (responseContentPath.isNotEmpty()) {
            // Convert streaming path to non-streaming path
            responseContentPath.replace(".delta.", ".message.")
        } else {
            DEFAULT_NON_STREAM_RESPONSE_CONTENT_PATH
        }
    }

    companion object {
        const val DEFAULT_RESPONSE_CONTENT_PATH = "\$.choices[0].delta.content"
        const val DEFAULT_NON_STREAM_RESPONSE_CONTENT_PATH = "\$.choices[0].message.content"
        /**
         * 创建默认配置
         */
        fun default() = ModelConfig()

        @Deprecated(
            message = "Use ModelRegistry.getAvailableModels() instead",
            replaceWith = ReplaceWith("ModelRegistry.getAvailableModels(provider)", "cc.unitmesh.llm.ModelRegistry")
        )
        fun getDefaultModelsForProvider(provider: LLMProviderType): List<String> {
            return ModelRegistry.getAvailableModels(provider)
        }

        @Deprecated(
            message = "Use ModelRegistry.createModel() instead",
            replaceWith = ReplaceWith("ModelRegistry.createModel(provider, modelName)", "cc.unitmesh.llm.ModelRegistry")
        )
        fun getDefaultModelForProvider(provider: LLMProviderType, modelName: String): ai.koog.prompt.llm.LLModel? {
            return ModelRegistry.createModel(provider, modelName)
        }
    }
}

