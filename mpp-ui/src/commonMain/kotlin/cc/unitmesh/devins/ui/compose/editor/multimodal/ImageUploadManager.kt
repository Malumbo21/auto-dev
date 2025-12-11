package cc.unitmesh.devins.ui.compose.editor.multimodal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val uploadBytesCallback: (suspend (imageBytes: ByteArray, fileName: String, mimeType: String, imageId: String, onProgress: (Int) -> Unit) -> ImageUploadResult)? = null,
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
     * Add an image and start uploading it immediately.
     * Uses StateFlow.update() for atomic state updates.
     */
    fun addImageAndUpload(image: AttachedImage) {
        // Add image with PENDING status using atomic update
        val newImage = image.copy(uploadStatus = ImageUploadStatus.PENDING)
        var shouldUpload = false

        _state.update { current ->
            if (!current.canAddMoreImages) {
                current // Return unchanged if can't add more
            } else {
                shouldUpload = true
                current.copy(images = current.images + newImage)
            }
        }

        // Start upload if callback is available and image was added
        if (shouldUpload && uploadCallback != null && image.path != null) {
            scope.launch {
                uploadImage(newImage)
            }
        }
    }

    /**
     * Add an image from bytes (e.g., pasted from clipboard) and start uploading it immediately.
     * Uses StateFlow.update() for atomic state updates.
     */
    fun addImageFromBytes(bytes: ByteArray, mimeType: String, suggestedName: String) {
        // Create AttachedImage from bytes
        val image = AttachedImage.fromBytes(bytes, mimeType, suggestedName)
        val newImage = image.copy(uploadStatus = ImageUploadStatus.PENDING)
        var shouldUpload = false

        _state.update { current ->
            if (!current.canAddMoreImages) {
                current // Return unchanged if can't add more
            } else {
                shouldUpload = true
                current.copy(images = current.images + newImage)
            }
        }

        // Start upload if bytes callback is available and image was added
        if (shouldUpload && uploadBytesCallback != null) {
            scope.launch {
                uploadImageBytes(newImage, bytes, mimeType, suggestedName)
            }
        }
    }

    /**
     * Upload an image from bytes to cloud storage.
     * All state updates use StateFlow.update() for atomic operations.
     */
    private suspend fun uploadImageBytes(image: AttachedImage, bytes: ByteArray, mimeType: String, fileName: String) {
        if (uploadBytesCallback == null) return

        val imageId = image.id
        updateStatus(imageId, ImageUploadStatus.COMPRESSING)

        try {
            // Update status to uploading
            updateStatus(imageId, ImageUploadStatus.UPLOADING)

            // Perform upload with progress callback
            val result = uploadBytesCallback.invoke(bytes, fileName, mimeType, imageId) { progress ->
                // Update progress, only if still uploading
                updateProgress(imageId, progress)
            }

            if (result.success && result.url != null) {
                // Update to completed with URL
                updateToCompleted(imageId, result)
                println("Pasted image uploaded: ${result.url}")
            } else {
                throw Exception(result.error ?: "Upload failed")
            }

        } catch (e: Exception) {
            updateToFailed(imageId, e.message ?: "Upload failed")
            onError?.invoke("Image upload failed: ${e.message}")
        }
    }

    /**
     * Remove an image from the state.
     * Uses StateFlow.update() for atomic state updates.
     */
    fun removeImage(imageId: String) {
        _state.update { current ->
            current.copy(images = current.images.filter { it.id != imageId })
        }
    }

    /**
     * Retry uploading a failed image.
     * Uses StateFlow.update() for atomic state updates.
     */
    fun retryUpload(image: AttachedImage) {
        if (image.path == null) return

        // Reset status to PENDING using atomic update
        _state.update { current ->
            current.copy(
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
        }

        scope.launch {
            uploadImage(image)
        }
    }

    /**
     * Clear all images.
     * Uses StateFlow.update() for atomic state updates.
     */
    fun clearImages() {
        _state.value = MultimodalState()
    }

    /**
     * Set analysis state.
     * Uses StateFlow.update() for atomic state updates.
     */
    fun setAnalyzing(isAnalyzing: Boolean, progress: String? = null) {
        _state.update { current ->
            current.copy(
                isAnalyzing = isAnalyzing,
                analysisProgress = progress
            )
        }
    }

    /**
     * Set analysis result.
     * Uses StateFlow.update() for atomic state updates.
     */
    fun setAnalysisResult(result: String?, error: String? = null) {
        _state.update { current ->
            current.copy(
                isAnalyzing = false,
                analysisProgress = null,
                analysisResult = result,
                analysisError = error
            )
        }
    }

    /**
     * Update analysis progress with streaming content.
     * Uses StateFlow.update() for atomic state updates.
     */
    fun updateAnalysisProgress(progress: String) {
        _state.update { current ->
            current.copy(analysisProgress = progress)
        }
    }

    /**
     * Set the vision model to use for analysis.
     * Uses StateFlow.update() for atomic state updates.
     */
    fun setVisionModel(model: String) {
        _state.update { current ->
            current.copy(visionModel = model)
        }
    }

    /**
     * Upload a single image to cloud storage.
     * All state updates use StateFlow.update() for atomic operations.
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

    private fun updateProgress(imageId: String, progress: Int) {
        _state.update { current ->
            current.copy(
                images = current.images.map { img ->
                    if (img.id == imageId &&
                        (img.uploadStatus == ImageUploadStatus.UPLOADING ||
                         img.uploadStatus == ImageUploadStatus.COMPRESSING)) {
                        img.copy(uploadProgress = progress)
                    } else img
                }
            )
        }
    }

    private fun updateToCompleted(imageId: String, result: ImageUploadResult) {
        _state.update { current ->
            current.copy(
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
    }

    private fun updateToFailed(imageId: String, error: String) {
        _state.update { current ->
            current.copy(
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
    }

    /**
     * Update image status atomically.
     * Prevents downgrading from terminal states unless retrying.
     * Uses StateFlow.update() for atomic state updates.
     */
    private fun updateStatus(imageId: String, status: ImageUploadStatus) {
        _state.update { current ->
            current.copy(
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
        }
    }
}

