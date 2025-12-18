package cc.unitmesh.xiuper.fs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

private class EchoBackend(private val name: String) : FsBackend {
    override suspend fun stat(path: FsPath): FsStat = FsStat(path, isDirectory = path.value == "/")
    override suspend fun list(path: FsPath): List<FsEntry> = listOf(FsEntry.File("$name:${path.value}"))
    override suspend fun read(path: FsPath, options: ReadOptions): ReadResult = ReadResult("$name:${path.value}".encodeToByteArray())
    override suspend fun write(path: FsPath, content: ByteArray, options: WriteOptions): WriteResult = WriteResult(true, "$name")
    override suspend fun delete(path: FsPath) = Unit
    override suspend fun mkdir(path: FsPath) = Unit
}

class XiuperVfsTest {
    @Test
    fun longestPrefixMountWins() = runTest {
        val vfs = XiuperVfs(
            mounts = listOf(
                Mount(FsPath.of("/"), EchoBackend("root")),
                Mount(FsPath.of("/http"), EchoBackend("http")),
                Mount(FsPath.of("/http/github"), EchoBackend("github"))
            )
        )

        val entries = vfs.list(FsPath.of("/http/github/repos"))
        assertEquals("github:/repos", (entries.single() as FsEntry.File).name)

        val text = vfs.read(FsPath.of("/http/x")).textOrNull()
        assertEquals("http:/x", text)
    }
}
