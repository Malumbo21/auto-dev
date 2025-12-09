package cc.unitmesh.llm.provider.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub Copilot models API response
 */
@Serializable
data class CopilotModelsResponse(
    val data: List<CopilotModel>
)

/**
 * GitHub Copilot model information
 */
@Serializable
data class CopilotModel(
    val capabilities: ModelCapabilities? = null,
    val id: String,
    @SerialName("model_picker_enabled")
    val modelPickerEnabled: Boolean = true,
    val name: String,
    val `object`: String? = null,
    val preview: Boolean = false,
    val vendor: String = "",
    val version: String = "",
    val policy: ModelPolicy? = null
) {
    /**
     * Check if this is an embedding model
     */
    val isEmbedding: Boolean
        get() = capabilities?.type == "embeddings"
    
    /**
     * Check if model is enabled (policy state is enabled or policy is null)
     */
    val isEnabled: Boolean
        get() = policy?.state == "enabled" || policy == null
    
    /**
     * Get display name for UI
     */
    fun getDisplayName(): String {
        val previewSuffix = if (preview) " (Preview)" else ""
        return "$vendor / $name$previewSuffix"
    }
    
    /**
     * Get context length from capabilities
     */
    fun getContextLength(): Long {
        return capabilities?.limits?.maxContextWindowTokens?.toLong() ?: 128_000L
    }
    
    /**
     * Get max output tokens from capabilities
     */
    fun getMaxOutputTokens(): Long? {
        return capabilities?.limits?.maxOutputTokens?.toLong()
    }
}

@Serializable
data class ModelPolicy(
    val state: String? = null,
    val terms: String? = null
)

@Serializable
data class ModelCapabilities(
    val family: String? = null,
    val limits: ModelLimits? = null,
    val `object`: String? = null,
    val supports: ModelSupports? = null,
    val tokenizer: String? = null,
    val type: String? = null
)

@Serializable
data class ModelLimits(
    @SerialName("max_context_window_tokens")
    val maxContextWindowTokens: Int? = null,
    @SerialName("max_prompt_tokens")
    val maxPromptTokens: Int? = null,
    @SerialName("max_output_tokens")
    val maxOutputTokens: Int? = null,
    @SerialName("max_inputs")
    val maxInputs: Int? = null,
    val vision: VisionLimits? = null
)

@Serializable
data class VisionLimits(
    @SerialName("max_prompt_image_size")
    val maxPromptImageSize: Int? = null,
    @SerialName("max_prompt_images")
    val maxPromptImages: Int? = null,
    @SerialName("supported_media_types")
    val supportedMediaTypes: List<String>? = null
)

@Serializable
data class ModelSupports(
    val streaming: Boolean? = null,
    @SerialName("tool_calls")
    val toolCalls: Boolean? = null,
    @SerialName("parallel_tool_calls")
    val parallelToolCalls: Boolean? = null,
    val vision: Boolean? = null,
    @SerialName("structured_outputs")
    val structuredOutputs: Boolean? = null,
    val dimensions: Boolean? = null
)

