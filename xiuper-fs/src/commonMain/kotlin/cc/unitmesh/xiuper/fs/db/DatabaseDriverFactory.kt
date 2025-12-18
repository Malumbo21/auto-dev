package cc.unitmesh.xiuper.fs.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.QueryResult
import cc.unitmesh.xiuper.fs.db.migrations.MigrationRegistry

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

/**
 * Create database instance and apply migrations if necessary.
 * Exposed for testing with custom drivers.
 */
fun createDatabase(driver: SqlDriver): XiuperFsDatabase {
    val currentVersion = getUserVersion(driver)
    val targetVersion = XiuperFsDatabase.Schema.version.toInt()
    
    when {
        currentVersion == 0 -> {
            // Fresh database: create latest schema directly
            XiuperFsDatabase.Schema.create(driver)
            setUserVersion(driver, targetVersion)
        }
        currentVersion < targetVersion -> {
            // Existing database: apply incremental migrations
            val migrations = MigrationRegistry.path(currentVersion, targetVersion)
            for (migration in migrations) {
                try {
                    migration.migrate(driver)
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "Migration failed: ${migration.description} " +
                        "(${migration.fromVersion} → ${migration.toVersion})",
                        e
                    )
                }
            }
            setUserVersion(driver, targetVersion)
        }
        currentVersion > targetVersion -> {
            // Future database opened by older code
            throw IllegalStateException(
                "Database version $currentVersion is newer than supported $targetVersion. " +
                "Please upgrade the application."
            )
        }
        // else: already up-to-date, no action needed
    }
    
    return XiuperFsDatabase(driver)
}

/**
 * Create database instance from a platform-specific driver factory.
 */
fun createDatabase(driverFactory: DatabaseDriverFactory): XiuperFsDatabase {
    return createDatabase(driverFactory.createDriver())
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

private fun setUserVersion(driver: SqlDriver, version: Int) {
    driver.execute(
        identifier = null,
        sql = "PRAGMA user_version = $version",
        parameters = 0,
        binders = null
    )
}

