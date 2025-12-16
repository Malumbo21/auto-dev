package cc.unitmesh.devins.ui.nano

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

/**
 * JVM implementation of decodeImageBytesToBitmap using Skia.
 */
internal actual fun decodeImageBytesToBitmap(bytes: ByteArray): ImageBitmap {
    val skiaImage = Image.makeFromEncoded(bytes)
    return skiaImage.toComposeImageBitmap()
}

