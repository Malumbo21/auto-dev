package cc.unitmesh.devins.idea.editor.multimodal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Result of image upload operation.
 */
data class IdeaImageUploadResult(
    val success: Boolean,
    val url: String? = null,
    val error: String? = null,
    val originalSize: Long = 0,
    val compressedSize: Long = 0
)

/**
 * Callback for image upload.
 */
typealias ImageUploadCallback = suspend (imagePath: String, imageId: String, onProgress: (Int) -> Unit) -> IdeaImageUploadResult

/**
 * Callback for image upload from bytes.
 */
typealias ImageUploadBytesCallback = suspend (imageBytes: ByteArray, fileName: String, mimeType: String, imageId: String, onProgress: (Int) -> Unit) -> IdeaImageUploadResult

/**
 * Callback for multimodal analysis.
 */
typealias MultimodalAnalysisCallback = suspend (imageUrls: List<String>, prompt: String, onChunk: (String) -> Unit) -> String?

/**
 * Listener for multimodal state changes.
 */
interface IdeaMultimodalStateListener {
    fun onStateChanged(state: IdeaMultimodalState)
}

/**
 * Manages image upload state and operations for IntelliJ IDEA.
 * Uses thread-safe state management and IntelliJ's threading APIs.
 */
class IdeaImageUploadManager(
    private val project: Project,
    private val uploadCallback: ImageUploadCallback? = null,
    private val uploadBytesCallback: ImageUploadBytesCallback? = null,
    private val onError: ((String) -> Unit)? = null
) : Disposable {
    
    private val stateRef = AtomicReference(IdeaMultimodalState())
    private val listeners = CopyOnWriteArrayList<IdeaMultimodalStateListener>()
    
    /** Get current state snapshot */
    val state: IdeaMultimodalState get() = stateRef.get()
    
    /** Check if upload callback is configured */
    fun isUploadConfigured(): Boolean = uploadCallback != null || uploadBytesCallback != null
    
    /**
     * Add a state change listener.
     */
    fun addListener(listener: IdeaMultimodalStateListener) {
        listeners.add(listener)
    }
    
    /**
     * Remove a state change listener.
     */
    fun removeListener(listener: IdeaMultimodalStateListener) {
        listeners.remove(listener)
    }
    
    // Debounce mechanism for UI updates
    private var lastUiUpdateTime = 0L
    private val uiUpdateDebounceMs = 100L // Only update UI every 100ms
    
    private fun updateState(updater: (IdeaMultimodalState) -> IdeaMultimodalState) {
        updateStateInternal(forceUiUpdate = false, updater)
    }
    
    private fun updateStateForced(updater: (IdeaMultimodalState) -> IdeaMultimodalState) {
        updateStateInternal(forceUiUpdate = true, updater)
    }
    
    private fun updateStateInternal(forceUiUpdate: Boolean, updater: (IdeaMultimodalState) -> IdeaMultimodalState) {
        var current: IdeaMultimodalState
        var next: IdeaMultimodalState
        do {
            current = stateRef.get()
            next = updater(current)
        } while (!stateRef.compareAndSet(current, next))
        
        // Debounce UI updates to prevent EDT spam during streaming
        val now = System.currentTimeMillis()
        val shouldUpdate = forceUiUpdate || (now - lastUiUpdateTime) >= uiUpdateDebounceMs
        
        if (shouldUpdate && listeners.isNotEmpty()) {
            lastUiUpdateTime = now
            // Notify listeners on EDT
            ApplicationManager.getApplication().invokeLater {
                listeners.forEach { it.onStateChanged(stateRef.get()) } // Get latest state
            }
        }
    }
    
    /**
     * Add an image and start uploading it immediately.
     */
    fun addImageAndUpload(image: IdeaAttachedImage) {
        val newImage = image.copy(uploadStatus = IdeaImageUploadStatus.PENDING)
        var shouldUpload = false
        
        updateState { current ->
            if (!current.canAddMoreImages) {
                current
            } else {
                shouldUpload = true
                current.copy(images = current.images + newImage)
            }
        }
        
        // Start upload in background
        if (shouldUpload && uploadCallback != null && image.path != null) {
            ApplicationManager.getApplication().executeOnPooledThread {
                kotlinx.coroutines.runBlocking {
                    uploadImage(newImage)
                }
            }
        }
    }
    
    /**
     * Add an image from bytes (e.g., pasted from clipboard) and start uploading.
     */
    fun addImageFromBytes(bytes: ByteArray, mimeType: String, suggestedName: String) {
        val image = IdeaAttachedImage.fromBytes(bytes, mimeType, suggestedName)
        val newImage = image.copy(uploadStatus = IdeaImageUploadStatus.PENDING)
        var shouldUpload = false
        
        updateState { current ->
            if (!current.canAddMoreImages) {
                current
            } else {
                shouldUpload = true
                current.copy(images = current.images + newImage)
            }
        }
        
        // Start upload in background
        if (shouldUpload && uploadBytesCallback != null) {
            ApplicationManager.getApplication().executeOnPooledThread {
                kotlinx.coroutines.runBlocking {
                    uploadImageBytes(newImage, bytes, mimeType, suggestedName)
                }
            }
        }
    }
    
    /**
     * Add an image from BufferedImage (e.g., from clipboard).
     * This method is safe to call from EDT - heavy operations are done in background.
     */
    fun addImageFromBufferedImage(image: java.awt.image.BufferedImage, suggestedName: String? = null) {
        val name = suggestedName ?: "pasted_image_${System.currentTimeMillis()}.png"
        
        // Create placeholder immediately for UI feedback
        val placeholderId = "pending_${System.currentTimeMillis()}"
        val placeholder = IdeaAttachedImage(
            id = placeholderId,
            name = name,
            path = null,
            bytes = null,
            thumbnail = image,  // Show thumbnail immediately
            mimeType = "image/png",
            originalSize = 0,
            uploadStatus = IdeaImageUploadStatus.PENDING
        )
        
        // Add placeholder to state
        updateState { current ->
            if (!current.canAddMoreImages) current
            else current.copy(images = current.images + placeholder)
        }
        
        // Encode image in background thread to avoid blocking EDT
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val attachedImage = IdeaAttachedImage.fromBufferedImage(image, name)
                val bytes = attachedImage.bytes
                
                if (bytes == null) {
                    // Remove placeholder if encoding failed
                    updateState { current ->
                        current.copy(images = current.images.filter { it.id != placeholderId })
                    }
                    return@executeOnPooledThread
                }
                
                // Replace placeholder with real image and start upload
                updateState { current ->
                    current.copy(images = current.images.map { img ->
                        if (img.id == placeholderId) attachedImage.copy(
                            id = placeholderId,
                            uploadStatus = IdeaImageUploadStatus.PENDING
                        ) else img
                    })
                }
                
                // Continue with upload
                if (uploadBytesCallback != null) {
                    kotlinx.coroutines.runBlocking {
                        uploadImageBytes(attachedImage.copy(id = placeholderId), bytes, "image/png", name)
                    }
                }
            } catch (e: Exception) {
                println("Error processing pasted image: ${e.message}")
                // Remove placeholder on error
                updateState { current ->
                    current.copy(images = current.images.filter { it.id != placeholderId })
                }
            }
        }
    }
    
    /**
     * Remove an image from the state.
     */
    fun removeImage(imageId: String) {
        updateState { current ->
            current.copy(images = current.images.filter { it.id != imageId })
        }
    }
    
    /**
     * Retry uploading a failed image.
     */
    fun retryUpload(image: IdeaAttachedImage) {
        // Reset status to PENDING
        updateState { current ->
            current.copy(
                images = current.images.map { img ->
                    if (img.id == image.id) {
                        img.copy(
                            uploadStatus = IdeaImageUploadStatus.PENDING,
                            uploadError = null,
                            uploadProgress = 0
                        )
                    } else img
                }
            )
        }
        
        // Retry upload
        if (image.path != null && uploadCallback != null) {
            ApplicationManager.getApplication().executeOnPooledThread {
                kotlinx.coroutines.runBlocking {
                    uploadImage(image)
                }
            }
        } else if (image.bytes != null && uploadBytesCallback != null) {
            ApplicationManager.getApplication().executeOnPooledThread {
                kotlinx.coroutines.runBlocking {
                    uploadImageBytes(image, image.bytes, image.mimeType, image.name)
                }
            }
        }
    }
    
    /**
     * Clear all images.
     */
    fun clearImages() {
        updateStateForced { IdeaMultimodalState() }
    }
    
    /**
     * Set analysis state.
     */
    fun setAnalyzing(isAnalyzing: Boolean, progress: String? = null) {
        updateStateForced { current ->
            current.copy(
                isAnalyzing = isAnalyzing,
                analysisProgress = progress
            )
        }
    }
    
    /**
     * Set analysis result.
     */
    fun setAnalysisResult(result: String?, error: String? = null) {
        updateStateForced { current ->
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
     */
    fun updateAnalysisProgress(progress: String) {
        updateState { current ->
            current.copy(analysisProgress = progress)
        }
    }
    
    /**
     * Set the vision model to use for analysis.
     */
    fun setVisionModel(model: String) {
        updateState { current ->
            current.copy(visionModel = model)
        }
    }
    
    /**
     * Upload a single image to cloud storage.
     */
    private suspend fun uploadImage(image: IdeaAttachedImage) {
        if (uploadCallback == null || image.path == null) return
        
        val imageId = image.id
        updateStatus(imageId, IdeaImageUploadStatus.COMPRESSING)
        
        try {
            updateStatus(imageId, IdeaImageUploadStatus.UPLOADING)
            
            val result = uploadCallback.invoke(image.path, imageId) { progress ->
                updateProgress(imageId, progress)
            }
            
            if (result.success && result.url != null) {
                updateToCompleted(imageId, result)
                println("Image uploaded: ${result.url}")
            } else {
                throw Exception(result.error ?: "Upload failed")
            }
        } catch (e: Exception) {
            updateToFailed(imageId, e.message ?: "Upload failed")
            onError?.invoke("Image upload failed: ${e.message}")
        }
    }
    
    /**
     * Upload an image from bytes to cloud storage.
     */
    private suspend fun uploadImageBytes(image: IdeaAttachedImage, bytes: ByteArray, mimeType: String, fileName: String) {
        if (uploadBytesCallback == null) return
        
        val imageId = image.id
        updateStatus(imageId, IdeaImageUploadStatus.COMPRESSING)
        
        try {
            updateStatus(imageId, IdeaImageUploadStatus.UPLOADING)
            
            val result = uploadBytesCallback.invoke(bytes, fileName, mimeType, imageId) { progress ->
                updateProgress(imageId, progress)
            }
            
            if (result.success && result.url != null) {
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
    
    private fun updateProgress(imageId: String, progress: Int) {
        updateState { current ->
            current.copy(
                images = current.images.map { img ->
                    if (img.id == imageId &&
                        (img.uploadStatus == IdeaImageUploadStatus.UPLOADING ||
                         img.uploadStatus == IdeaImageUploadStatus.COMPRESSING)) {
                        img.copy(uploadProgress = progress)
                    } else img
                }
            )
        }
    }
    
    private fun updateToCompleted(imageId: String, result: IdeaImageUploadResult) {
        updateState { current ->
            current.copy(
                images = current.images.map { img ->
                    if (img.id == imageId) {
                        img.copy(
                            uploadStatus = IdeaImageUploadStatus.COMPLETED,
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
        updateState { current ->
            current.copy(
                images = current.images.map { img ->
                    if (img.id == imageId) {
                        img.copy(
                            uploadStatus = IdeaImageUploadStatus.FAILED,
                            uploadError = error
                        )
                    } else img
                }
            )
        }
    }
    
    private fun updateStatus(imageId: String, status: IdeaImageUploadStatus) {
        updateState { current ->
            current.copy(
                images = current.images.map { img ->
                    if (img.id == imageId) {
                        val isTerminal = img.uploadStatus == IdeaImageUploadStatus.COMPLETED ||
                            img.uploadStatus == IdeaImageUploadStatus.FAILED
                        val isRetry = status == IdeaImageUploadStatus.PENDING
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
    
    override fun dispose() {
        listeners.clear()
    }
}

