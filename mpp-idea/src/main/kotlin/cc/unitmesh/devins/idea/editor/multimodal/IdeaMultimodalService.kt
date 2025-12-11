package cc.unitmesh.devins.idea.editor.multimodal

import cc.unitmesh.config.CloudStorageConfig
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.llm.ModelConfig
import cc.unitmesh.llm.multimodal.ImageCompressor
import cc.unitmesh.llm.multimodal.MultimodalLLMService
import cc.unitmesh.llm.multimodal.TencentCosUploader
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Service providing default implementations for multimodal operations in IntelliJ IDEA.
 * Integrates with mpp-core's TencentCosUploader and MultimodalLLMService.
 *
 * Usage:
 * ```kotlin
 * val service = IdeaMultimodalService.getInstance(project)
 * val callbacks = service.createCallbacks()
 * 
 * val inputPanel = IdeaMultimodalInputPanel(
 *     project = project,
 *     parentDisposable = disposable,
 *     onImageUpload = callbacks.uploadCallback,
 *     onImageUploadBytes = callbacks.uploadBytesCallback,
 *     onMultimodalAnalysis = callbacks.analysisCallback
 * )
 * ```
 */
@Service(Service.Level.PROJECT)
class IdeaMultimodalService(private val project: Project) : Disposable {

    companion object {
        fun getInstance(project: Project): IdeaMultimodalService {
            return project.getService(IdeaMultimodalService::class.java)
        }
    }

    private var cosUploader: TencentCosUploader? = null
    private var multimodalLLMService: MultimodalLLMService? = null
    private var cloudStorageConfig: CloudStorageConfig? = null
    private var isInitialized = false

    /**
     * Initialize the service with configuration.
     * Call this before using the callbacks.
     */
    fun initialize() {
        if (isInitialized) return

        // Run blocking since this is typically called from UI thread during setup
        ApplicationManager.getApplication().executeOnPooledThread {
            runBlocking {
                try {
                    val config = ConfigManager.load()
                    cloudStorageConfig = config.getCloudStorage()

                    // Initialize COS uploader if configured
                    val cosConfig = cloudStorageConfig
                    if (cosConfig != null && cosConfig.isConfigured()) {
                        cosUploader = TencentCosUploader(
                            secretId = cosConfig.secretId,
                            secretKey = cosConfig.secretKey,
                            region = cosConfig.region,
                            bucket = cosConfig.bucket
                        )
                        println("✅ Tencent COS uploader initialized: ${cosConfig.bucket}")
                    }

                    // Initialize multimodal LLM service
                    val activeModelConfig = config.getActiveModelConfig()
                    if (activeModelConfig != null && activeModelConfig.isValid()) {
                        multimodalLLMService = MultimodalLLMService(activeModelConfig, cosUploader)
                        println("✅ Multimodal LLM service initialized: ${activeModelConfig.modelName}")
                    }

                    isInitialized = true
                } catch (e: Exception) {
                    println("❌ Failed to initialize IdeaMultimodalService: ${e.message}")
                }
            }
        }
    }

    /**
     * Check if the service is properly configured for multimodal operations.
     */
    fun isConfigured(): Boolean {
        if (!isInitialized) initialize()
        return cosUploader != null
    }

    /**
     * Check if vision analysis is available.
     */
    fun isVisionAvailable(): Boolean {
        if (!isInitialized) initialize()
        return multimodalLLMService != null
    }

    /**
     * Create callbacks for use with IdeaDevInInput or IdeaMultimodalInputPanel.
     */
    fun createCallbacks(): MultimodalCallbacks {
        if (!isInitialized) initialize()

        return MultimodalCallbacks(
            uploadCallback = if (cosUploader != null) { path, id, onProgress ->
                uploadImageFromPath(path, onProgress)
            } else null,

            uploadBytesCallback = if (cosUploader != null) { bytes, name, mime, id, onProgress ->
                uploadImageBytes(bytes, name, mime, onProgress)
            } else null,

            analysisCallback = if (multimodalLLMService != null) { urls, prompt, onChunk ->
                analyzeImages(urls, prompt, onChunk)
            } else null
        )
    }

