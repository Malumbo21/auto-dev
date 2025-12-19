package cc.unitmesh.devins.ui.compose.sketch

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * WASM implementation of encodeImageBitmapToPng.
 * Uses Skia to encode the image to PNG format.
 */
actual fun encodeImageBitmapToPng(imageBitmap: ImageBitmap): ByteArray? {
    return try {
        val skiaBitmap = imageBitmap.asSkiaBitmap()
        val image = Image.makeFromBitmap(skiaBitmap)
        val data = image.encodeToData(EncodedImageFormat.PNG)
        data?.bytes
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

