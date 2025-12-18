package cc.unitmesh.xiuper.fs.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(XiuperFsDatabase.Schema, "xiuper-fs.db")
    }
}
