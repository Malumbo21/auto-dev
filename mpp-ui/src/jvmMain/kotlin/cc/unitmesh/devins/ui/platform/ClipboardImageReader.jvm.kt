package cc.unitmesh.devins.ui.platform

import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * JVM implementation of ClipboardImageReader using java.awt.Toolkit.
 */
class JvmClipboardImageReader : ClipboardImageReader {

    override fun hasImage(): Boolean {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)
        } catch (e: Exception) {
            println("Error checking clipboard for image: ${e.message}")
            false
        }
    }

    override fun readImage(): ClipboardImageData? {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard

            if (!clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                return null
            }

            val image = clipboard.getData(DataFlavor.imageFlavor) as? Image ?: return null

            // Convert Image to BufferedImage
            val bufferedImage = toBufferedImage(image)

            // Convert to PNG bytes
            val outputStream = ByteArrayOutputStream()
            ImageIO.write(bufferedImage, "png", outputStream)
            val bytes = outputStream.toByteArray()

            if (bytes.isEmpty()) {
                println("Failed to convert clipboard image to bytes")
                return null
            }

            // Generate unique name with timestamp
            val timestamp = System.currentTimeMillis()
            val suggestedName = "pasted_image_$timestamp.png"

            ClipboardImageData(
                bytes = bytes,
                mimeType = "image/png",
                suggestedName = suggestedName
            )
        } catch (e: Exception) {
            println("Error reading image from clipboard: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Convert any Image to BufferedImage.
     */
    private fun toBufferedImage(image: Image): BufferedImage {
        if (image is BufferedImage) {
            return image
        }

        // Create a buffered image with transparency
        val width = image.getWidth(null)
        val height = image.getHeight(null)

        if (width <= 0 || height <= 0) {
            throw IllegalArgumentException("Invalid image dimensions: ${width}x${height}")
        }

        val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        // Draw the image on to the buffered image
        val graphics = bufferedImage.createGraphics()
        try {
            graphics.drawImage(image, 0, 0, null)
        } finally {
            graphics.dispose()
        }

        return bufferedImage
    }
}

actual fun createClipboardImageReader(): ClipboardImageReader = JvmClipboardImageReader()

