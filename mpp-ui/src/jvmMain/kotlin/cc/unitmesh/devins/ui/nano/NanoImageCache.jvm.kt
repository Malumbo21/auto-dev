package cc.unitmesh.devins.ui.nano

import java.io.File

internal actual suspend fun platformReadCachedImageBytes(key: String): ByteArray? {
    val file = nanoImageCacheFile(key)
    return if (file.exists() && file.isFile) {
        runCatching { file.readBytes() }.getOrNull()
    } else {
        null
    }
}

internal actual suspend fun platformWriteCachedImageBytes(key: String, bytes: ByteArray) {
    val file = nanoImageCacheFile(key)
    file.parentFile?.mkdirs()
    runCatching { file.writeBytes(bytes) }
}

private fun nanoImageCacheFile(key: String): File {
    val tmpDir = System.getProperty("java.io.tmpdir").orEmpty().ifEmpty { "/tmp" }
    return File(File(File(tmpDir), "autodev"), "nano-images").resolve(key)
}
