package cc.unitmesh.llm

import cc.unitmesh.config.ConfigManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton manager for LLM and Image Generation service configurations.
 * 
 * This manager provides reactive StateFlow-based configuration that allows
 * UI components to update service configurations dynamically without recreating
 * service instances.
 * 
 * Usage:
 * - UI components call updateModelConfig() when user selects a different model
 * - UI components call updateImageGenerationModel() when user selects a different image model
 * - Services read from currentModelConfig and currentImageGenerationModel StateFlows
 */
object LLMServiceManager {
    
    /**
     * Default image generation model
     */
    const val DEFAULT_IMAGE_MODEL = "cogview-3-flash"
    
    /**
     * Available image generation models for GLM CogView
     */
    val AVAILABLE_IMAGE_MODELS = listOf("cogview-3-flash", "cogview-4")
    
    // Current LLM model configuration
    private val _currentModelConfig = MutableStateFlow<ModelConfig?>(null)
    val currentModelConfig: StateFlow<ModelConfig?> = _currentModelConfig.asStateFlow()
    
    // Current image generation model (GLM CogView)
    private val _currentImageGenerationModel = MutableStateFlow(DEFAULT_IMAGE_MODEL)
    val currentImageGenerationModel: StateFlow<String> = _currentImageGenerationModel.asStateFlow()
    
    /**
     * Update the current LLM model configuration.
     * Called by ModelSelector when user selects a different model.
     * 
     * @param config The new model configuration
     */
    fun updateModelConfig(config: ModelConfig) {
        _currentModelConfig.value = config
    }
    
    /**
     * Update the current image generation model.
     * Called by ImageGenerationModelSelector when user selects a different model.
     * 
     * @param model The model ID (e.g., "cogview-3-flash", "cogview-4")
     */
    fun updateImageGenerationModel(model: String) {
        if (model in AVAILABLE_IMAGE_MODELS) {
            _currentImageGenerationModel.value = model
        }
    }
    
    /**
     * Get the current image generation model synchronously.
     * Used by ImageGenerationService when building requests.
     */
    fun getImageGenerationModel(): String = _currentImageGenerationModel.value
    
    /**
     * Get the current model config synchronously.
     * Used by services that need the current config.
     */
    fun getModelConfig(): ModelConfig? = _currentModelConfig.value
    
    /**
     * Check if the current provider is GLM (supports image generation).
     */
    fun isGlmProvider(): Boolean {
        return _currentModelConfig.value?.provider == LLMProviderType.GLM
    }
    
    /**
     * Initialize from ConfigManager.
     * Should be called at app startup to load the active configuration.
     */
    suspend fun initializeFromConfig() {
        try {
            val wrapper = ConfigManager.load()
            val activeConfig = wrapper.getActiveModelConfig()
            if (activeConfig != null) {
                _currentModelConfig.value = activeConfig
            }
        } catch (e: Exception) {
            // Config not available, leave as null
        }
    }
    
    /**
     * Reset to defaults (for testing or cleanup).
     */
    fun reset() {
        _currentModelConfig.value = null
        _currentImageGenerationModel.value = DEFAULT_IMAGE_MODEL
    }
}

