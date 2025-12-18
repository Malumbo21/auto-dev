package cc.unitmesh.xiuper.fs.db.migrations

import app.cash.sqldelight.db.SqlDriver

/**
 * Migration v1 → v2: Add extended attributes support.
 * 
 * Adds FsXattr table for POSIX-style extended attributes (user.*, security.*, etc.).
 * Indexed by path for efficient lookup and cascades on delete.
 */
class Migration_1_to_2 : Migration {
    override val fromVersion = 1
    override val toVersion = 2
    override val description = "Add extended attributes table"
    
    override fun migrate(driver: SqlDriver) {
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE IF NOT EXISTS FsXattr (
                    path TEXT NOT NULL,
                    name TEXT NOT NULL,
                    value BLOB NOT NULL,
                    PRIMARY KEY (path, name),
                    FOREIGN KEY (path) REFERENCES FsNode(path) ON DELETE CASCADE
                )
            """.trimIndent(),
            parameters = 0,
            binders = null
        )
        
        // Create index for efficient lookup by path
        driver.execute(
            identifier = null,
            sql = """
                CREATE INDEX IF NOT EXISTS idx_xattr_path ON FsXattr(path)
            """.trimIndent(),
            parameters = 0,
            binders = null
        )
    }
}
