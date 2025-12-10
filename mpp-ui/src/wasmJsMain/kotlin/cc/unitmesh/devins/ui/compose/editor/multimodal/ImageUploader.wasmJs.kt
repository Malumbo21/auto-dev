package cc.unitmesh.devins.ui.compose.editor.multimodal

import cc.unitmesh.config.CloudStorageConfig

/**
 * WASM stub implementation of ImageUploader.
 * Cloud storage upload is only supported on JVM platform.
 */
actual class ImageUploader actual constructor(private val config: CloudStorageConfig) {
    actual suspend fun uploadImage(
        imagePath: String,
        onProgress: (Int) -> Unit
    ): ImageUploadResult {
        return ImageUploadResult(
            success = false,
            error = "Image upload is not supported on web platform. Please use the desktop app."
        )
    }
    
    actual fun isConfigured(): Boolean = false
    
    actual fun close() {}
}

/**
 * WASM stub implementation of VisionAnalysisService.
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
        throw UnsupportedOperationException("Vision analysis is not supported on web platform")
    }
    
    actual fun close() {}
}