    /**
     * Upload an image from file path.
     */
    suspend fun uploadImageFromPath(
        imagePath: String,
        onProgress: (Int) -> Unit = {}
    ): IdeaImageUploadResult = withContext(Dispatchers.IO) {
        val uploader = cosUploader ?: return@withContext IdeaImageUploadResult(
            success = false,
            error = "Cloud storage not configured"
        )

        try {
            val file = File(imagePath)
            if (!file.exists()) {
                return@withContext IdeaImageUploadResult(
                    success = false,
                    error = "File not found: $imagePath"
                )
            }

            onProgress(10)

            // Compress the image
            val compressionResult = ImageCompressor.compress(file, ImageCompressor.Config.BALANCED)
            onProgress(40)

            // Upload to COS
            val result = uploader.uploadImageBytes(
                compressionResult.bytes,
                file.nameWithoutExtension + ".jpg",
                compressionResult.format
            )
            onProgress(90)

            result.fold(
                onSuccess = { url ->
                    onProgress(100)
                    IdeaImageUploadResult(
                        success = true,
                        url = url,
                        originalSize = file.length(),
                        compressedSize = compressionResult.bytes.size.toLong()
                    )
                },
                onFailure = { e ->
                    IdeaImageUploadResult(
                        success = false,
                        error = e.message ?: "Upload failed"
                    )
                }
            )
        } catch (e: Exception) {
            IdeaImageUploadResult(
                success = false,
                error = e.message ?: "Upload failed"
            )
        }
    }

    /**
     * Upload image bytes directly.
     */
    suspend fun uploadImageBytes(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        onProgress: (Int) -> Unit = {}
    ): IdeaImageUploadResult = withContext(Dispatchers.IO) {
        val uploader = cosUploader ?: return@withContext IdeaImageUploadResult(
            success = false,
            error = "Cloud storage not configured"
        )

        try {
            onProgress(10)

            // Compress the image bytes
            val compressionResult = ImageCompressor.compress(bytes, ImageCompressor.Config.BALANCED)
            onProgress(40)

            // Upload to COS
            val result = uploader.uploadImageBytes(
                compressionResult.bytes,
                fileName.substringBeforeLast('.') + ".jpg",
                compressionResult.format
            )
            onProgress(90)

            result.fold(
                onSuccess = { url ->
                    onProgress(100)
                    IdeaImageUploadResult(
                        success = true,
                        url = url,
                        originalSize = bytes.size.toLong(),
                        compressedSize = compressionResult.bytes.size.toLong()
                    )
                },
                onFailure = { e ->
                    IdeaImageUploadResult(
                        success = false,
                        error = e.message ?: "Upload failed"
                    )
                }
            )
        } catch (e: Exception) {
            IdeaImageUploadResult(
                success = false,
                error = e.message ?: "Upload failed"
            )
        }
    }

    /**
     * Analyze images with vision model.
     */
    suspend fun analyzeImages(
        imageUrls: List<String>,
        prompt: String,
        onChunk: (String) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        val llmService = multimodalLLMService ?: return@withContext null

        try {
            // For multiple images, we analyze them one by one and combine results
            if (imageUrls.size == 1) {
                val chunks = StringBuilder()
                llmService.streamImageUnderstanding(imageUrls.first(), prompt).collect { chunk ->
                    chunks.append(chunk)
                    onChunk(chunk)
                }
                chunks.toString()
            } else {
                // Multiple images: analyze each and combine
                val results = imageUrls.mapIndexed { index, url ->
                    val imagePrompt = if (imageUrls.size > 1) {
                        "Image ${index + 1}/${imageUrls.size}: $prompt"
                    } else prompt

                    val chunks = StringBuilder()
                    llmService.streamImageUnderstanding(url, imagePrompt).collect { chunk ->
                        chunks.append(chunk)
                        onChunk(chunk)
                    }
                    "Image ${index + 1}: ${chunks.toString()}"
                }
                results.joinToString("\n\n")
            }
        } catch (e: Exception) {
            println("❌ Vision analysis failed: ${e.message}")
            null
        }
    }

    override fun dispose() {
        // Cleanup resources
        cosUploader = null
        multimodalLLMService = null
    }
}

/**
 * Container for multimodal callbacks.
 */
data class MultimodalCallbacks(
    val uploadCallback: ImageUploadCallback?,
    val uploadBytesCallback: ImageUploadBytesCallback?,
    val analysisCallback: MultimodalAnalysisCallback?
) {
    val isUploadConfigured: Boolean get() = uploadCallback != null || uploadBytesCallback != null
    val isAnalysisConfigured: Boolean get() = analysisCallback != null
}

