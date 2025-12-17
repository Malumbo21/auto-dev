package cc.unitmesh.devins.ui.nano

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun platformReadCachedImageBytes(key: String): ByteArray? {
    val path = nanoImageCachePath(key)
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    return data.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun platformWriteCachedImageBytes(key: String, bytes: ByteArray) {
    val dir = nanoImageCacheDirPath()
    val fileManager = NSFileManager.defaultManager
    runCatching {
        fileManager.createDirectoryAtPath(
            path = dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
    }

    val path = nanoImageCachePath(key)
    runCatching {
        bytes.usePinned { pinned ->
            val file = fopen(path, "wb") ?: return@usePinned
            try {
                fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file)
            } finally {
                fclose(file)
            }
        }
    }
}

private fun nanoImageCacheDirPath(): String {
    val base = NSTemporaryDirectory()
    val trimmed = base.trimEnd('/')
    return "$trimmed/autodev/nano-images"
}

private fun nanoImageCachePath(key: String): String = nanoImageCacheDirPath() + "/" + key

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val lengthInt = length.toInt()
    val out = ByteArray(lengthInt)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return out
}

