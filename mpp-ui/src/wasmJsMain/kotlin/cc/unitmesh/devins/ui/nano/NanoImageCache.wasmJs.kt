package cc.unitmesh.devins.ui.nano

import cc.unitmesh.devins.ui.platform.IndexedDBStorage
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.Uint8Array
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private fun setUint8Array(array: Uint8Array, index: Int, value: Int): Unit = js("array[index] = value")
private fun getUint8Array(array: Uint8Array, index: Int): Int = js("array[index]")
private fun getUint8ArrayLength(array: Uint8Array): Int = js("array.length")

private fun namespacedKey(key: String): String = "nano-img:$key"

internal actual suspend fun platformReadCachedImageBytes(key: String): ByteArray? {
    return suspendCancellableCoroutine { cont ->
        IndexedDBStorage.loadBinary(namespacedKey(key)).then({ uint8 ->
            cont.resume(uint8?.toByteArray())
            null
        }).catch({ error ->
            cont.resumeWithException(RuntimeException(error.toString()))
            null
        })
    }
}

internal actual suspend fun platformWriteCachedImageBytes(key: String, bytes: ByteArray) {
    val uint8 = bytes.toUint8Array()
    return suspendCancellableCoroutine { cont ->
        IndexedDBStorage.saveBinary(namespacedKey(key), uint8).then({ _ ->
            cont.resume(Unit)
            null
        }).catch({ error ->
            cont.resumeWithException(RuntimeException(error.toString()))
            null
        })
    }
}

private fun Uint8Array.toByteArray(): ByteArray {
    val len = getUint8ArrayLength(this)
    val out = ByteArray(len)
    for (i in 0 until len) {
        out[i] = (getUint8Array(this, i) and 0xFF).toByte()
    }
    return out
}

private fun ByteArray.toUint8Array(): Uint8Array {
    val out = Uint8Array(size)
    for (i in indices) {
        setUint8Array(out, i, this[i].toInt() and 0xFF)
    }
    return out
}
