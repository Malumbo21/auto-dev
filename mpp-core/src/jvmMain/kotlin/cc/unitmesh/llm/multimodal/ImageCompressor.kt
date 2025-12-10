package cc.unitmesh.llm.multimodal

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Image compressor for multimodal AI requests.
 * 
 * Vision models charge by tokens, so compressing images before sending
 * can significantly reduce costs while maintaining acceptable quality.
 * 
 * Inspired by: https://github.com/zetbaitsu/Compressor
 */
object ImageCompressor {

    /**
     * Compression configuration
     */
    data class Config(
        /** Maximum width in pixels (default: 1024 for good balance) */
        val maxWidth: Int = 1024,
        /** Maximum height in pixels (default: 1024 for good balance) */
        val maxHeight: Int = 1024,
        /** JPEG quality (0.0 - 1.0, default: 0.8 for good balance) */
        val quality: Float = 0.8f,
        /** Maximum file size in bytes (default: 500KB) */
        val maxFileSize: Long = 500 * 1024,
        /** Output format (JPEG is best for compression, PNG for transparency) */
        val format: OutputFormat = OutputFormat.JPEG
    ) {
        companion object {
            /** High quality preset - larger files, better visual quality */
            val HIGH_QUALITY = Config(maxWidth = 2048, maxHeight = 2048, quality = 0.9f, maxFileSize = 1024 * 1024)
            
            /** Balanced preset - good for most use cases */
            val BALANCED = Config()
            
            /** Low quality preset - smaller files, faster processing */
            val LOW_QUALITY = Config(maxWidth = 512, maxHeight = 512, quality = 0.6f, maxFileSize = 200 * 1024)
            
            /** Thumbnail preset - very small, for previews */
            val THUMBNAIL = Config(maxWidth = 256, maxHeight = 256, quality = 0.5f, maxFileSize = 50 * 1024)
        }
    }

    enum class OutputFormat {
        JPEG,
        PNG
    }

    /**
     * Compression result with statistics
     */
    data class CompressionResult(
        val bytes: ByteArray,
        val originalSize: Long,
        val compressedSize: Long,
        val originalWidth: Int,
        val originalHeight: Int,
        val newWidth: Int,
        val newHeight: Int,
        val format: String
    ) {
        val compressionRatio: Float
            get() = if (originalSize > 0) compressedSize.toFloat() / originalSize else 1f

        val savedBytes: Long
            get() = originalSize - compressedSize

        val savedPercentage: Float
            get() = if (originalSize > 0) (1 - compressionRatio) * 100 else 0f

        override fun toString(): String {
            return "CompressionResult(" +
                    "original=${originalWidth}x${originalHeight} ${originalSize / 1024}KB, " +
                    "compressed=${newWidth}x${newHeight} ${compressedSize / 1024}KB, " +
                    "saved=${savedPercentage.toInt()}%)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CompressionResult) return false
            return bytes.contentEquals(other.bytes) &&
                    originalSize == other.originalSize &&
                    compressedSize == other.compressedSize
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + originalSize.hashCode()
            result = 31 * result + compressedSize.hashCode()
            return result
        }
    }

    /**
     * Compress an image file with the given configuration.
     */
    fun compress(file: File, config: Config = Config.BALANCED): CompressionResult {
        val originalSize = file.length()
        val originalImage = ImageIO.read(file)
            ?: throw IllegalArgumentException("Cannot read image file: ${file.absolutePath}")

        return compressImage(originalImage, originalSize, config)
    }

    /**
     * Compress image bytes with the given configuration.
     */
    fun compress(bytes: ByteArray, config: Config = Config.BALANCED): CompressionResult {
        val originalSize = bytes.size.toLong()
        val originalImage = ImageIO.read(bytes.inputStream())
            ?: throw IllegalArgumentException("Cannot read image from bytes")

        return compressImage(originalImage, originalSize, config)
    }

