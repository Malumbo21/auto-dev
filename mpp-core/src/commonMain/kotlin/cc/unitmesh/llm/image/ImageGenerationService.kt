package cc.unitmesh.llm.image

import cc.unitmesh.agent.tool.impl.http.HttpClientFactory
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.llm.LLMProviderType
import cc.unitmesh.llm.ModelConfig
import cc.unitmesh.llm.ModelRegistry
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/**
 * Image Generation Service using GLM CogView-3-Flash API.
 *
 * This service generates images from text prompts using the GLM CogView-3-Flash model.
 * It is designed to work across all platforms (JVM, JS, WASM) using Ktor HTTP client.
 *
 * API Reference: https://docs.bigmodel.cn/api-reference/模型-api/图像生成
 *
 * @param config ModelConfig containing GLM API key and optional base URL
 */
class ImageGenerationService(
    private val config: ModelConfig,
    private val client: HttpClient = HttpClientFactory.create()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inFlightMutex = Mutex()
    private val inFlight = mutableMapOf<RequestKey, kotlinx.coroutines.Deferred<ImageGenerationResult>>()

    private val baseUrl: String
        get() = config.baseUrl.ifEmpty {
            ModelRegistry.getDefaultBaseUrl(LLMProviderType.GLM)
        }

    /**
     * Generate an image from a text prompt.
     *
     * @param prompt Text description of the image to generate
     * @param size Image size (default: "1024x512")
     * @return Result containing the generated image URL or error
     */
    suspend fun generateImage(
        prompt: String,
        size: String = "1024x512"
    ): ImageGenerationResult {
        val key = RequestKey(prompt = prompt, size = size)

        // In-flight de-duplication:
        // - If the same (prompt, size) is already being generated, await that request.
        // - Otherwise start one request and let others await it.
        val deferred = inFlightMutex.withLock {
            inFlight[key] ?: run {
                val created = scope.async(start = CoroutineStart.LAZY) {
                    generateImageInternal(prompt = prompt, size = size)
                }
                inFlight[key] = created
                created.invokeOnCompletion {
                    scope.launch {
                        inFlightMutex.withLock {
                            if (inFlight[key] === created) {
                                inFlight.remove(key)
                            }
                        }
                    }
                }
                created.start()
                created
            }
        }

        return deferred.await()
    }

    private suspend fun generateImageInternal(prompt: String, size: String): ImageGenerationResult {
        return try {
            val requestBody = buildImageRequest(prompt, size)

            val response = client.post("${baseUrl}images/generations") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${config.apiKey}")
                setBody(requestBody)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                return ImageGenerationResult.Error("API request failed: ${response.status} - $errorBody")
            }

            val responseText = response.bodyAsText()
            parseImageResponse(responseText)
        } catch (e: Exception) {
            ImageGenerationResult.Error("Image generation failed: ${e.message}")
        }
    }

    private fun buildImageRequest(prompt: String, size: String): String {
        val requestJson = buildJsonObject {
            put("model", "cogview-3-flash")
            put("prompt", prompt)
            put("size", size)
        }
        return json.encodeToString(JsonObject.serializer(), requestJson)
    }

    private fun parseImageResponse(responseText: String): ImageGenerationResult {
        return try {
            val jsonResponse = json.parseToJsonElement(responseText).jsonObject

            // Check for error response
            val error = jsonResponse["error"]?.jsonObject
            if (error != null) {
                val message = error["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                return ImageGenerationResult.Error(message)
            }

            val data = jsonResponse["data"]?.jsonArray
            if (data.isNullOrEmpty()) {
                return ImageGenerationResult.Error("No image data in response")
            }

            val imageUrl = data[0].jsonObject["url"]?.jsonPrimitive?.contentOrNull
            if (imageUrl.isNullOrBlank()) {
                return ImageGenerationResult.Error("No image URL in response")
            }

            ImageGenerationResult.Success(imageUrl)
        } catch (e: Exception) {
            ImageGenerationResult.Error("Failed to parse response: ${e.message}")
        }
    }

    fun close() {
        scope.cancel()
        client.close()
    }

    private data class RequestKey(
        val prompt: String,
        val size: String
    )

    companion object {
        /**
         * Create an ImageGenerationService from a ModelConfig.
         */
        fun create(config: ModelConfig): ImageGenerationService {
            return ImageGenerationService(config)
        }

        suspend fun default(): ImageGenerationService? {
            val activeConfig = ConfigManager.load().getActiveConfig() ?: return null
            return create(activeConfig.toModelConfig())
        }

        /**
         * Create an ImageGenerationService with just an API key.
         */
        fun create(apiKey: String): ImageGenerationService {
            val config = ModelConfig(
                provider = LLMProviderType.GLM,
                modelName = "cogview-3-flash",
                apiKey = apiKey,
                baseUrl = ModelRegistry.getDefaultBaseUrl(LLMProviderType.GLM)
            )
            return ImageGenerationService(config)
        }
    }
}

/**
 * Result of image generation operation.
 */
sealed class ImageGenerationResult {
    data class Success(val imageUrl: String) : ImageGenerationResult()
    data class Error(val message: String) : ImageGenerationResult()
}

