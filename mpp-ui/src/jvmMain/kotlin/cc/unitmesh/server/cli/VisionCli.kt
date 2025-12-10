package cc.unitmesh.server.cli

import cc.unitmesh.config.ConfigManager
import cc.unitmesh.llm.LLMProviderType
import cc.unitmesh.llm.ModelConfig
import cc.unitmesh.llm.multimodal.ImageCompressor
import cc.unitmesh.llm.multimodal.MultimodalLLMService
import cc.unitmesh.llm.multimodal.TencentCosUploader
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Vision CLI - Test multimodal image understanding with GLM-4.6V
 * 
 * Features:
 * - Image compression to reduce costs
 * - Upload to Tencent COS for URL access
 * - Streaming response from GLM-4.6V
 * 
 * Usage:
 * ```bash
 * ./gradlew :mpp-ui:runVisionCli \
 *     -PvisionImage=/path/to/image.png \
 *     -PvisionPrompt="What is this image about?" \
 *     -PcosSecretId=YOUR_SECRET_ID \
 *     -PcosSecretKey=YOUR_SECRET_KEY \
 *     -PcosBucket=YOUR_BUCKET
 * ```
 * 
 * Environment variables can also be used:
 * - TENCENT_COS_SECRET_ID
 * - TENCENT_COS_SECRET_KEY
 * - TENCENT_COS_BUCKET
 * - TENCENT_COS_REGION (optional, default: ap-guangzhou)
 */
object VisionCli {
    private const val ANSI_RESET = "\u001B[0m"
    private const val ANSI_GREEN = "\u001B[32m"
    private const val ANSI_RED = "\u001B[31m"
    private const val ANSI_YELLOW = "\u001B[33m"
    private const val ANSI_BLUE = "\u001B[34m"
    private const val ANSI_CYAN = "\u001B[36m"
    private const val ANSI_GRAY = "\u001B[90m"
    private const val ANSI_BOLD = "\u001B[1m"

