package cc.unitmesh.xiuper.fs.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        // WASM doesn't have a built-in SQLDelight driver here.
        // This keeps compilation working; DB backend should not be used on WASM until a driver is provided.
        return NoopSqlDriver
    }
}

private object NoopSqlDriver : SqlDriver {
    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        throw UnsupportedOperationException("SQLDelight driver not available on WASM")
    }

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
        throw UnsupportedOperationException("SQLDelight driver not available on WASM")
    }

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        throw UnsupportedOperationException("SQLDelight driver not available on WASM")
    }

    override fun currentTransaction(): Transacter.Transaction? = null

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {}

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {}

    override fun notifyListeners(vararg queryKeys: String) {}

    override fun close() {}
}