    /**
     * Compress a BufferedImage with the given configuration.
     */
    fun compress(image: BufferedImage, config: Config = Config.BALANCED): CompressionResult {
        // Estimate original size (rough calculation)
        val originalSize = (image.width * image.height * 3).toLong()
        return compressImage(image, originalSize, config)
    }

    private fun compressImage(
        originalImage: BufferedImage,
        originalSize: Long,
        config: Config
    ): CompressionResult {
        val originalWidth = originalImage.width
        val originalHeight = originalImage.height

        // Step 1: Resize if necessary
        val (targetWidth, targetHeight) = calculateTargetSize(
            originalWidth, originalHeight,
            config.maxWidth, config.maxHeight
        )

        val resizedImage = if (targetWidth != originalWidth || targetHeight != originalHeight) {
            resizeImage(originalImage, targetWidth, targetHeight)
        } else {
            originalImage
        }

        // Step 2: Compress with quality setting
        var quality = config.quality
        var compressedBytes = compressToBytes(resizedImage, quality, config.format)

        // Step 3: Iteratively reduce quality if file size is too large
        while (compressedBytes.size > config.maxFileSize && quality > 0.3f) {
            quality -= 0.1f
            compressedBytes = compressToBytes(resizedImage, quality, config.format)
        }

        // Step 4: If still too large, resize further
        if (compressedBytes.size > config.maxFileSize) {
            val reductionFactor = sqrt(config.maxFileSize.toDouble() / compressedBytes.size)
            val newWidth = (targetWidth * reductionFactor).toInt().coerceAtLeast(100)
            val newHeight = (targetHeight * reductionFactor).toInt().coerceAtLeast(100)
            
            val furtherResized = resizeImage(resizedImage, newWidth, newHeight)
            compressedBytes = compressToBytes(furtherResized, quality, config.format)
        }

        val formatStr = when (config.format) {
            OutputFormat.JPEG -> "image/jpeg"
            OutputFormat.PNG -> "image/png"
        }

        return CompressionResult(
            bytes = compressedBytes,
            originalSize = originalSize,
            compressedSize = compressedBytes.size.toLong(),
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            newWidth = if (targetWidth != originalWidth) targetWidth else originalWidth,
            newHeight = if (targetHeight != originalHeight) targetHeight else originalHeight,
            format = formatStr
        )
    }

    private fun calculateTargetSize(
        width: Int,
        height: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Pair<Int, Int> {
        if (width <= maxWidth && height <= maxHeight) {
            return width to height
        }

        val widthRatio = maxWidth.toFloat() / width
        val heightRatio = maxHeight.toFloat() / height
        val ratio = min(widthRatio, heightRatio)

        return (width * ratio).toInt() to (height * ratio).toInt()
    }

    private fun resizeImage(source: BufferedImage, width: Int, height: Int): BufferedImage {
        val resized = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g2d = resized.createGraphics()
        
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        g2d.drawImage(source, 0, 0, width, height, null)
        g2d.dispose()
        
        return resized
    }

    private fun compressToBytes(image: BufferedImage, quality: Float, format: OutputFormat): ByteArray {
        val baos = ByteArrayOutputStream()

        when (format) {
            OutputFormat.JPEG -> {
                val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
                val param = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = quality
                }
                
                val ios = ImageIO.createImageOutputStream(baos)
                writer.output = ios
                writer.write(null, IIOImage(image, null, null), param)
                ios.close()
                writer.dispose()
            }
            OutputFormat.PNG -> {
                // PNG doesn't support quality parameter, but we can optimize it
                ImageIO.write(image, "png", baos)
            }
        }

        return baos.toByteArray()
    }

    /**
     * Get image dimensions without loading the entire image.
     */
    fun getImageDimensions(file: File): Pair<Int, Int>? {
        return try {
            val readers = ImageIO.getImageReadersBySuffix(file.extension)
            if (readers.hasNext()) {
                val reader = readers.next()
                ImageIO.createImageInputStream(file).use { stream ->
                    reader.input = stream
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    reader.dispose()
                    width to height
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

