package cc.unitmesh.devins.idea.editor.multimodal

import cc.unitmesh.config.CloudStorageConfig
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.llm.LLMProviderType
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
        /** Default vision model name for GLM/ZhiPu */
        const val VISION_MODEL_NAME = "glm-4.6v"
        
        fun getInstance(project: Project): IdeaMultimodalService {
            return project.getService(IdeaMultimodalService::class.java)
        }
    }

    private var cosUploader: TencentCosUploader? = null
    private var multimodalLLMService: MultimodalLLMService? = null
    private var cloudStorageConfig: CloudStorageConfig? = null
    private var isInitialized = false
    
    /** Current vision model being used */
    var currentVisionModel: String = VISION_MODEL_NAME
        private set

    /**
     * Initialize the service with configuration.
     * This method is safe to call from any thread - it won't block the EDT.
     * Uses a CountDownLatch to wait for initialization without blocking EDT.
     */
    fun initialize() {
        if (isInitialized) return

        // Use a lock to prevent multiple simultaneous initializations
        synchronized(this) {
            if (isInitialized) return
            
            // Check if we're on EDT - if so, don't wait for completion
            val isEdt = ApplicationManager.getApplication().isDispatchThread
            val latch = if (!isEdt) java.util.concurrent.CountDownLatch(1) else null
            
            // Run initialization on pooled thread to avoid blocking EDT
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
                        } else {
                            println("⚠️ Cloud storage not configured - image upload will be disabled")
                        }

                        // Initialize multimodal LLM service with vision-capable model
                        // Look for GLM/ZhiPu config specifically, as it's the primary supported vision provider
                        val glmConfig = config.getModelConfigByProvider("GLM") 
                            ?: config.getModelConfigByProvider("zhipu")
                            ?: config.getModelConfigByProvider("ZHIPU")
                        
                        println("📋 Looking for GLM/ZhiPu vision config...")
                        if (glmConfig != null) {
                            println("   Found GLM config with API Key: ${glmConfig.apiKey.take(10)}...")
                            
                            // Create vision-specific ModelConfig with glm-4.6v model name
                            val visionModelConfig = glmConfig.copy(
                                modelName = VISION_MODEL_NAME  // Force vision model
                            )
                            multimodalLLMService = MultimodalLLMService(visionModelConfig, cosUploader)
                            currentVisionModel = VISION_MODEL_NAME
                            println("✅ Multimodal LLM service initialized with vision model: ${visionModelConfig.modelName}")
                        } else {
                            // Fall back to active config if it's GLM
                            val activeConfig = config.getActiveModelConfig()
                            if (activeConfig != null && activeConfig.provider == LLMProviderType.GLM) {
                                val visionModelConfig = activeConfig.copy(modelName = VISION_MODEL_NAME)
                                multimodalLLMService = MultimodalLLMService(visionModelConfig, cosUploader)
                                currentVisionModel = VISION_MODEL_NAME
                                println("✅ Using active GLM config with vision model: ${visionModelConfig.modelName}")
                            } else {
                                println("⚠️ No GLM/ZhiPu config found - vision analysis will be disabled")
                                println("   Hint: Add a GLM/ZhiPu configuration to enable vision analysis")
                                println("   Available providers: ${config.getAllConfigs().map { it.provider }}")
                            }
                        }

                        isInitialized = true
                        println("✅ IdeaMultimodalService initialized (COS: ${cosUploader != null}, Vision: ${multimodalLLMService != null})")
                    } catch (e: Exception) {
                        println("❌ Failed to initialize IdeaMultimodalService: ${e.message}")
                        e.printStackTrace()
                        isInitialized = true // Mark as initialized to prevent retry loops
                    } finally {
                        latch?.countDown()
                    }
                }
            }
            
            // If not on EDT, wait for initialization to complete (with timeout)
            if (!isEdt && latch != null) {
                try {
                    latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    println("⚠️ Initialization wait interrupted")
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
     * Callbacks check for service availability at CALL time, not creation time.
     */
    fun createCallbacks(): MultimodalCallbacks {
        if (!isInitialized) {
            println("⚠️ IdeaMultimodalService not initialized, initializing now...")
            initialize()
        }

        val hasUploader = cosUploader != null
        val hasVision = multimodalLLMService != null
        
        println("📦 Creating multimodal callbacks: upload=$hasUploader, vision=$hasVision")

        return MultimodalCallbacks(
            // Upload callback - check at call time
            uploadCallback = { path, id, onProgress ->
                if (cosUploader != null) {
                    uploadImageFromPath(path, onProgress)
                } else {
                    println("⚠️ Upload callback: cosUploader is null")
                    IdeaImageUploadResult(success = false, error = "Cloud storage not configured")
                }
            },

            // Upload bytes callback - check at call time
            uploadBytesCallback = { bytes, name, mime, id, onProgress ->
                if (cosUploader != null) {
                    uploadImageBytes(bytes, name, mime, onProgress)
                } else {
                    println("⚠️ Upload bytes callback: cosUploader is null")
                    IdeaImageUploadResult(success = false, error = "Cloud storage not configured")
                }
            },

            // Vision analysis callback - ALWAYS created, checks at call time
            // This is the key fix: don't return null callback, return callback that checks at runtime
            analysisCallback = { urls, prompt, onChunk ->
                println("🔍 Vision analysis requested: ${urls.size} images, prompt='${prompt.take(50)}...'")
                println("   multimodalLLMService available: ${multimodalLLMService != null}")
                
                if (multimodalLLMService != null) {
                    analyzeImages(urls, prompt, onChunk)
                } else {
                    // Try to re-initialize if service is not available
                    println("⚠️ multimodalLLMService is null, attempting re-initialization...")
                    if (!isInitialized) {
                        initialize()
                    }
                    
                    if (multimodalLLMService != null) {
                        println("✅ Re-initialization successful, proceeding with analysis")
                        analyzeImages(urls, prompt, onChunk)
                    } else {
                        println("❌ Vision service still not available after re-init")
                        onChunk("[Vision analysis unavailable - service not configured]")
                        null
                    }
                }
            }
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
 * Note: Callbacks are always non-null now (they check service availability at call time)
 */
data class MultimodalCallbacks(
    val uploadCallback: ImageUploadCallback,
    val uploadBytesCallback: ImageUploadBytesCallback,
    val analysisCallback: MultimodalAnalysisCallback
) {
    // These now always return true since callbacks are always provided
    val isUploadConfigured: Boolean get() = true
    val isAnalysisConfigured: Boolean get() = true
}

