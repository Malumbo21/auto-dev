package cc.unitmesh.xiuper.fs.conformance

import cc.unitmesh.xiuper.fs.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

suspend fun runPosixSubsetConformance(vfs: XiuperFileSystem) {
    // Root exists and is a directory
    assertTrue(vfs.stat(FsPath.of("/")).isDirectory)

    // mkdir basic behavior
    vfs.mkdir(FsPath.of("/tmp"))
    assertTrue(vfs.stat(FsPath.of("/tmp")).isDirectory)

    // mkdir on existing path -> EEXIST
    assertFailsWith<FsException> {
        vfs.mkdir(FsPath.of("/tmp"))
    }.also { assertEquals(FsErrorCode.EEXIST, it.code) }

    // write create-or-truncate
    vfs.write(FsPath.of("/tmp/hello.txt"), "hello".encodeToByteArray())
    val read1 = vfs.read(FsPath.of("/tmp/hello.txt")).bytes.decodeToString()
    assertEquals("hello", read1)

    vfs.write(FsPath.of("/tmp/hello.txt"), "h".encodeToByteArray())
    val read2 = vfs.read(FsPath.of("/tmp/hello.txt")).bytes.decodeToString()
    assertEquals("h", read2)

    // list entries
    val entries = vfs.list(FsPath.of("/tmp")).map { it.name }.toSet()
    assertTrue(entries.contains("hello.txt"))

    // delete file
    vfs.delete(FsPath.of("/tmp/hello.txt"))
    assertFailsWith<FsException> {
        vfs.stat(FsPath.of("/tmp/hello.txt"))
    }.also { assertEquals(FsErrorCode.ENOENT, it.code) }

    // delete non-empty directory -> ENOTEMPTY
    vfs.write(FsPath.of("/tmp/a.txt"), "a".encodeToByteArray())
    assertFailsWith<FsException> {
        vfs.delete(FsPath.of("/tmp"))
    }.also { assertEquals(FsErrorCode.ENOTEMPTY, it.code) }

    // delete empty directory
    vfs.delete(FsPath.of("/tmp/a.txt"))
    vfs.delete(FsPath.of("/tmp"))
    assertFailsWith<FsException> {
        vfs.stat(FsPath.of("/tmp"))
    }.also { assertEquals(FsErrorCode.ENOENT, it.code) }
}
