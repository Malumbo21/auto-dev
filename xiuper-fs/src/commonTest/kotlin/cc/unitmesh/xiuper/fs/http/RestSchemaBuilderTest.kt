package cc.unitmesh.xiuper.fs.http

import cc.unitmesh.xiuper.fs.FsEntry
import cc.unitmesh.xiuper.fs.FsPath
import cc.unitmesh.xiuper.fs.ReadResult
import cc.unitmesh.xiuper.fs.WriteOptions
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RestSchemaBuilderTest {
    @Test
    fun builderMatchesVarAndAffixSegments() {
        val schema = RestSchema.builder()
            .read("/x/{id}/page_{page}/data.json") { p, _ ->
                ResolvedRead(HttpCall(url = "id=${p["id"]}&page=${p["page"]}", method = HttpMethod.Get))
            }
            .build()

        val rr = schema.resolveRead(FsPath.of("/x/abc/page_12/data.json"), NoopRuntime)
        assertNotNull(rr)
        assertEquals("id=abc&page=12", rr.http.url)
    }

    @Test
    fun builderCanReturnLocalWrite() {
        val schema = RestSchema.builder()
            .write("/control/query") { _, content, _, _ ->
                ResolvedWrite.Local { rt ->
                    rt.setQuery("/control", mapOf("raw" to content.decodeToString()))
                    cc.unitmesh.xiuper.fs.WriteResult(ok = true)
                }
            }
            .build()

        val rt = FakeRuntime()
        val w = schema.resolveWrite(FsPath.of("/control/query"), "k=v".encodeToByteArray(), WriteOptions(), rt)
        assertNotNull(w)
        (w as ResolvedWrite.Local).action(rt)
        assertEquals("k=v", rt.getQuery("/control")?.get("raw"))
    }

    @Test
    fun readTransformCanPostProcess() {
        val schema = RestSchema.builder()
            .read("/t") { _, _ ->
                ResolvedRead(
                    http = HttpCall(url = "t", method = HttpMethod.Get),
                    transform = { ReadResult(bytes = "ok".encodeToByteArray(), contentType = "text/plain") }
                )
            }
            .build()

        val rr = schema.resolveRead(FsPath.of("/t"), NoopRuntime)
        assertNotNull(rr)
        val out = rr.transform!!.invoke(ReadResult(bytes = "raw".encodeToByteArray(), contentType = "application/json"))
        assertEquals("ok", out.bytes.decodeToString())
    }

    private object NoopRuntime : RestSchemaRuntime {
        override fun setQuery(scopePath: String, query: Map<String, String>) = Unit
        override fun getQuery(scopePath: String): Map<String, String>? = null
    }

    private class FakeRuntime : RestSchemaRuntime {
        private val queryByScope = mutableMapOf<String, Map<String, String>>()

        override fun setQuery(scopePath: String, query: Map<String, String>) {
            queryByScope[scopePath] = query
        }

        override fun getQuery(scopePath: String): Map<String, String>? = queryByScope[scopePath]
    }
}
