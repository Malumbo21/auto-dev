package cc.unitmesh.xiuper.fs.conformance

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cc.unitmesh.xiuper.fs.*
import cc.unitmesh.xiuper.fs.db.DbFsBackend
import cc.unitmesh.xiuper.fs.db.XiuperFsDatabase
import kotlinx.datetime.Clock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class DbPosixSubsetConformanceTest {
    private data class ConformanceTarget(
        val fs: XiuperFileSystem,
        val capabilities: BackendCapabilities
    )

    private fun newTarget(): ConformanceTarget {
        val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        XiuperFsDatabase.Schema.create(driver)
        val database = XiuperFsDatabase(driver)

        database.fsNodeQueries.upsertNode(
            path = "/",
            isDir = 1L,
            content = null,
            mtimeEpochMillis = Clock.System.now().toEpochMilliseconds(),
        )

        val backend = DbFsBackend(database)
        val capabilities = (backend as? CapabilityAwareBackend)?.capabilities ?: BackendCapabilities()
        val fs = XiuperVfs(
            mounts = listOf(
                Mount(FsPath.of("/"), backend)
            )
        )
        return ConformanceTarget(fs, capabilities)
    }

    @Test
    fun rootAlwaysExistsAndIsDirectory() = runTest {
        val (fs) = newTarget()
        val st = fs.stat(FsPath.of("/"))
        assertTrue(st.isDirectory)
        assertEquals("/", st.path.value)

        val entries = fs.list(FsPath.of("/"))
        assertEquals(0, entries.size)
    }

    @Test
    fun listNonexistentIsEnoent() = runTest {
        val (fs) = newTarget()
        assertFsError(FsErrorCode.ENOENT) {
            fs.list(FsPath.of("/missing"))
        }
    }

    @Test
    fun mkdirRequiresParentAndIsNotMkdirP() = runTest {
        val (fs, caps) = newTarget()
        if (!caps.supportsMkdir) {
            assertFsError(FsErrorCode.ENOTSUP) {
                fs.mkdir(FsPath.of("/a/b"))
            }
            return@runTest
        }

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
        val (fs, caps) = newTarget()
        if (!caps.supportsMkdir) {
            assertFsError(FsErrorCode.ENOTSUP) {
                fs.mkdir(FsPath.of("/dir"))
            }
            return@runTest
        }
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
        val (fs, caps) = newTarget()
        assertFsError(FsErrorCode.ENOENT) {
            fs.write(FsPath.of("/a/file.txt"), "x".encodeToByteArray())
        }

        if (!caps.supportsMkdir) {
            return@runTest
        }

        fs.mkdir(FsPath.of("/a"))
        assertTrue(fs.write(FsPath.of("/a/file.txt"), "hello".encodeToByteArray()).ok)

        val read = fs.read(FsPath.of("/a/file.txt")).textOrNull()
        assertEquals("hello", read)
    }

    @Test
    fun writeIsCreateOrTruncate() = runTest {
        val (fs, caps) = newTarget()
        if (!caps.supportsMkdir) return@runTest
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
        val (fs, caps) = newTarget()
        if (!caps.supportsMkdir) return@runTest
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
        val (fs, caps) = newTarget()
        if (!caps.supportsMkdir) return@runTest
        if (!caps.supportsDelete) {
            assertFsError(FsErrorCode.ENOTSUP) {
                fs.delete(FsPath.of("/d"))
            }
            return@runTest
        }
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
        val (fs, caps) = newTarget()
        if (!caps.supportsMkdir) return@runTest
        if (!caps.supportsDelete) {
            assertFsError(FsErrorCode.ENOTSUP) {
                fs.delete(FsPath.of("/d"))
            }
            return@runTest
        }
        fs.mkdir(FsPath.of("/d"))
        fs.write(FsPath.of("/d/f"), "x".encodeToByteArray())

        assertFsError(FsErrorCode.ENOTEMPTY) {
            fs.delete(FsPath.of("/d"))
        }
    }

    @Test
    fun deleteRootIsEacces() = runTest {
        val (fs, caps) = newTarget()
        val expected = if (caps.supportsDelete) FsErrorCode.EACCES else FsErrorCode.ENOTSUP
        assertFsError(expected) {
            fs.delete(FsPath.of("/"))
        }
    }

    @Test
    fun commitIsNoOpAndOk() = runTest {
        val (fs) = newTarget()
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
