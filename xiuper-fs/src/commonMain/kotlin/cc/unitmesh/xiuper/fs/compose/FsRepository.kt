package cc.unitmesh.xiuper.fs.compose

import cc.unitmesh.xiuper.fs.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Repository adapter for Compose UI integration.
 * 
 * Provides StateFlow-based reactive access to filesystem state.
 * Typical usage:
 * ```
 * val repo = FsRepository(vfs, scope)
 * val entries by repo.observeDir("/http/github/issues").collectAsState(emptyList())
 * val content by repo.observeText("/http/github/issues/1/title").collectAsState("")
 * ```
 */
class FsRepository(
    private val vfs: XiuperFileSystem,
    private val scope: CoroutineScope
) {
    private val dirCache = mutableMapOf<FsPath, MutableStateFlow<List<FsEntry>>>()
    private val fileCache = mutableMapOf<FsPath, MutableStateFlow<ByteArray?>>()

    /**
     * Observe directory contents as StateFlow.
     * Updates automatically when refreshDir() is called for this path.
     */
    fun observeDir(path: String): StateFlow<List<FsEntry>> {
        val fsPath = FsPath.of(path)
        return dirCache.getOrPut(fsPath) {
            MutableStateFlow<List<FsEntry>>(emptyList()).also { flow ->
                scope.launch {
                    try {
                        val entries = vfs.list(fsPath)
                        flow.value = entries
                    } catch (e: FsException) {
                        // Keep empty on error
                    }
                }
            }
        }
    }

    /**
     * Observe file content as StateFlow (raw bytes).
     * Updates automatically when refreshFile() is called for this path.
     */
    fun observeFile(path: String): StateFlow<ByteArray?> {
        val fsPath = FsPath.of(path)
        return fileCache.getOrPut(fsPath) {
            MutableStateFlow<ByteArray?>(null).also { flow ->
                scope.launch {
                    try {
                        val result = vfs.read(fsPath)
                        flow.value = result.bytes
                    } catch (e: FsException) {
                        flow.value = null
                    }
                }
            }
        }
    }

    /**
     * Observe file content as text (UTF-8).
     */
    fun observeText(path: String): StateFlow<String> {
        return observeFile(path).map { bytes ->
            bytes?.decodeToString() ?: ""
        }.stateIn(scope, SharingStarted.Eagerly, "")
    }

    /**
     * Manually refresh directory listing.
     */
    suspend fun refreshDir(path: String) {
        val fsPath = FsPath.of(path)
        val flow = dirCache[fsPath] ?: return
        try {
            val entries = vfs.list(fsPath)
            flow.value = entries
        } catch (e: FsException) {
            // Keep previous value on error
        }
    }

    /**
     * Manually refresh file content.
     */
    suspend fun refreshFile(path: String) {
        val fsPath = FsPath.of(path)
        val flow = fileCache[fsPath] ?: return
        try {
            val result = vfs.read(fsPath)
            flow.value = result.bytes
        } catch (e: FsException) {
            flow.value = null
        }
    }

    /**
     * Write file and refresh observers.
     */
    suspend fun writeFile(path: String, content: ByteArray): Result<Unit> {
        val fsPath = FsPath.of(path)
        return try {
            val result = vfs.write(fsPath, content)
            if (result.ok) {
                refreshFile(path)
                Result.success(Unit)
            } else {
                Result.failure(Exception(result.message ?: "Write failed"))
            }
        } catch (e: FsException) {
            Result.failure(e)
        }
    }

    /**
     * Write text file (UTF-8) and refresh observers.
     */
    suspend fun writeText(path: String, text: String): Result<Unit> {
        return writeFile(path, text.encodeToByteArray())
    }

    /**
     * Delete file and refresh parent directory.
     */
    suspend fun deleteFile(path: String): Result<Unit> {
        val fsPath = FsPath.of(path)
        return try {
            vfs.delete(fsPath)
            // Refresh parent directory if observed
            val parentPath = fsPath.parent()
            if (parentPath != null && dirCache.containsKey(parentPath)) {
                refreshDir(parentPath.value)
            }
            Result.success(Unit)
        } catch (e: FsException) {
            Result.failure(e)
        }
    }

    /**
     * Create directory and refresh parent.
     */
    suspend fun createDir(path: String): Result<Unit> {
        val fsPath = FsPath.of(path)
        return try {
            vfs.mkdir(fsPath)
            // Refresh parent directory if observed
            val parentPath = fsPath.parent()
            if (parentPath != null && dirCache.containsKey(parentPath)) {
                refreshDir(parentPath.value)
            }
            Result.success(Unit)
        } catch (e: FsException) {
            Result.failure(e)
        }
    }

    /**
     * Clear all cached flows (useful for logout/unmount scenarios).
     */
    fun clearCache() {
        dirCache.clear()
        fileCache.clear()
    }
}
