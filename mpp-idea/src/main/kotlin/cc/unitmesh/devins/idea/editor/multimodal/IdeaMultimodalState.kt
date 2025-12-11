package cc.unitmesh.devins.idea.editor.multimodal

import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Upload status for an attached image.
 */
enum class IdeaImageUploadStatus {
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
 * Adapted from Compose version for IntelliJ IDEA platform.
 */
data class IdeaAttachedImage(
    /** Unique identifier for this image */
    val id: String = UUID.randomUUID().toString(),
    /** Display name of the image */
    val name: String,
    /** Full path to the image file (for local files) */
    val path: String? = null,
    /** Image data as bytes (for pasted images or compressed) */
    val bytes: ByteArray? = null,
    /** Buffered image for preview (optional, can be loaded lazily) */
    val thumbnail: BufferedImage? = null,
    /** MIME type of the image */
    val mimeType: String = "image/png",
    /** URL of the uploaded image (after cloud upload) */
    val uploadedUrl: String? = null,
    /** Original file size in bytes */
    val originalSize: Long = 0,
    /** Compressed size in bytes (if compressed) */
    val compressedSize: Long? = null,
    /** Timestamp when the image was attached */
    val attachedAt: Long = System.currentTimeMillis(),
    /** Current upload status */
    val uploadStatus: IdeaImageUploadStatus = IdeaImageUploadStatus.PENDING,
    /** Upload error message if failed */
    val uploadError: String? = null,
    /** Upload progress (0-100) */
    val uploadProgress: Int = 0
) {
    val isUploaded: Boolean get() = uploadedUrl != null && uploadStatus == IdeaImageUploadStatus.COMPLETED
    val isUploading: Boolean get() = uploadStatus == IdeaImageUploadStatus.COMPRESSING || uploadStatus == IdeaImageUploadStatus.UPLOADING
    val isFailed: Boolean get() = uploadStatus == IdeaImageUploadStatus.FAILED

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
        other as IdeaAttachedImage
        return id == other.id &&
            name == other.name &&
            path == other.path &&
            mimeType == other.mimeType &&
            uploadedUrl == other.uploadedUrl &&
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
        val SUPPORTED_EXTENSIONS = listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        val SUPPORTED_MIME_TYPES = listOf(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"
        )

        fun fromPath(path: String): IdeaAttachedImage {
            val file = File(path)
            val name = file.name
            val extension = name.substringAfterLast('.', "").lowercase()
            val mimeType = when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "bmp" -> "image/bmp"
                else -> "image/png"
            }
            return IdeaAttachedImage(
                name = name,
                path = path,
                mimeType = mimeType,
                originalSize = file.length()
            )
        }

        /**
         * Create an IdeaAttachedImage from bytes (e.g., pasted from clipboard).
         */
        fun fromBytes(bytes: ByteArray, mimeType: String, suggestedName: String): IdeaAttachedImage {
            return IdeaAttachedImage(
                name = suggestedName,
                path = null,
                bytes = bytes,
                mimeType = mimeType,
                originalSize = bytes.size.toLong()
            )
        }

        /**
         * Create an IdeaAttachedImage from BufferedImage (e.g., from clipboard).
         */
        fun fromBufferedImage(image: BufferedImage, suggestedName: String = "pasted_image_${System.currentTimeMillis()}.png"): IdeaAttachedImage {
            val outputStream = ByteArrayOutputStream()
            ImageIO.write(image, "png", outputStream)
            val bytes = outputStream.toByteArray()
            
            return IdeaAttachedImage(
                name = suggestedName,
                path = null,
                bytes = bytes,
                thumbnail = image,
                mimeType = "image/png",
                originalSize = bytes.size.toLong()
            )
        }
    }
}

/**
 * State for multimodal input handling.
 */
data class IdeaMultimodalState(
    /** List of attached images */
    val images: List<IdeaAttachedImage> = emptyList(),
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

