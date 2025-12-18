package cc.unitmesh.xiuper.fs.db

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(driverFactory: DatabaseDriverFactory): XiuperFsDatabase {
    val driver = driverFactory.createDriver()
    XiuperFsDatabase.Schema.create(driver)
    return XiuperFsDatabase(driver)
}
