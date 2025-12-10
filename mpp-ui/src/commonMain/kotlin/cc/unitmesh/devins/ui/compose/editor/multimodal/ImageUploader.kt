package cc.unitmesh.devins.ui.compose.editor.multimodal

import cc.unitmesh.config.CloudStorageConfig

/**
 * Result of image upload operation.
 */
data class ImageUploadResult(
    val success: Boolean,
    val url: String? = null,
    val error: String? = null,
    val originalSize: Long = 0,
    val compressedSize: Long = 0
)

/**
 * Platform-specific image uploader.
 * Handles image compression and upload to cloud storage.
 */
expect class ImageUploader(config: CloudStorageConfig) {
    /**
     * Upload an image to cloud storage.
     * @param imagePath Path to the local image file
     * @param onProgress Progress callback (0-100)
     * @return Upload result with URL or error
     */
    suspend fun uploadImage(
        imagePath: String,
        onProgress: (Int) -> Unit = {}
    ): ImageUploadResult
    
    /**
     * Check if the uploader is properly configured.
     */
    fun isConfigured(): Boolean
    
    /**
     * Close and release resources.
     */
    fun close()
}

/**
 * Vision analysis service for analyzing images with a vision model.
 */
expect class VisionAnalysisService(
    apiKey: String,
    modelName: String = "glm-4.6v"
) {
    /**
     * Analyze images with the vision model.
     * @param imageUrls List of uploaded image URLs
     * @param prompt User's prompt
     * @param onChunk Callback for streaming response chunks
     * @return Full analysis result
     */
    suspend fun analyzeImages(
        imageUrls: List<String>,
        prompt: String,
        onChunk: (String) -> Unit = {}
    ): String
    
    /**
     * Close and release resources.
     */
    fun close()
}

