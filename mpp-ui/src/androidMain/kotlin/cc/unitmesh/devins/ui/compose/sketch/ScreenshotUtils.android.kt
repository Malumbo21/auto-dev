package cc.unitmesh.devins.ui.compose.sketch

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream

/**
 * Android implementation of encodeImageBitmapToPng.
 * Uses Android's Bitmap.compress to encode the image to PNG format.
 */
actual fun encodeImageBitmapToPng(imageBitmap: ImageBitmap): ByteArray? {
    return try {
        val androidBitmap = imageBitmap.asAndroidBitmap()
        val outputStream = ByteArrayOutputStream()
        androidBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.toByteArray()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

