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
import kotlinx.serialization.Serializable
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
    private val config: ModelConfig
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client: HttpClient = HttpClientFactory.create()

    private val baseUrl: String
        get() = config.baseUrl.ifEmpty {
            ModelRegistry.getDefaultBaseUrl(LLMProviderType.GLM)
        }

    /**
     * Generate an image from a text prompt.
     *
     * @param prompt Text description of the image to generate
     * @param size Image size (default: "1024x1024")
     * @return Result containing the generated image URL or error
     */
    suspend fun generateImage(
        prompt: String,
        size: String = "1024x1024"
    ): ImageGenerationResult {
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
        client.close()
    }

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

