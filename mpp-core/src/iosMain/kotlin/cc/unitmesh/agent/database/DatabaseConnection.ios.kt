package cc.unitmesh.agent.database

/**
 * iOS platform database connection placeholder
 * 
 * iOS should use Core Data or SQLite for local storage,
 * not direct remote database connections.
 * Use server API when accessing remote databases.
 */
actual fun createDatabaseConnection(config: DatabaseConfig): DatabaseConnection {
    throw UnsupportedOperationException(
        "Direct database connections are not recommended on iOS. " +
        "Use Core Data for local storage or connect through server API."
    )
}
