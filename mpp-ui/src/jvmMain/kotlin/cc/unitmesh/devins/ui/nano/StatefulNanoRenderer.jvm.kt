package cc.unitmesh.devins.ui.nano

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import cc.unitmesh.config.ConfigManager
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File

/**
 * JVM implementation of decodeImageBytesToBitmap using Skia.
 */
internal actual fun decodeImageBytesToBitmap(bytes: ByteArray): ImageBitmap {
    val skiaImage = Image.makeFromEncoded(bytes)
    return skiaImage.toComposeImageBitmap()
}

/**
 * Best-effort persistence for troubleshooting and local reuse.
 *
 * Writes a JPEG copy to `~/.autodev/tmp/nano-images/<key>.jpg`.
 */
internal fun persistNanoImageAsJpeg(key: String, bytes: ByteArray) {
    runCatching {
        val configDir = File(ConfigManager.getConfigPath()).parentFile ?: return
        val outputDir = File(configDir, "tmp").resolve("nano-images")
        outputDir.mkdirs()

        val target = outputDir.resolve("$key.jpg")
        val image = Image.makeFromEncoded(bytes)
        val data = image.encodeToData(EncodedImageFormat.JPEG, 90) ?: return
        target.writeBytes(data.bytes)
    }
}

