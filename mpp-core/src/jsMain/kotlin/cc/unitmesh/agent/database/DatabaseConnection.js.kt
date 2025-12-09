package cc.unitmesh.agent.database

/**
 * JS platform database connection placeholder
 * 
 * JS platform does not support direct database connections.
 * Use server HTTP API to access databases instead.
 */
actual fun createDatabaseConnection(config: DatabaseConfig): DatabaseConnection {
    throw UnsupportedOperationException(
        "Database connections are not supported on JS platform. " +
        "Use the server API to access databases instead."
    )
}
