package cc.unitmesh.devins.ui.compose.editor.multimodal

import cc.unitmesh.config.CloudStorageConfig
import cc.unitmesh.llm.multimodal.ImageCompressor
import cc.unitmesh.llm.multimodal.MultimodalLLMService
import cc.unitmesh.llm.multimodal.TencentCosUploader
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * JVM implementation of ImageUploader using TencentCosUploader and ImageCompressor.
 */
actual class ImageUploader actual constructor(private val config: CloudStorageConfig) {
    private val cosUploader: TencentCosUploader? = if (config.isConfigured()) {
        TencentCosUploader(
            secretId = config.secretId,
            secretKey = config.secretKey,
            region = config.region,
            bucket = config.bucket
        )
    } else null

    actual suspend fun uploadImage(
        imagePath: String,
        onProgress: (Int) -> Unit
    ): ImageUploadResult = withContext(Dispatchers.IO) {
        if (cosUploader == null) {
            return@withContext ImageUploadResult(
                success = false,
                error = "Cloud storage not configured"
            )
        }

        try {
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                return@withContext ImageUploadResult(
                    success = false,
                    error = "Image file not found: $imagePath"
                )
            }

            val originalSize = imageFile.length()
            onProgress(10)

            // Compress image using ImageCompressor object
            onProgress(20)

            val compressionResult = ImageCompressor.compress(imageFile, ImageCompressor.Config.BALANCED)

            onProgress(40)

            println("📸 Compressed: ${originalSize / 1024}KB -> ${compressionResult.compressedSize / 1024}KB")

            // Create temporary file with compressed image - use jpg by default since BALANCED uses JPEG
            val extension = "jpg"
            val tempFile = File.createTempFile("compressed_", ".$extension")
            tempFile.writeBytes(compressionResult.bytes)
            tempFile.deleteOnExit()

            onProgress(60)

            // Generate object key
            val timestamp = System.currentTimeMillis()
            val uuid = UUID.randomUUID().toString().substring(0, 8)
            val objectKey = "multimodal/$timestamp/${uuid}_${imageFile.nameWithoutExtension}.$extension"

            // Upload to COS
            val uploadResult = cosUploader.uploadImage(tempFile, objectKey)

            onProgress(90)

            // Clean up temp file
            tempFile.delete()

            onProgress(100)

            uploadResult.fold(
                onSuccess = { url ->
                    ImageUploadResult(
                        success = true,
                        url = url,
                        originalSize = originalSize,
                        compressedSize = compressionResult.compressedSize
                    )
                },
                onFailure = { e ->
                    ImageUploadResult(
                        success = false,
                        error = e.message ?: "Upload failed"
                    )
                }
            )
        } catch (e: Exception) {
            ImageUploadResult(
                success = false,
                error = e.message ?: "Upload failed"
            )
        }
    }

    actual suspend fun uploadImageBytes(
        imageBytes: ByteArray,
        fileName: String,
        mimeType: String,
        onProgress: (Int) -> Unit
    ): ImageUploadResult = withContext(Dispatchers.IO) {
        if (cosUploader == null) {
            return@withContext ImageUploadResult(
                success = false,
                error = "Cloud storage not configured"
            )
        }

        try {
            val originalSize = imageBytes.size.toLong()
            onProgress(10)

            // Determine extension from mimeType or fileName
            val extension = when {
                mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
                mimeType.contains("png") -> "png"
                mimeType.contains("gif") -> "gif"
                mimeType.contains("webp") -> "webp"
                else -> fileName.substringAfterLast('.', "png")
            }

            // Create temporary file from bytes
            val tempInputFile = File.createTempFile("pasted_", ".$extension")
            tempInputFile.writeBytes(imageBytes)
            tempInputFile.deleteOnExit()

            onProgress(20)

            // Compress image using ImageCompressor
            val compressionResult = ImageCompressor.compress(tempInputFile, ImageCompressor.Config.BALANCED)

            onProgress(40)

            println("Pasted image compressed: ${originalSize / 1024}KB -> ${compressionResult.compressedSize / 1024}KB")

            // Create temporary file with compressed image
            val compressedExtension = "jpg" // BALANCED uses JPEG
            val tempCompressedFile = File.createTempFile("compressed_pasted_", ".$compressedExtension")
            tempCompressedFile.writeBytes(compressionResult.bytes)
            tempCompressedFile.deleteOnExit()

            onProgress(60)

            // Generate object key
            val timestamp = System.currentTimeMillis()
            val uuid = UUID.randomUUID().toString().substring(0, 8)
            val baseName = fileName.substringBeforeLast('.').ifEmpty { "pasted_image" }
            val objectKey = "multimodal/$timestamp/${uuid}_$baseName.$compressedExtension"

            // Upload to COS
            val uploadResult = cosUploader.uploadImage(tempCompressedFile, objectKey)

            onProgress(90)

            // Clean up temp files
            tempInputFile.delete()
            tempCompressedFile.delete()

            onProgress(100)

            uploadResult.fold(
                onSuccess = { url ->
                    ImageUploadResult(
                        success = true,
                        url = url,
                        originalSize = originalSize,
                        compressedSize = compressionResult.compressedSize
                    )
                },
                onFailure = { e ->
                    ImageUploadResult(
                        success = false,
                        error = e.message ?: "Upload failed"
                    )
                }
            )
        } catch (e: Exception) {
            ImageUploadResult(
                success = false,
                error = e.message ?: "Upload failed"
            )
        }
    }

    actual fun isConfigured(): Boolean = config.isConfigured() && cosUploader != null

    actual fun close() {
        cosUploader?.close()
    }
}

/**
 * JVM implementation of VisionAnalysisService using MultimodalLLMService.
 */
actual class VisionAnalysisService actual constructor(
    private val apiKey: String,
    private val modelName: String
) {
    private val multimodalService = MultimodalLLMService.createWithoutCos(
        apiKey = apiKey,
        modelName = modelName
    )

    actual suspend fun analyzeImages(
        imageUrls: List<String>,
        prompt: String,
        onChunk: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val result = StringBuilder()

        // Process each image URL with the multimodal service
        // For multiple images, we combine them into a single analysis
        if (imageUrls.isEmpty()) {
            throw IllegalArgumentException("No image URLs provided")
        }

        // Use the first image for now (MultimodalLLMService handles single image)
        // TODO: Support multiple images in MultimodalLLMService
        val imageUrl = imageUrls.first()
        val fullPrompt = if (imageUrls.size > 1) {
            "$prompt\n\n(Note: ${imageUrls.size} images were uploaded, analyzing the first one)"
        } else {
            prompt
        }

        multimodalService.streamImageUnderstanding(
            imageUrl = imageUrl,
            prompt = fullPrompt,
            enableThinking = true
        ).collect { chunk ->
            result.append(chunk)
            onChunk(chunk)
        }

        result.toString()
    }

    actual fun close() {
        multimodalService.close()
    }
}

