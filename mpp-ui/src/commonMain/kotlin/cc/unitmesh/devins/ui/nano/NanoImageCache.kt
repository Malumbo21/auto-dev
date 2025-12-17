package cc.unitmesh.devins.ui.nano

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object NanoImageCache {
    private val memoryCache = mutableMapOf<String, ByteArray>()
    private val mutex = Mutex()

    suspend fun getOrPut(key: String, downloader: suspend () -> ByteArray): ByteArray {
        memoryCache[key]?.let { return it }

        return mutex.withLock {
            memoryCache[key]?.let { return@withLock it }

            val persisted = platformReadCachedImageBytes(key)
            if (persisted != null) {
                memoryCache[key] = persisted
                return@withLock persisted
            }

            val bytes = downloader()
            memoryCache[key] = bytes
            // Best-effort persistence; ignore platform failures.
            runCatching { platformWriteCachedImageBytes(key, bytes) }
            bytes
        }
    }
}

internal fun nanoImageCacheKeyFromSrc(src: String): String {
    // Key = <sanitized filename>__<url hash>
    // - Keeps UX intent: same filename groups together
    // - Avoids collisions when different URLs share a filename
    // - Does not rely on platform crypto APIs

    val withoutFragment = src.substringBefore('#')
    val lastSegmentRaw = withoutFragment
        .substringBefore('?')
        .substringAfterLast('/', missingDelimiterValue = withoutFragment)
        .trim()

    val filePart = (if (lastSegmentRaw.isNotEmpty()) lastSegmentRaw else "image")
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(80)

    val hashPart = fnv1a64Hex(withoutFragment).take(16)
    return ("${filePart}__${hashPart}").take(120)
}

private fun fnv1a64Hex(input: String): String {
    var hash = -0x340d631b7bdddcdbL // 1469598103934665603
    val prime = 0x100000001b3L // 1099511628211
    // Use code units for stable cross-platform hashing.
    for (ch in input) {
        hash = hash xor ch.code.toLong()
        hash *= prime
    }
    return hash.toULong().toString(16)
}

internal expect suspend fun platformReadCachedImageBytes(key: String): ByteArray?
internal expect suspend fun platformWriteCachedImageBytes(key: String, bytes: ByteArray)
