package cc.unitmesh.devins.ui.compose.editor.multimodal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.jvm.Synchronized

/**
 * Manages image upload state and operations.
 * This is a standalone component that can be easily tested.
 *
 * Uses StateFlow for thread-safe state management and ensures all state updates
 * happen atomically using the update() function.
 */
class ImageUploadManager(
    private val scope: CoroutineScope,
    private val uploadCallback: (suspend (imagePath: String, imageId: String, onProgress: (Int) -> Unit) -> ImageUploadResult)? = null,
    private val onError: ((String) -> Unit)? = null
) {
    private val _state = MutableStateFlow(MultimodalState())
    val state: StateFlow<MultimodalState> = _state.asStateFlow()

    /**
     * Get current state snapshot
     */
    fun getState(): MultimodalState = _state.value

    /**
     * Check if upload callback is configured
     */
    fun isUploadConfigured(): Boolean = uploadCallback != null

    /**
     * Add an image and start uploading it immediately
     */
    @Synchronized
    fun addImageAndUpload(image: AttachedImage) {
        val current = _state.value
        if (!current.canAddMoreImages) return

        // Add image with PENDING status
        val newImage = image.copy(uploadStatus = ImageUploadStatus.PENDING)
        _state.value = current.copy(images = current.images + newImage)

        // Start upload if callback is available
        if (uploadCallback != null && image.path != null) {
            scope.launch {
                uploadImage(newImage)
            }
        }
    }

    /**
     * Remove an image from the state
     */
    @Synchronized
    fun removeImage(imageId: String) {
        val current = _state.value
        _state.value = current.copy(images = current.images.filter { it.id != imageId })
    }

    /**
     * Retry uploading a failed image
     */
    @Synchronized
    fun retryUpload(image: AttachedImage) {
        if (image.path == null) return

        // Reset status to PENDING
        val current = _state.value
        _state.value = current.copy(
            images = current.images.map { img ->
                if (img.id == image.id) {
                    img.copy(
                        uploadStatus = ImageUploadStatus.PENDING,
                        uploadError = null,
                        uploadProgress = 0
                    )
                } else img
            }
        )

        scope.launch {
            uploadImage(image)
        }
    }

    /**
     * Clear all images
     */
    @Synchronized
    fun clearImages() {
        _state.value = MultimodalState()
    }

    /**
     * Set analysis state
     */
    @Synchronized
    fun setAnalyzing(isAnalyzing: Boolean, progress: String? = null) {
        val current = _state.value
        _state.value = current.copy(
            isAnalyzing = isAnalyzing,
            analysisProgress = progress
        )
    }

    /**
     * Set analysis result
     */
    @Synchronized
    fun setAnalysisResult(result: String?, error: String? = null) {
        val current = _state.value
        _state.value = current.copy(
            isAnalyzing = false,
            analysisProgress = null,
            analysisResult = result,
            analysisError = error
        )
    }

    /**
     * Update analysis progress with streaming content
     */
    @Synchronized
    fun updateAnalysisProgress(progress: String) {
        val current = _state.value
        _state.value = current.copy(
            analysisProgress = progress
        )
    }

    /**
     * Set the vision model to use for analysis
     */
    @Synchronized
    fun setVisionModel(model: String) {
        val current = _state.value
        _state.value = current.copy(
            visionModel = model
        )
    }

    /**
     * Upload a single image to cloud storage.
     * All state updates use direct assignment with synchronized blocks.
     */
    private suspend fun uploadImage(image: AttachedImage) {
        if (uploadCallback == null || image.path == null) return

        val imageId = image.id
        updateStatus(imageId, ImageUploadStatus.COMPRESSING)

        try {
            // Update status to uploading
            updateStatus(imageId, ImageUploadStatus.UPLOADING)

            // Perform upload with progress callback
            val result = uploadCallback.invoke(image.path, imageId) { progress ->
                // Update progress, only if still uploading
                updateProgress(imageId, progress)
            }

            if (result.success && result.url != null) {
                // Update to completed with URL
                updateToCompleted(imageId, result)
                println("✅ Image uploaded: ${result.url}")
            } else {
                throw Exception(result.error ?: "Upload failed")
            }

        } catch (e: Exception) {
            updateToFailed(imageId, e.message ?: "Upload failed")
            onError?.invoke("Image upload failed: ${e.message}")
        }
    }

    @Synchronized
    private fun updateProgress(imageId: String, progress: Int) {
        val current = _state.value
        _state.value = current.copy(
            images = current.images.map { img ->
                if (img.id == imageId &&
                    (img.uploadStatus == ImageUploadStatus.UPLOADING ||
                     img.uploadStatus == ImageUploadStatus.COMPRESSING)) {
                    img.copy(uploadProgress = progress)
                } else img
            }
        )
    }

    @Synchronized
    private fun updateToCompleted(imageId: String, result: ImageUploadResult) {
        val current = _state.value
        _state.value = current.copy(
            images = current.images.map { img ->
                if (img.id == imageId) {
                    img.copy(
                        uploadStatus = ImageUploadStatus.COMPLETED,
                        uploadedUrl = result.url,
                        uploadProgress = 100,
                        originalSize = result.originalSize,
                        compressedSize = result.compressedSize
                    )
                } else img
            }
        )
    }

    @Synchronized
    private fun updateToFailed(imageId: String, error: String) {
        val current = _state.value
        _state.value = current.copy(
            images = current.images.map { img ->
                if (img.id == imageId) {
                    img.copy(
                        uploadStatus = ImageUploadStatus.FAILED,
                        uploadError = error
                    )
                } else img
            }
        )
    }

    /**
     * Update image status atomically.
     * Prevents downgrading from terminal states unless retrying.
     * Uses synchronized block to ensure thread safety.
     */
    @Synchronized
    private fun updateStatus(imageId: String, status: ImageUploadStatus) {
        val current = _state.value
        val newState = current.copy(
            images = current.images.map { img ->
                if (img.id == imageId) {
                    val isTerminal = img.uploadStatus == ImageUploadStatus.COMPLETED ||
                        img.uploadStatus == ImageUploadStatus.FAILED
                    val isRetry = status == ImageUploadStatus.PENDING
                    if (isTerminal && !isRetry) {
                        img // Don't update terminal states
                    } else {
                        img.copy(uploadStatus = status)
                    }
                } else img
            }
        )
        _state.value = newState
    }
}

