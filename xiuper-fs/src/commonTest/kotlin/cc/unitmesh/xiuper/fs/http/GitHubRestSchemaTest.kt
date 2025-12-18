package cc.unitmesh.xiuper.fs.http

import cc.unitmesh.xiuper.fs.FsEntry
import cc.unitmesh.xiuper.fs.FsPath
import cc.unitmesh.xiuper.fs.WriteOptions
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitHubRestSchemaTest {
    @Test
    fun listIssuesRootContainsMagicAndControlEntries() {
        val schema = RestSchema.githubIssues()
        val rt = FakeRuntime()

        val entries = schema.list(FsPath.of("/github/unit-mesh/auto-dev/issues"), rt)
        val names = entries.map { it.name }.toSet()

        assertTrue("page_1" in names)
        assertTrue("new" in names)
        assertTrue("query" in names)
        assertTrue("results" in names)

        val newEntry = entries.first { it.name == "new" }
        assertTrue(newEntry is FsEntry.Special)
        assertEquals(FsEntry.SpecialKind.MagicNew, (newEntry as FsEntry.Special).kind)

        val queryEntry = entries.first { it.name == "query" }
        assertTrue(queryEntry is FsEntry.Special)
        assertEquals(FsEntry.SpecialKind.ControlQuery, (queryEntry as FsEntry.Special).kind)
    }

    @Test
    fun resolveIssueDataJsonToIssueEndpoint() {
        val schema = RestSchema.githubIssues()
        val rt = FakeRuntime()

        val rr = schema.resolveRead(FsPath.of("/github/phodal/xiuper/issues/123/data.json"), rt)
        assertNotNull(rr)
        assertEquals(HttpMethod.Get, rr.http.method)
        assertEquals("repos/phodal/xiuper/issues/123", rr.http.url)
    }

    @Test
    fun resolveFieldTitleReadIsProjectionOfIssueEndpoint() {
        val schema = RestSchema.githubIssues()
        val rt = FakeRuntime()

        val rr = schema.resolveRead(FsPath.of("/github/phodal/xiuper/issues/123/fields/title"), rt)
        assertNotNull(rr)
        assertEquals(HttpMethod.Get, rr.http.method)
        assertEquals("repos/phodal/xiuper/issues/123", rr.http.url)
        assertNotNull(rr.transform)
    }

    @Test
    fun resolveNewIssueWriteIsPostToIssuesCollection() {
        val schema = RestSchema.githubIssues()
        val rt = FakeRuntime()

        val w = schema.resolveWrite(
            FsPath.of("/github/phodal/xiuper/issues/new"),
            "{\"title\":\"hi\"}".encodeToByteArray(),
            WriteOptions(contentType = "application/json"),
            rt
        )
        assertNotNull(w)
        val http = w as? ResolvedWrite.Http
        assertNotNull(http)
        assertEquals(HttpMethod.Post, http.http.method)
        assertEquals("repos/phodal/xiuper/issues", http.http.url)
        assertEquals("application/json", http.http.contentType)
    }

    @Test
    fun queryStateAffectsResultsPaginationRead() {
        val schema = RestSchema.githubIssues()
        val rt = FakeRuntime()

        val w = schema.resolveWrite(
            FsPath.of("/github/phodal/xiuper/issues/query"),
            "state=open".encodeToByteArray(),
            WriteOptions(),
            rt
        )
        assertNotNull(w)
        val local = w as? ResolvedWrite.Local
        assertNotNull(local)
        local.action(rt)

        val rr = schema.resolveRead(FsPath.of("/github/phodal/xiuper/issues/results/page_2/data.json"), rt)
        assertNotNull(rr)
        assertEquals("repos/phodal/xiuper/issues?page=2&state=open", rr.http.url)
    }

    @Test
    fun resolvePageDataJsonToIssuesListEndpoint() {
        val schema = RestSchema.githubIssues()
        val rt = FakeRuntime()

        val rr = schema.resolveRead(FsPath.of("/github/phodal/xiuper/issues/page_3/data.json"), rt)
        assertNotNull(rr)
        assertEquals("repos/phodal/xiuper/issues?page=3", rr.http.url)
    }

    private class FakeRuntime : RestSchemaRuntime {
        private val queryByScope = mutableMapOf<String, Map<String, String>>()

        override fun setQuery(scopePath: String, query: Map<String, String>) {
            queryByScope[scopePath] = query
        }

        override fun getQuery(scopePath: String): Map<String, String>? = queryByScope[scopePath]
    }
}
