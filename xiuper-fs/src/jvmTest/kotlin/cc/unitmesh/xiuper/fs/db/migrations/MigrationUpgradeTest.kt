package cc.unitmesh.xiuper.fs.db.migrations

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cc.unitmesh.xiuper.fs.db.XiuperFsDatabase
import cc.unitmesh.xiuper.fs.db.createDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for actual migration upgrade paths.
 * Verifies schema evolution and data integrity across versions.
 */
class MigrationUpgradeTest {
    
    @Test
    fun migrateFromV1ToV2AddsXattrTable() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        
        // Create v1 schema manually
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS FsNode (
                path TEXT NOT NULL PRIMARY KEY,
                isDir INTEGER NOT NULL,
                content BLOB,
                mtimeEpochMillis INTEGER NOT NULL
            )
        """.trimIndent(), 0, null)
        
        // Insert some v1 data
        driver.execute(null, """
            INSERT INTO FsNode(path, isDir, content, mtimeEpochMillis)
            VALUES ('/test', 0, X'68656C6C6F', 1000000)
        """.trimIndent(), 0, null)
        
        // Set version to 1
        driver.execute(null, "PRAGMA user_version = 1", 0, null)
        
        // Now apply migration to v2
        val database = createDatabase(driver)
        
        // Verify version updated to 2 (not just target, but specifically v2)
        val version = getUserVersion(driver)
        assertEquals(2, version, "Schema version should be 2 after migration")
        
        // Verify FsXattr table exists
        val tables = getTableNames(driver)
        assertTrue(tables.contains("FsXattr"), "FsXattr table should exist after migration")
        
        // Verify old data intact
        val db = XiuperFsDatabase(driver)
        val node = db.fsNodeQueries.selectByPath("/test").executeAsOne()
        assertEquals("/test", node.path)
        assertEquals(0L, node.isDir)
        
        // Verify we can insert xattr
        driver.execute(null, """
            INSERT INTO FsXattr(path, name, value)
            VALUES ('/test', 'user.comment', X'746573742064617461')
        """.trimIndent(), 0, null)
        
        val xattr = driver.executeQuery(null, 
            "SELECT name, value FROM FsXattr WHERE path = '/test'",
            { cursor ->
                cursor.next()
                val name = cursor.getString(0)
                val value = cursor.getBytes(1)
                QueryResult.Value(name to value)
            }, 0, null).value
        
        assertEquals("user.comment", xattr.first)
        assertTrue(xattr.second != null)
    }
    
    @Test
    fun multiStepMigrationPreservesData() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        
        // Create v1 schema
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS FsNode (
                path TEXT NOT NULL PRIMARY KEY,
                isDir INTEGER NOT NULL,
                content BLOB,
                mtimeEpochMillis INTEGER NOT NULL
            )
        """.trimIndent(), 0, null)
        
        driver.execute(null, """
            INSERT INTO FsNode(path, isDir, content, mtimeEpochMillis)
            VALUES ('/', 1, NULL, 900000),
                   ('/file.txt', 0, X'64617461', 1000000)
        """.trimIndent(), 0, null)
        
        driver.execute(null, "PRAGMA user_version = 1", 0, null)
        
        // Migrate to latest
        createDatabase(driver)
        
        // Verify all data preserved
        val db = XiuperFsDatabase(driver)
        val nodes = db.fsNodeQueries.selectAll().executeAsList()
        assertEquals(2, nodes.size)
        
        val root = nodes.find { it.path == "/" }
        assertTrue(root != null && root.isDir == 1L)
        
        val file = nodes.find { it.path == "/file.txt" }
        assertTrue(file != null && file.isDir == 0L)
    }
    
    private fun getUserVersion(driver: SqlDriver): Int {
        return driver.executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            mapper = { cursor ->
                if (cursor.next().value) {
                    QueryResult.Value(cursor.getLong(0)?.toInt() ?: 0)
                } else {
                    QueryResult.Value(0)
                }
            },
            parameters = 0,
            binders = null
        ).value
    }
    
    private fun getTableNames(driver: SqlDriver): List<String> {
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM sqlite_master WHERE type='table'",
            mapper = { cursor ->
                val names = mutableListOf<String>()
                while (cursor.next().value) {
                    cursor.getString(0)?.let { names.add(it) }
                }
                QueryResult.Value(names)
            },
            parameters = 0,
            binders = null
        ).value
    }
}
