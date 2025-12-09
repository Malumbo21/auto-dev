package cc.unitmesh.agent.database

/**
 * Android platform database connection placeholder
 * 
 * Android should use Room or other local database frameworks,
 * not direct remote database connections.
 * Use server API when accessing remote databases.
 */
actual fun createDatabaseConnection(config: DatabaseConfig): DatabaseConnection {
    throw UnsupportedOperationException(
        "Direct database connections are not recommended on Android. " +
        "Use Room database for local storage or connect through server API."
    )
}
