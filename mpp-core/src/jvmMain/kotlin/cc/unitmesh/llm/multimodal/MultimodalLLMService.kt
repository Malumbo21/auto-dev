package cc.unitmesh.llm.multimodal

import cc.unitmesh.llm.LLMProviderType
import cc.unitmesh.llm.ModelConfig
import cc.unitmesh.llm.ModelRegistry
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File

/**
 * Multimodal LLM Service for vision models like GLM-4.6V.
 * 
 * Supports streaming responses with image understanding capabilities.
 * 
 * Reference: https://docs.bigmodel.cn/cn/guide/models/vlm/glm-4.6v
 */
class MultimodalLLMService(
    private val config: ModelConfig,
    private val cosUploader: TencentCosUploader? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000 // 2 minutes for vision tasks
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
        }
    }

    private val baseUrl: String
        get() = config.baseUrl.ifEmpty { 
            ModelRegistry.getDefaultBaseUrl(LLMProviderType.GLM)
        }

    /**
     * Send an image understanding request with streaming response.
     * 
     * @param imageUrl URL of the image to analyze
     * @param prompt Text prompt describing what to analyze
     * @param enableThinking Whether to enable deep thinking mode (default: false)
     * @return Flow of response text chunks
     */
    fun streamImageUnderstanding(
        imageUrl: String,
        prompt: String,
        enableThinking: Boolean = false
    ): Flow<String> = flow {
        val requestBody = buildImageRequest(imageUrl, prompt, enableThinking, stream = true)
        
        client.preparePost("${baseUrl}chat/completions") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${config.apiKey}")
            setBody(requestBody)
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                throw RuntimeException("API request failed: ${response.status} - $errorBody")
            }

            // Process SSE stream
            val channel: ByteReadChannel = response.bodyAsChannel()
            
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                val trimmedLine = line.trim()
                
                if (trimmedLine.startsWith("data: ")) {
                    val data = trimmedLine.removePrefix("data: ").trim()
                    if (data == "[DONE]") {
                        return@execute
                    }
                    try {
                        val chunk = json.parseToJsonElement(data).jsonObject
                        val choices = chunk["choices"]?.jsonArray
                        if (choices != null && choices.isNotEmpty()) {
                            val delta = choices[0].jsonObject["delta"]?.jsonObject
                            val text = delta?.get("content")?.jsonPrimitive?.contentOrNull
                            if (text != null) {
                                emit(text)
                            }
                        }
                    } catch (e: Exception) {
                        // Skip malformed JSON
                    }
                }
            }
        }
    }

    /**
     * Send an image understanding request with non-streaming response.
     */
    suspend fun sendImageUnderstanding(
        imageUrl: String,
        prompt: String,
        enableThinking: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val requestBody = buildImageRequest(imageUrl, prompt, enableThinking, stream = false)
        
        val response = client.post("${baseUrl}chat/completions") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${config.apiKey}")
            setBody(requestBody)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw RuntimeException("API request failed: ${response.status} - $errorBody")
        }

        val responseText = response.bodyAsText()
        val jsonResponse = json.parseToJsonElement(responseText).jsonObject
        val choices = jsonResponse["choices"]?.jsonArray
        
        if (choices != null && choices.isNotEmpty()) {
            val message = choices[0].jsonObject["message"]?.jsonObject
            message?.get("content")?.jsonPrimitive?.contentOrNull ?: ""
        } else {
            throw RuntimeException("No response content received")
        }
    }

    /**
     * Compress and upload a local image file, then analyze it.
     * 
     * @param imageFile Local image file
     * @param prompt Text prompt
     * @param compressionConfig Image compression configuration
     * @param enableThinking Whether to enable deep thinking mode
     * @return Flow of response text chunks
     */
    fun streamImageFromFile(
        imageFile: File,
        prompt: String,
        compressionConfig: ImageCompressor.Config = ImageCompressor.Config.BALANCED,
        enableThinking: Boolean = false
    ): Flow<String> = flow {
        // Step 1: Compress the image
        println("📸 Compressing image: ${imageFile.name}")
        val compressionResult = ImageCompressor.compress(imageFile, compressionConfig)
        println("   $compressionResult")

        // Step 2: Upload to COS (or use base64 fallback)
        val imageUrl = if (cosUploader != null) {
            println("☁️ Uploading to Tencent COS...")
            val uploadResult = cosUploader.uploadImageBytes(
                compressionResult.bytes,
                imageFile.nameWithoutExtension + ".jpg",
                compressionResult.format
            )
            uploadResult.getOrThrow().also {
                println("   Uploaded: $it")
            }
        } else {
            // Fallback: use base64 data URL (some APIs support this)
            println("⚠️ No COS uploader configured, using base64 encoding")
            val base64 = java.util.Base64.getEncoder().encodeToString(compressionResult.bytes)
            "data:${compressionResult.format};base64,$base64"
        }

        // Step 3: Stream the analysis
        println("🤖 Analyzing image with ${config.modelName}...")
        streamImageUnderstanding(imageUrl, prompt, enableThinking).collect { emit(it) }
    }

    /**
     * Non-streaming version of image file analysis.
     */
    suspend fun sendImageFromFile(
        imageFile: File,
        prompt: String,
        compressionConfig: ImageCompressor.Config = ImageCompressor.Config.BALANCED,
        enableThinking: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        // Step 1: Compress the image
        val compressionResult = ImageCompressor.compress(imageFile, compressionConfig)

        // Step 2: Upload to COS (or use base64 fallback)
        val imageUrl = if (cosUploader != null) {
            val uploadResult = cosUploader.uploadImageBytes(
                compressionResult.bytes,
                imageFile.nameWithoutExtension + ".jpg",
                compressionResult.format
            )
            uploadResult.getOrThrow()
        } else {
            val base64 = java.util.Base64.getEncoder().encodeToString(compressionResult.bytes)
            "data:${compressionResult.format};base64,$base64"
        }

        // Step 3: Analyze
        sendImageUnderstanding(imageUrl, prompt, enableThinking)
    }

    private fun buildImageRequest(
        imageUrl: String,
        prompt: String,
        enableThinking: Boolean,
        stream: Boolean
    ): String {
        val messages = buildJsonArray {
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") {
                            put("url", imageUrl)
                        }
                    }
                    addJsonObject {
                        put("type", "text")
                        put("text", prompt)
                    }
                }
            }
        }

        val requestJson = buildJsonObject {
            put("model", config.modelName)
            put("messages", messages)
            put("stream", stream)
            if (enableThinking) {
                putJsonObject("thinking") {
                    put("type", "enabled")
                }
            }
            if (config.temperature > 0) {
                put("temperature", config.temperature)
            }
            // GLM-4.6V max output tokens is 8192, don't send max_tokens if config exceeds this
            val maxOutputTokens = 8192
            if (config.maxTokens > 0 && config.maxTokens <= maxOutputTokens) {
                put("max_tokens", config.maxTokens)
            }
        }

        return json.encodeToString(requestJson)
    }

    fun close() {
        client.close()
        cosUploader?.close()
    }

    companion object {
        /**
         * Create a MultimodalLLMService with Tencent COS uploader.
         * 
         * @param apiKey GLM API key
         * @param modelName Vision model name (default: glm-4.6v)
         * @param cosSecretId Tencent COS SecretId
         * @param cosSecretKey Tencent COS SecretKey
         * @param cosBucket Tencent COS bucket name (format: bucket-appid)
         * @param cosRegion Tencent COS region (default: ap-guangzhou)
         */
        fun createWithCos(
            apiKey: String,
            modelName: String = "glm-4.6v",
            cosSecretId: String,
            cosSecretKey: String,
            cosBucket: String,
            cosRegion: String = "ap-guangzhou"
        ): MultimodalLLMService {
            val config = ModelConfig(
                provider = LLMProviderType.GLM,
                modelName = modelName,
                apiKey = apiKey,
                baseUrl = ModelRegistry.getDefaultBaseUrl(LLMProviderType.GLM)
            )
            
            val cosUploader = TencentCosUploader(
                secretId = cosSecretId,
                secretKey = cosSecretKey,
                bucket = cosBucket,
                region = cosRegion
            )
            
            return MultimodalLLMService(config, cosUploader)
        }

        /**
         * Create a MultimodalLLMService without COS uploader (uses base64 encoding).
         * 
         * Note: Base64 encoding may not work with all vision models.
         * GLM-4.6V specifically requires URLs.
         */
        fun createWithoutCos(
            apiKey: String,
            modelName: String = "glm-4.6v"
        ): MultimodalLLMService {
            val config = ModelConfig(
                provider = LLMProviderType.GLM,
                modelName = modelName,
                apiKey = apiKey,
                baseUrl = ModelRegistry.getDefaultBaseUrl(LLMProviderType.GLM)
            )
            
            return MultimodalLLMService(config, null)
        }
    }
}

