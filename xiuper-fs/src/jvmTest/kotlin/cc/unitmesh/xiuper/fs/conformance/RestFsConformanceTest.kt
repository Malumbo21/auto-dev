package cc.unitmesh.xiuper.fs.conformance

import cc.unitmesh.xiuper.fs.XiuperFileSystem
import cc.unitmesh.xiuper.fs.http.RestFsBackend
import cc.unitmesh.xiuper.fs.http.RestServiceConfig
import cc.unitmesh.xiuper.fs.http.HttpClientFactory
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * Conformance tests for REST filesystem backend.
 * Verifies capability-aware behavior:
 * - REST backend declares supportsMkdir=false, supportsDelete=false
 * - Conformance tests should pass by asserting ENOTSUP for these operations
 *
 * NOTE: This is a placeholder - real REST conformance requires a mock HTTP server.
 * For now, we just verify that RestFsBackend properly declares its capabilities.
 */
class RestFsConformanceTest {
    @Test
    fun restBackendDeclaresReadOnlyCapabilities() = runTest {
        val config = RestServiceConfig(baseUrl = "http://mock.local")
        val backend = RestFsBackend(config)

        // REST backend should declare it doesn't support mkdir/delete
        assert(!backend.capabilities.supportsMkdir) { "REST backend should not support mkdir" }
        assert(!backend.capabilities.supportsDelete) { "REST backend should not support delete" }
    }
}
