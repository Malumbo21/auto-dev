package cc.unitmesh.devins.ui.nano

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Android implementation of decodeImageBytesToBitmap.
 */
internal actual fun decodeImageBytesToBitmap(bytes: ByteArray): ImageBitmap {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw IllegalArgumentException("Failed to decode image bytes")
    return bitmap.asImageBitmap()
}

