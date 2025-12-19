package cc.unitmesh.devins.ui.compose.sketch

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Encode an ImageBitmap to PNG bytes.
 * Platform-specific implementations handle the actual encoding.
 *
 * @param imageBitmap The ImageBitmap to encode
 * @return PNG bytes, or null if encoding fails
 */
expect fun encodeImageBitmapToPng(imageBitmap: ImageBitmap): ByteArray?

