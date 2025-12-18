package cc.unitmesh.xiuper.fs.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseDriverFactory {
    private var context: Context? = null

    fun init(context: Context) {
        this.context = context
    }

    actual fun createDriver(): SqlDriver {
        val context = requireNotNull(context) { "DatabaseDriverFactory.init(context) must be called before createDriver()" }
        return AndroidSqliteDriver(XiuperFsDatabase.Schema, context, "xiuper-fs.db")
    }
}
