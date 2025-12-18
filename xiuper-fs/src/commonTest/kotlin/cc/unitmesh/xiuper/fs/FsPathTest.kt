package cc.unitmesh.xiuper.fs

import kotlin.test.Test
import kotlin.test.assertEquals

class FsPathTest {
    @Test
    fun normalizeRemovesDotSegments() {
        assertEquals("/a/b", FsPath.normalize("/a/./b"))
        assertEquals("/a/b", FsPath.normalize("/a/x/../b"))
        assertEquals("/", FsPath.normalize("/../"))
    }

    @Test
    fun resolveJoinsAndNormalizes() {
        val p = FsPath.of("/a").resolve("b/../c")
        assertEquals("/a/c", p.value)
    }
}
