package cc.unitmesh.xiuper.fs.db.migrations

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cc.unitmesh.xiuper.fs.db.XiuperFsDatabase
import cc.unitmesh.xiuper.fs.db.createDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MigrationTest {
    
    @Test
    fun freshDatabaseCreatesLatestSchemaAndSetsVersion() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        
        val database = createDatabase(driver)
        
        // Verify schema version was set
        val version = getUserVersion(driver)
        assertEquals(XiuperFsDatabase.Schema.version.toInt(), version)
        
        // Verify schema was created
        val tables = getTableNames(driver)
        assertTrue(tables.contains("FsNode"), "FsNode table should exist")
    }
    
    @Test
    fun upToDateDatabaseSkipsMigrations() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        
        // Create initial DB
        XiuperFsDatabase.Schema.create(driver)
        setUserVersion(driver, XiuperFsDatabase.Schema.version.toInt())
        
        // Open again (should be no-op)
        val database = createDatabase(driver)
        
        val version = getUserVersion(driver)
        assertEquals(XiuperFsDatabase.Schema.version.toInt(), version)
    }
    
    @Test
    fun futureVersionThrowsError() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        
        XiuperFsDatabase.Schema.create(driver)
        val futureVersion = XiuperFsDatabase.Schema.version.toInt() + 100
        setUserVersion(driver, futureVersion)
        
        val exception = assertFailsWith<IllegalStateException> {
            createDatabase(driver)
        }
        assertTrue(exception.message!!.contains("newer than supported"))
    }
    
    @Test
    fun migrationRegistryRejectsDowngrade() {
        val exception = assertFailsWith<UnsupportedOperationException> {
            MigrationRegistry.path(current = 2, target = 1)
        }
        assertTrue(exception.message!!.contains("Downgrade"))
    }
    
    @Test
    fun migrationRegistryReturnsEmptyForSameVersion() {
        val path = MigrationRegistry.path(current = 1, target = 1)
        assertTrue(path.isEmpty())
    }
    
    @Test
    fun migrationRegistryThrowsIfNoPathExists() {
        // Currently registry has v1→v2; v2→v3 should fail
        val exception = assertFailsWith<IllegalStateException> {
            MigrationRegistry.path(current = 2, target = 3)
        }
        assertTrue(exception.message!!.contains("No migration found"))
    }
    
    // Helper functions
    
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
    
    private fun setUserVersion(driver: SqlDriver, version: Int) {
        driver.execute(
            identifier = null,
            sql = "PRAGMA user_version = $version",
            parameters = 0,
            binders = null
        )
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
