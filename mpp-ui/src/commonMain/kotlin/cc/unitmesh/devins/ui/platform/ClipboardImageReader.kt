package cc.unitmesh.devins.ui.platform

/**
 * Result of reading an image from clipboard.
 */
data class ClipboardImageData(
    /** Image data as bytes */
    val bytes: ByteArray,
    /** MIME type of the image */
    val mimeType: String = "image/png",
    /** Suggested file name */
    val suggestedName: String = "pasted_image.png"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as ClipboardImageData
        return bytes.contentEquals(other.bytes) && mimeType == other.mimeType
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}

/**
 * Cross-platform clipboard image reader.
 * Reads image data from system clipboard.
 */
interface ClipboardImageReader {
    /**
     * Check if clipboard contains an image.
     * @return true if clipboard has image data
     */
    fun hasImage(): Boolean

    /**
     * Read image from clipboard.
     * @return ClipboardImageData if clipboard contains an image, null otherwise
     */
    fun readImage(): ClipboardImageData?
}

/**
 * Get platform-specific clipboard image reader instance.
 */
expect fun createClipboardImageReader(): ClipboardImageReader

