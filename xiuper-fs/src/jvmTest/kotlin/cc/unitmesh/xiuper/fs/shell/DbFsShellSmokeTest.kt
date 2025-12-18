package cc.unitmesh.xiuper.fs.shell

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cc.unitmesh.xiuper.fs.db.DbFsBackend
import cc.unitmesh.xiuper.fs.db.XiuperFsDatabase
import kotlinx.datetime.Clock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DbFsShellSmokeTest {

    private fun newDbBackend(): DbFsBackend {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        XiuperFsDatabase.Schema.create(driver)
        val database = XiuperFsDatabase(driver)

        database.fsNodeQueries.upsertNode(
            path = "/",
            isDir = 1L,
            content = null,
            mtimeEpochMillis = Clock.System.now().toEpochMilliseconds(),
        )

        return DbFsBackend(database)
    }

    @Test
    fun `db backend works via shell commands`() = runTest {
        val shell = ShellFsInterpreter(newDbBackend())

        assertEquals(0, shell.execute("mkdir /data").exitCode)
        assertEquals(0, shell.execute("echo hello sqlite > /data/hello.txt").exitCode)

        val cat = shell.execute("cat /data/hello.txt")
        assertEquals(0, cat.exitCode)
        assertEquals("hello sqlite", cat.stdout)

        val ls = shell.execute("ls /data")
        assertEquals(0, ls.exitCode)
        assertTrue(ls.stdout.contains("hello.txt"))

        assertEquals(0, shell.execute("cp /data/hello.txt /data/copy.txt").exitCode)
        assertEquals("hello sqlite", shell.execute("cat /data/copy.txt").stdout)

        assertEquals(0, shell.execute("mv /data/copy.txt /data/moved.txt").exitCode)
        assertEquals("hello sqlite", shell.execute("cat /data/moved.txt").stdout)

        assertEquals(0, shell.execute("rm /data/moved.txt").exitCode)
        assertEquals(1, shell.execute("cat /data/moved.txt").exitCode)
    }
}