    @JvmStatic
    fun main(args: Array<String>) {
        println("$ANSI_BOLD$ANSI_CYAN")
        println("=".repeat(80))
        println("AutoDev Vision CLI (GLM-4.6V Multimodal)")
        println("=".repeat(80))
        println(ANSI_RESET)

        // Parse arguments from system properties or environment
        val imagePath = System.getProperty("visionImage") 
            ?: System.getenv("VISION_IMAGE")
            ?: args.getOrNull(0)
            ?: run {
                printUsage()
                return
            }

        val prompt = System.getProperty("visionPrompt")
            ?: System.getenv("VISION_PROMPT")
            ?: args.getOrNull(1)
            ?: "What is this image about? Please describe it in detail."

        val enableThinking = System.getProperty("enableThinking")?.toBoolean() ?: false

        // COS credentials
        val cosSecretId = System.getProperty("cosSecretId")
            ?: System.getenv("TENCENT_COS_SECRET_ID")
        val cosSecretKey = System.getProperty("cosSecretKey")
            ?: System.getenv("TENCENT_COS_SECRET_KEY")
        val cosBucket = System.getProperty("cosBucket")
            ?: System.getenv("TENCENT_COS_BUCKET")
        val cosRegion = System.getProperty("cosRegion")
            ?: System.getenv("TENCENT_COS_REGION")
            ?: "ap-guangzhou"

        // Validate image file
        val imageFile = File(imagePath)
        if (!imageFile.exists()) {
            println("${ANSI_RED}❌ Error: Image file not found: $imagePath$ANSI_RESET")
            return
        }

        println("${ANSI_BLUE}📂 Image: ${imageFile.absolutePath}$ANSI_RESET")
        println("${ANSI_BLUE}📏 Size: ${imageFile.length() / 1024}KB$ANSI_RESET")
        println("${ANSI_BLUE}💬 Prompt: $prompt$ANSI_RESET")
        if (enableThinking) {
            println("${ANSI_BLUE}🧠 Deep thinking: enabled$ANSI_RESET")
        }
        println()

        runBlocking {
            try {
                // Load GLM API key from config
                val configWrapper = ConfigManager.load()
                val glmConfig = configWrapper.getModelConfigByProvider(LLMProviderType.GLM.name)
                    ?: configWrapper.getActiveModelConfig()
                
                if (glmConfig == null || !glmConfig.isValid()) {
                    println("${ANSI_RED}❌ Error: No valid GLM configuration found.$ANSI_RESET")
                    println("${ANSI_YELLOW}Please configure GLM in ~/.autodev/config.yaml$ANSI_RESET")
                    return@runBlocking
                }

                println("${ANSI_GREEN}✓ Using GLM config: ${glmConfig.modelName}$ANSI_RESET")

                // Create multimodal service
                val multimodalService = if (cosSecretId != null && cosSecretKey != null && cosBucket != null) {
                    println("${ANSI_GREEN}✓ Tencent COS configured$ANSI_RESET")
                    MultimodalLLMService.createWithCos(
                        apiKey = glmConfig.apiKey,
                        modelName = "glm-4.6v",
                        cosSecretId = cosSecretId,
                        cosSecretKey = cosSecretKey,
                        cosBucket = cosBucket,
                        cosRegion = cosRegion
                    )
                } else {
                    println("${ANSI_YELLOW}⚠️ Tencent COS not configured, using base64 encoding$ANSI_RESET")
                    println("${ANSI_GRAY}   Note: GLM-4.6V may not support base64, please configure COS$ANSI_RESET")
                    MultimodalLLMService.createWithoutCos(
                        apiKey = glmConfig.apiKey,
                        modelName = "glm-4.6v"
                    )
                }

                println()
                println("${ANSI_BOLD}${ANSI_CYAN}═══════════════════════════════════════════════════════════════════════════════$ANSI_RESET")
                println("${ANSI_BOLD}${ANSI_CYAN}Response:$ANSI_RESET")
                println("${ANSI_GRAY}───────────────────────────────────────────────────────────────────────────────$ANSI_RESET")

                // Stream the response
                multimodalService.streamImageFromFile(
                    imageFile = imageFile,
                    prompt = prompt,
                    compressionConfig = ImageCompressor.Config.BALANCED,
                    enableThinking = enableThinking
                ).collect { chunk ->
                    print(chunk)
                    System.out.flush()
                }

                println()
                println("${ANSI_GRAY}───────────────────────────────────────────────────────────────────────────────$ANSI_RESET")
                println("${ANSI_GREEN}✅ Done$ANSI_RESET")

                multimodalService.close()

            } catch (e: Exception) {
                println("${ANSI_RED}❌ Error: ${e.message}$ANSI_RESET")
                e.printStackTrace()
            }
        }
    }

    private fun printUsage() {
        println("""
            ${ANSI_YELLOW}Usage:${ANSI_RESET}
              ./gradlew :mpp-ui:runVisionCli \
                  -PvisionImage=/path/to/image.png \
                  -PvisionPrompt="What is this image about?" \
                  -PcosSecretId=YOUR_SECRET_ID \
                  -PcosSecretKey=YOUR_SECRET_KEY \
                  -PcosBucket=YOUR_BUCKET
            
            ${ANSI_YELLOW}Environment Variables:${ANSI_RESET}
              VISION_IMAGE           - Path to image file
              VISION_PROMPT          - Text prompt
              TENCENT_COS_SECRET_ID  - Tencent COS Secret ID
              TENCENT_COS_SECRET_KEY - Tencent COS Secret Key
              TENCENT_COS_BUCKET     - Tencent COS Bucket (format: bucket-appid)
              TENCENT_COS_REGION     - Tencent COS Region (default: ap-guangzhou)
            
            ${ANSI_YELLOW}Example:${ANSI_RESET}
              ./gradlew :mpp-ui:runVisionCli \
                  -PvisionImage=./mpp-cli.png \
                  -PvisionPrompt="请描述这张图片中的内容"
        """.trimIndent())
    }
}

