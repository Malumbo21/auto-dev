package cc.unitmesh.xiuper.fs.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        // Requires browser environment; wasmJs tests/build should still compile.
        return WebWorkerDriver(
            WorkerJs.worker(),
        )
    }
}

private object WorkerJs {
    fun worker(): dynamic {
        // See @cashapp/sqldelight-sqljs-worker docs; this is a lightweight bridge.
        // In actual browser setup, bundler will provide Worker.
        return js("new Worker(new URL('@cashapp/sqldelight-sqljs-worker/sqljs.worker.js', import.meta.url), { type: 'module' })")
    }
}
