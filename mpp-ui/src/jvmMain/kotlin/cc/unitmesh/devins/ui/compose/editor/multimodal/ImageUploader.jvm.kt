package cc.unitmesh.devins.ui.compose.editor.multimodal

import cc.unitmesh.config.CloudStorageConfig
import cc.unitmesh.llm.multimodal.ImageCompressor
import cc.unitmesh.llm.multimodal.TencentCosUploader
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
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
    private val client = HttpClient(CIO) {
        expectSuccess = false
    }
    private val json = Json { ignoreUnknownKeys = true }
    
    actual suspend fun analyzeImages(
        imageUrls: List<String>,
        prompt: String,
        onChunk: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val result = StringBuilder()
        
        try {
            val requestBody = buildImageRequest(imageUrls, prompt)
            val requestUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
            
            val response = client.post(requestUrl) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                    append(HttpHeaders.ContentType, "application/json")
                }
                setBody(requestBody)
            }
            
            if (response.status.isSuccess()) {
                val channel: ByteReadChannel = response.body()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: continue
                    if (line.startsWith("data: ")) {
                        val jsonString = line.substringAfter("data: ").trim()
                        if (jsonString == "[DONE]") break
                        
                        try {
                            val jsonElement = json.parseToJsonElement(jsonString)
                            val content = jsonElement.jsonObject["choices"]
                                ?.jsonArray?.get(0)
                                ?.jsonObject?.get("delta")
                                ?.jsonObject?.get("content")
                                ?.jsonPrimitive?.content
                            if (content != null) {
                                result.append(content)
                                onChunk(content)
                            }
                        } catch (_: Exception) {
                            // Skip parsing errors
                        }
                    }
                }
            } else {
                val errorBody = response.bodyAsText()
                throw RuntimeException("API request failed: ${response.status.value} - $errorBody")
            }
        } catch (e: Exception) {
            throw e
        }
        
        result.toString()
    }
    
    private fun buildImageRequest(imageUrls: List<String>, prompt: String): String {
        val contentArray = buildJsonArray {
            // Add all images
            imageUrls.forEach { url ->
                addJsonObject {
                    put("type", "image_url")
                    putJsonObject("image_url") {
                        put("url", url)
                    }
                }
            }
            // Add text prompt
            addJsonObject {
                put("type", "text")
                put("text", prompt)
            }
        }
        
        val messages = buildJsonArray {
            addJsonObject {
                put("role", "user")
                put("content", contentArray)
            }
        }
        
        val requestJson = buildJsonObject {
            put("model", modelName)
            put("messages", messages)
            put("stream", true)
        }
        
        return json.encodeToString(JsonObject.serializer(), requestJson)
    }
    
    actual fun close() {
        client.close()
    }
}

