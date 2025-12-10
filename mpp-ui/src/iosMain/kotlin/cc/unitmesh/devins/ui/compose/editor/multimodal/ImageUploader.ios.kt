package cc.unitmesh.devins.ui.compose.editor.multimodal

import cc.unitmesh.config.CloudStorageConfig

/**
 * iOS stub implementation of ImageUploader.
 * Full cloud storage support coming soon.
 */
actual class ImageUploader actual constructor(private val config: CloudStorageConfig) {
    actual suspend fun uploadImage(
        imagePath: String,
        onProgress: (Int) -> Unit
    ): ImageUploadResult {
        return ImageUploadResult(
            success = false,
            error = "Image upload is not yet supported on iOS. Coming soon!"
        )
    }
    
    actual fun isConfigured(): Boolean = false
    
    actual fun close() {}
}

/**
 * iOS stub implementation of VisionAnalysisService.
 */
actual class VisionAnalysisService actual constructor(
    private val apiKey: String,
    private val modelName: String
) {
    actual suspend fun analyzeImages(
        imageUrls: List<String>,
        prompt: String,
        onChunk: (String) -> Unit
    ): String {
        throw UnsupportedOperationException("Vision analysis is not yet supported on iOS")
    }
    
    actual fun close() {}
}

