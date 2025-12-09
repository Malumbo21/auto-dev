package cc.unitmesh.agent.database

/**
 * WASM 平台数据库连接占位符
 * 
 * WASM 平台当前不支持直接的数据库连接。
 * 应该通过服务器的 HTTP API 调用来访问数据库。
 */
actual fun createDatabaseConnection(config: DatabaseConfig): DatabaseConnection {
    throw UnsupportedOperationException(
        "Database connections are not supported on WASM platform. " +
        "Use the server API to access databases instead."
    )
}
