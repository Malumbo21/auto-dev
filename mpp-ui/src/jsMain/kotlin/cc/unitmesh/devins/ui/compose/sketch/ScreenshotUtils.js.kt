package cc.unitmesh.devins.ui.compose.sketch

import androidx.compose.ui.graphics.ImageBitmap

/**
 * JS implementation of encodeImageBitmapToPng.
 * Note: JS platform has limited support for image encoding.
 * Returns null as screenshot is not fully supported on JS platform.
 */
actual fun encodeImageBitmapToPng(imageBitmap: ImageBitmap): ByteArray? {
    // JS platform does not have direct access to Skia or native image encoding
    // Screenshot functionality is limited on this platform
    println("[ScreenshotUtils] Screenshot not supported on JS platform")
    return null
}

