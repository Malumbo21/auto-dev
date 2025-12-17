package cc.unitmesh.devins.ui.nano

import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * WASM implementation of decodeImageBytesToBitmap using Compose Resources.
 */
internal actual fun decodeImageBytesToBitmap(bytes: ByteArray): ImageBitmap {
    return bytes.decodeToImageBitmap()
}

