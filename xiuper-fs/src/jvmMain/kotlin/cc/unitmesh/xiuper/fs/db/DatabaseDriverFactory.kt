package cc.unitmesh.xiuper.fs.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        // Intentionally use a file-based DB for production; tests should provide their own driver.
        return JdbcSqliteDriver("jdbc:sqlite:xiuper-fs.db")
    }
}
