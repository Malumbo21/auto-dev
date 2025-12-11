package cc.unitmesh.devins.ui.compose.editor.multimodal

import kotlinx.datetime.Clock

/**
 * Upload status for an attached image.
 */
enum class ImageUploadStatus {
    /** Image is pending upload */
    PENDING,
    /** Image is being compressed */
    COMPRESSING,
    /** Image is being uploaded to cloud storage */
    UPLOADING,
    /** Upload completed successfully */
    COMPLETED,
    /** Upload failed */
    FAILED
}

/**
 * Represents an attached image for multimodal processing.
 */
data class AttachedImage(
    /** Unique identifier for this image */
    val id: String = generateId(),
    /** Display name of the image */
    val name: String,
    /** Full path to the image file (for local files) */
    val path: String? = null,
    /** Image data as bytes (for pasted images or compressed) */
    val bytes: ByteArray? = null,
    /** MIME type of the image */
    val mimeType: String = "image/png",
    /** URL of the uploaded image (after COS upload) */
    val uploadedUrl: String? = null,
    /** Original file size in bytes */
    val originalSize: Long = 0,
    /** Compressed size in bytes (if compressed) */
    val compressedSize: Long? = null,
    /** Timestamp when the image was attached */
    val attachedAt: Long = Clock.System.now().toEpochMilliseconds(),
    /** Current upload status */
    val uploadStatus: ImageUploadStatus = ImageUploadStatus.PENDING,
    /** Upload error message if failed */
    val uploadError: String? = null,
    /** Upload progress (0-100) */
    val uploadProgress: Int = 0
) {
    val isUploaded: Boolean get() = uploadedUrl != null && uploadStatus == ImageUploadStatus.COMPLETED
    val isUploading: Boolean get() = uploadStatus == ImageUploadStatus.COMPRESSING || uploadStatus == ImageUploadStatus.UPLOADING
    val isFailed: Boolean get() = uploadStatus == ImageUploadStatus.FAILED

    val compressionSavings: String? get() {
        if (compressedSize == null || originalSize == 0L) return null
        val savedPercent = ((originalSize - compressedSize) * 100 / originalSize).toInt()
        return if (savedPercent > 0) "${savedPercent}%" else null
    }

    val displaySize: String get() {
        val size = compressedSize ?: originalSize
        return when {
            size >= 1024 * 1024 -> "${size / (1024 * 1024)}MB"
            size >= 1024 -> "${size / 1024}KB"
            else -> "${size}B"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as AttachedImage
        // Compare all relevant fields for state change detection
        return id == other.id &&
            name == other.name &&
            path == other.path &&
            mimeType == other.mimeType &&
            uploadedUrl == other.uploadedUrl &&
            originalSize == other.originalSize &&
            compressedSize == other.compressedSize &&
            uploadStatus == other.uploadStatus &&
            uploadError == other.uploadError &&
            uploadProgress == other.uploadProgress
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + uploadStatus.hashCode()
        result = 31 * result + (uploadedUrl?.hashCode() ?: 0)
        result = 31 * result + uploadProgress
        return result
    }

    companion object {
        private var counter = 0
        private fun generateId(): String {
            counter++
            return "img_${Clock.System.now().toEpochMilliseconds()}_$counter"
        }

        val SUPPORTED_EXTENSIONS = listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        val SUPPORTED_MIME_TYPES = listOf(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"
        )

        fun fromPath(path: String): AttachedImage {
            val name = path.substringAfterLast('/').substringAfterLast('\\')
            val extension = name.substringAfterLast('.', "").lowercase()
            val mimeType = when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "bmp" -> "image/bmp"
                else -> "image/png"
            }
            return AttachedImage(
                name = name,
                path = path,
                mimeType = mimeType
            )
        }

        /**
         * Create an AttachedImage from bytes (e.g., pasted from clipboard).
         */
        fun fromBytes(bytes: ByteArray, mimeType: String, suggestedName: String): AttachedImage {
            return AttachedImage(
                name = suggestedName,
                path = null,
                bytes = bytes,
                mimeType = mimeType,
                originalSize = bytes.size.toLong()
            )
        }
    }
}

/**
 * State for multimodal input handling.
 */
data class MultimodalState(
    /** List of attached images */
    val images: List<AttachedImage> = emptyList(),
    /** Whether multimodal analysis is in progress */
    val isAnalyzing: Boolean = false,
    /** Current analysis progress message */
    val analysisProgress: String? = null,
    /** Analysis result from the vision model */
    val analysisResult: String? = null,
    /** Error message if analysis failed */
    val analysisError: String? = null,
    /** The multimodal model being used */
    val visionModel: String = "glm-4.6v"
) {
    val hasImages: Boolean get() = images.isNotEmpty()
    val imageCount: Int get() = images.size
    val canAddMoreImages: Boolean get() = images.size < MAX_IMAGES

    /** Check if any image is currently uploading */
    val isUploading: Boolean get() = images.any { it.isUploading }

    /** Check if all images have been uploaded successfully */
    val allImagesUploaded: Boolean get() = images.isNotEmpty() && images.all { it.isUploaded }

    /** Check if any image upload failed */
    val hasUploadError: Boolean get() = images.any { it.isFailed }

    /** Get the number of images currently uploading */
    val uploadingCount: Int get() = images.count { it.isUploading }

    /** Get the number of images that have been uploaded */
    val uploadedCount: Int get() = images.count { it.isUploaded }

    /** Check if send should be enabled (no uploads in progress, at least one image uploaded or no images) */
    val canSend: Boolean get() = !isUploading && !isAnalyzing && (images.isEmpty() || allImagesUploaded)

    companion object {
        const val MAX_IMAGES = 5 // Maximum number of images per message
    }
}

/**
 * Events for multimodal processing.
 */
sealed class MultimodalEvent {
    /** Image selection from file picker */
    data class ImageSelected(val path: String) : MultimodalEvent()

    /** Image pasted from clipboard */
    data class ImagePasted(val bytes: ByteArray, val mimeType: String) : MultimodalEvent()

    /** Remove an attached image */
    data class ImageRemoved(val imageId: String) : MultimodalEvent()

    /** Clear all attached images */
    data object ClearImages : MultimodalEvent()

    /** Analysis started */
    data class AnalysisStarted(val prompt: String) : MultimodalEvent()

    /** Analysis progress update */
    data class AnalysisProgress(val message: String) : MultimodalEvent()

    /** Analysis chunk received (streaming) */
    data class AnalysisChunk(val chunk: String) : MultimodalEvent()

    /** Analysis completed */
    data class AnalysisCompleted(val result: String) : MultimodalEvent()

    /** Analysis failed */
    data class AnalysisFailed(val error: String) : MultimodalEvent()
}

