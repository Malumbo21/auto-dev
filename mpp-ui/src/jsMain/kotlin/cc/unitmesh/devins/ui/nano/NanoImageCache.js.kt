package cc.unitmesh.devins.ui.nano

internal actual suspend fun platformReadCachedImageBytes(key: String): ByteArray? = null

internal actual suspend fun platformWriteCachedImageBytes(key: String, bytes: ByteArray) {
    // No persistent storage wired for JS target yet.
}
