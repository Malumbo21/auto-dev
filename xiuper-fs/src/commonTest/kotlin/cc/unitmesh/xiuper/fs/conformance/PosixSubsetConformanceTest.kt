package cc.unitmesh.xiuper.fs.conformance

import cc.unitmesh.xiuper.fs.*
import cc.unitmesh.xiuper.fs.memory.InMemoryFsBackend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class PosixSubsetConformanceTest {
    private fun newFs(): XiuperFileSystem {
        return XiuperVfs(
            mounts = listOf(
                Mount(FsPath.of("/"), InMemoryFsBackend())
            )
        )
    }

    @Test
    fun rootAlwaysExistsAndIsDirectory() = runTest {
        val fs = newFs()
        val st = fs.stat(FsPath.of("/"))
        assertTrue(st.isDirectory)
        assertEquals("/", st.path.value)

        val entries = fs.list(FsPath.of("/"))
        assertEquals(0, entries.size)
    }

    @Test
    fun listNonexistentIsEnoent() = runTest {
        val fs = newFs()
        assertFsError(FsErrorCode.ENOENT) {
            fs.list(FsPath.of("/missing"))
        }
    }

    @Test
    fun mkdirRequiresParentAndIsNotMkdirP() = runTest {
        val fs = newFs()
        assertFsError(FsErrorCode.ENOENT) {
            fs.mkdir(FsPath.of("/a/b"))
        }

        fs.mkdir(FsPath.of("/a"))
        fs.mkdir(FsPath.of("/a/b"))

        val st = fs.stat(FsPath.of("/a/b"))
        assertTrue(st.isDirectory)
    }

    @Test
    fun mkdirExistingIsEexist() = runTest {
        val fs = newFs()
        fs.mkdir(FsPath.of("/dir"))
        assertFsError(FsErrorCode.EEXIST) {
            fs.mkdir(FsPath.of("/dir"))
        }

        assertFsError(FsErrorCode.EEXIST) {
            fs.mkdir(FsPath.of("/"))
        }
    }

    @Test
    fun writeRequiresParentDir() = runTest {
        val fs = newFs()
        assertFsError(FsErrorCode.ENOENT) {
            fs.write(FsPath.of("/a/file.txt"), "x".encodeToByteArray())
        }

        fs.mkdir(FsPath.of("/a"))
        assertTrue(fs.write(FsPath.of("/a/file.txt"), "hello".encodeToByteArray()).ok)

        val read = fs.read(FsPath.of("/a/file.txt")).textOrNull()
        assertEquals("hello", read)
    }

    @Test
    fun writeIsCreateOrTruncate() = runTest {
        val fs = newFs()
        fs.mkdir(FsPath.of("/d"))

        fs.write(FsPath.of("/d/f"), "hello".encodeToByteArray())
        fs.write(FsPath.of("/d/f"), "hi".encodeToByteArray())

        assertEquals("hi", fs.read(FsPath.of("/d/f")).textOrNull())
        val st = fs.stat(FsPath.of("/d/f"))
        assertEquals(2, st.size)
        assertTrue(!st.isDirectory)
    }

    @Test
    fun typeErrorsForReadAndList() = runTest {
        val fs = newFs()
        fs.mkdir(FsPath.of("/d"))
        fs.write(FsPath.of("/d/f"), "x".encodeToByteArray())

        assertFsError(FsErrorCode.EISDIR) {
            fs.read(FsPath.of("/d"))
        }

        assertFsError(FsErrorCode.ENOTDIR) {
            fs.list(FsPath.of("/d/f"))
        }
    }

    @Test
    fun deleteFileAndEmptyDirectory() = runTest {
        val fs = newFs()
        fs.mkdir(FsPath.of("/d"))
        fs.write(FsPath.of("/d/f"), "x".encodeToByteArray())

        fs.delete(FsPath.of("/d/f"))
        assertFsError(FsErrorCode.ENOENT) {
            fs.read(FsPath.of("/d/f"))
        }

        fs.delete(FsPath.of("/d"))
        assertFsError(FsErrorCode.ENOENT) {
            fs.stat(FsPath.of("/d"))
        }
    }

    @Test
    fun deleteNonEmptyDirectoryIsEnotempty() = runTest {
        val fs = newFs()
        fs.mkdir(FsPath.of("/d"))
        fs.write(FsPath.of("/d/f"), "x".encodeToByteArray())

        assertFsError(FsErrorCode.ENOTEMPTY) {
            fs.delete(FsPath.of("/d"))
        }
    }

    @Test
    fun deleteRootIsEacces() = runTest {
        val fs = newFs()
        assertFsError(FsErrorCode.EACCES) {
            fs.delete(FsPath.of("/"))
        }
    }

    @Test
    fun commitIsNoOpAndOk() = runTest {
        val fs = newFs()
        val r = fs.commit(FsPath.of("/"))
        assertTrue(r.ok)
    }

    private suspend fun assertFsError(code: FsErrorCode, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected FsException($code)")
        } catch (e: FsException) {
            assertEquals(code, e.code)
            assertNotNull(e.message)
        }
    }
}
