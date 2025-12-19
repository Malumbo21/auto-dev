package cc.unitmesh.devins.ui.compose.agent.webedit

import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devins.ui.compose.agent.webedit.automation.VisionFallbackProvider
import cc.unitmesh.llm.LLMProviderType
import cc.unitmesh.llm.multimodal.MultimodalLLMService
import cc.unitmesh.llm.multimodal.TencentCosUploader
import cc.unitmesh.viewer.web.webedit.AccessibilityNode
import cc.unitmesh.viewer.web.webedit.WebEditAction
import cc.unitmesh.viewer.web.webedit.WebEditBridge
import cc.unitmesh.viewer.web.webedit.WebEditMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.util.*

/**
 * JVM implementation of WebEditVisionHelper.
 *
 * Uses MultimodalLLMService (GLM-4.6V) to understand screenshots when DOM-based
 * automation fails or needs visual confirmation.
 *
 * Workflow:
 * 1. Capture screenshot from WebView via bridge
 * 2. Upload to Tencent COS (required by GLM-4.6V)
 * 3. Send to vision model with prompt
 * 4. Parse response for actions or understanding
 *
 * Implements [VisionFallbackProvider] for integration with WebEditAutomation.
 */
actual class WebEditVisionHelper actual constructor(
    private val bridge: WebEditBridge
) : VisionFallbackProvider {
    private var cosUploader: TencentCosUploader? = null
    private var multimodalService: MultimodalLLMService? = null

    // Internal constructor for factory (internal so createWebEditVisionHelper can use it)
    internal constructor(
        bridge: WebEditBridge,
        cosUploader: TencentCosUploader?,
        multimodalService: MultimodalLLMService?
    ) : this(bridge) {
        this.cosUploader = cosUploader
        this.multimodalService = multimodalService
    }

    companion object {
        private const val VISION_MODEL_NAME = "glm-4.6v"
        private const val SCREENSHOT_TIMEOUT_MS = 10_000L
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Check if vision fallback is available.
     */
    actual override fun isAvailable(): Boolean = cosUploader != null && multimodalService != null

    /**
     * Capture a screenshot and get visual understanding from the LLM.
     *
     * @param userIntent The user's original intent/command
     * @param actionableContext Optional DOM context to include in prompt
     * @return Flow of LLM response chunks
     */
    actual fun analyzeScreenshot(
        userIntent: String,
        actionableContext: String?
    ): Flow<String> = flow {
        if (!isAvailable()) {
            emit("[Vision fallback not available - check COS and GLM config]")
            return@flow
        }

        // Step 1: Capture screenshot
        emit("📸 Capturing screenshot...\n")
        val screenshot = captureScreenshot()
        if (screenshot == null || screenshot.error != null) {
            emit("❌ Screenshot capture failed: ${screenshot?.error ?: "timeout"}\n")
            return@flow
        }

        val base64 = screenshot.base64
        if (base64.isNullOrEmpty()) {
            emit("❌ Screenshot is empty\n")
            return@flow
        }

        // Step 2: Upload to COS
        emit("☁️ Uploading to cloud storage...\n")
        val imageBytes = Base64.getDecoder().decode(base64)
        val fileName = "webedit_${System.currentTimeMillis()}.jpg"

        val uploadResult = cosUploader!!.uploadImageBytes(
            bytes = imageBytes,
            fileName = fileName,
            contentType = screenshot.mimeType ?: "image/jpeg"
        )

        if (uploadResult.isFailure) {
            emit("❌ Upload failed: ${uploadResult.exceptionOrNull()?.message}\n")
            return@flow
        }

        val imageUrl = uploadResult.getOrThrow()
        emit("✅ Uploaded: $imageUrl\n")

        // Step 3: Build prompt and call vision model
        emit("🤖 Analyzing with vision model...\n\n")

        val prompt = buildVisionPrompt(userIntent, actionableContext, screenshot)

        multimodalService!!.streamImageUnderstanding(
            imageUrl = imageUrl,
            prompt = prompt,
            enableThinking = false
        ).collect { chunk ->
            emit(chunk)
        }
    }

    /**
     * Use vision to suggest browser actions when DOM-based approach fails.
     *
     * @param userIntent The user's command
     * @param failedAction The action that failed (optional)
     * @param actionableElements Current actionable elements from DOM
     * @return List of suggested WebEditActions, or empty if vision fails
     */
    actual override suspend fun suggestActionsWithVision(
        userIntent: String,
        failedAction: WebEditAction?,
        actionableElements: List<AccessibilityNode>
    ): List<WebEditAction> {
        if (!isAvailable()) return emptyList()

        // Capture screenshot
        val screenshot = captureScreenshot() ?: return emptyList()
        if (screenshot.error != null || screenshot.base64.isNullOrEmpty()) return emptyList()

        // Upload to COS
        val imageBytes = Base64.getDecoder().decode(screenshot.base64)
        val fileName = "webedit_action_${System.currentTimeMillis()}.jpg"
        val uploadResult = cosUploader!!.uploadImageBytes(imageBytes, fileName, screenshot.mimeType ?: "image/jpeg")
        if (uploadResult.isFailure) return emptyList()

        val imageUrl = uploadResult.getOrThrow()

        // Build action-focused prompt
        val prompt = buildActionPrompt(userIntent, failedAction, actionableElements, screenshot)

        // Collect full response
        val responseBuilder = StringBuilder()
        multimodalService!!.streamImageUnderstanding(imageUrl, prompt, enableThinking = false)
            .collect { responseBuilder.append(it) }

        // Parse actions from response
        return parseActionsFromResponse(responseBuilder.toString())
    }

    /**
     * Capture screenshot from the WebView and wait for result.
     */
    private suspend fun captureScreenshot(): WebEditMessage.ScreenshotCaptured? {
        // Clear previous screenshot
        bridge.captureScreenshot(maxWidth = 1280, quality = 0.8)

        // Wait for screenshot result
        return withTimeoutOrNull(SCREENSHOT_TIMEOUT_MS) {
            // Poll for screenshot (StateFlow doesn't have a "waitForNext" built-in)
            var attempts = 0
            while (attempts < 100) {
                val current = bridge.lastScreenshot.value
                if (current != null && current.base64 != null) {
                    return@withTimeoutOrNull current
                }
                if (current?.error != null) {
                    return@withTimeoutOrNull current
                }
                delay(100)
                attempts++
            }
            null
        }
    }

    private fun buildVisionPrompt(
        userIntent: String,
        actionableContext: String?,
        screenshot: WebEditMessage.ScreenshotCaptured
    ): String {
        return buildString {
            appendLine("You are a browser automation assistant analyzing a webpage screenshot.")
            appendLine()
            appendLine("## Current Page")
            appendLine("- URL: ${screenshot.url ?: "unknown"}")
            appendLine("- Title: ${screenshot.title ?: "unknown"}")
            appendLine("- Viewport: ${screenshot.width}x${screenshot.height}")
            appendLine()
            appendLine("## User Intent")
            appendLine(userIntent)
            appendLine()
            if (!actionableContext.isNullOrBlank()) {
                appendLine("## Known Interactive Elements (from DOM)")
                appendLine(actionableContext)
                appendLine()
            }
            appendLine("## Task")
            appendLine("Analyze the screenshot and help the user accomplish their intent.")
            appendLine("If the user wants to interact with the page, identify:")
            appendLine("1. What element they should interact with (describe it visually)")
            appendLine("2. What action to take (click, type, etc.)")
            appendLine("3. A CSS selector if you can identify one from visible text/attributes")
            appendLine()
            appendLine("Be concise and actionable.")
        }
    }

    private fun buildActionPrompt(
        userIntent: String,
        failedAction: WebEditAction?,
        actionableElements: List<AccessibilityNode>,
        screenshot: WebEditMessage.ScreenshotCaptured
    ): String {
        return buildString {
            appendLine("You are a browser automation assistant. The user wants to:")
            appendLine(userIntent)
            appendLine()
            appendLine("Page: ${screenshot.url} - ${screenshot.title}")
            appendLine()
            if (failedAction != null) {
                appendLine("## Previous Attempt Failed")
                appendLine("Action: ${failedAction.action}, selector: ${failedAction.selector}")
                appendLine()
            }
            if (actionableElements.isNotEmpty()) {
                appendLine("## Available Elements (from DOM)")
                actionableElements.take(20).forEach { el ->
                    appendLine("- [${el.role}] \"${el.name}\" → ${el.selector}")
                }
                appendLine()
            }
            appendLine("## Output Format")
            appendLine("Output a JSON array of actions. Each action should have:")
            appendLine("- action: \"click\" | \"type\" | \"pressKey\" | \"select\"")
            appendLine("- selector: CSS selector string")
            appendLine("- text: (for type) text to enter")
            appendLine("- key: (for pressKey) key name like \"Enter\"")
            appendLine()
            appendLine("Example: [{\"action\":\"click\",\"selector\":\"button.search\"}]")
            appendLine()
            appendLine("Look at the screenshot and output ONLY the JSON array, no explanation.")
        }
    }

    private fun parseActionsFromResponse(response: String): List<WebEditAction> {
        return try {
            // Extract JSON array from response
            val cleaned = response.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            // Find JSON array bounds
            val start = cleaned.indexOf('[')
            val end = cleaned.lastIndexOf(']')
            if (start == -1 || end == -1 || end <= start) return emptyList()

            val jsonArray = cleaned.substring(start, end + 1)
            json.decodeFromString<List<WebEditAction>>(jsonArray)
        } catch (e: Exception) {
            println("[WebEditVisionHelper] Failed to parse actions: ${e.message}")
            emptyList()
        }
    }

    /**
     * Clean up resources.
     */
    actual fun close() {
        multimodalService?.close()
        cosUploader?.close()
    }
}

/**
 * JVM implementation: Create WebEditVisionHelper from ConfigManager settings.
 */
actual suspend fun createWebEditVisionHelper(bridge: WebEditBridge): WebEditVisionHelper? {
    return try {
        val config = ConfigManager.load()

        // Initialize COS uploader
        val cloudStorage = config.getCloudStorage()
        println("[WebEditVisionHelper] 📋 Cloud storage config:")
        println("   provider: ${cloudStorage.provider}")
        println("   secretId: ${if (cloudStorage.secretId.isNotBlank()) "${cloudStorage.secretId.take(8)}..." else "(empty)"}")
        println("   secretKey: ${if (cloudStorage.secretKey.isNotBlank()) "***" else "(empty)"}")
        println("   bucket: ${cloudStorage.bucket}")
        println("   region: ${cloudStorage.region}")
        println("   isConfigured(): ${cloudStorage.isConfigured()}")

        val cosUploader = if (cloudStorage.isConfigured()) {
            println("[WebEditVisionHelper] ✅ Creating TencentCosUploader...")
            TencentCosUploader(
                secretId = cloudStorage.secretId,
                secretKey = cloudStorage.secretKey,
                region = cloudStorage.region,
                bucket = cloudStorage.bucket
            )
        } else {
            println("[WebEditVisionHelper] ⚠️ Cloud storage not configured - vision fallback disabled")
            println("   Check: secretId=${cloudStorage.secretId.isNotBlank()}, secretKey=${cloudStorage.secretKey.isNotBlank()}, bucket=${cloudStorage.bucket.isNotBlank()}, region=${cloudStorage.region.isNotBlank()}")
            null
        }

        // Initialize multimodal LLM service
        println("[WebEditVisionHelper] 📋 Looking for GLM/ZhiPu config...")
        val glmConfig = config.getModelConfigByProvider("GLM")
            ?: config.getModelConfigByProvider("zhipu")
            ?: config.getModelConfigByProvider("ZHIPU")

        if (glmConfig != null) {
            println("[WebEditVisionHelper] ✅ Found GLM config: ${glmConfig.modelName}, apiKey=${if (glmConfig.apiKey.isNotBlank()) "${glmConfig.apiKey.take(8)}..." else "(empty)"}")
        } else {
            println("[WebEditVisionHelper] ⚠️ No GLM config found by provider name")
            val activeConfig = config.getActiveModelConfig()
            if (activeConfig != null) {
                println("[WebEditVisionHelper] 📋 Active config: provider=${activeConfig.provider}, model=${activeConfig.modelName}")
            }
        }

        val multimodalService = if (glmConfig != null) {
            val visionConfig = glmConfig.copy(modelName = "glm-4.6v")
            println("[WebEditVisionHelper] ✅ Creating MultimodalLLMService with ${visionConfig.modelName}")
            MultimodalLLMService(visionConfig, cosUploader)
        } else {
            // Try active config if it's GLM
            val activeConfig = config.getActiveModelConfig()
            if (activeConfig != null && activeConfig.provider == LLMProviderType.GLM) {
                println("[WebEditVisionHelper] ✅ Using active GLM config: ${activeConfig.modelName}")
                val visionConfig = activeConfig.copy(modelName = "glm-4.6v")
                MultimodalLLMService(visionConfig, cosUploader)
            } else {
                println("[WebEditVisionHelper] ⚠️ No GLM/ZhiPu config found - vision fallback disabled")
                null
            }
        }

        if (cosUploader != null && multimodalService != null) {
            println("[WebEditVisionHelper] ✅ Vision helper initialized")
            // Use private constructor
            WebEditVisionHelper(bridge, cosUploader, multimodalService)
        } else {
            null
        }
    } catch (e: Exception) {
        println("[WebEditVisionHelper] ❌ Failed to initialize: ${e.message}")
        null
    }
}
