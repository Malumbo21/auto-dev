package cc.unitmesh.devins.ui.compose.sketch

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * JVM implementation of encodeImageBitmapToPng.
 * Uses Java's ImageIO to encode the image to PNG format.
 */
actual fun encodeImageBitmapToPng(imageBitmap: ImageBitmap): ByteArray? {
    return try {
        val awtImage = imageBitmap.toAwtImage()
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(awtImage, "png", outputStream)
        outputStream.toByteArray()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

