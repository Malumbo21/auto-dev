package cc.unitmesh.xiuper.fs

import kotlinx.datetime.Instant

enum class FsErrorCode {
    ENOENT,
    EACCES,
    EEXIST,
    EINVAL,
    EIO,
    ENOTDIR,
    ENOTEMPTY,
    EISDIR,
    ENOTSUP
}

class FsException(
    val code: FsErrorCode,
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

sealed class FsEntry {
    abstract val name: String

    data class File(
        override val name: String,
        val size: Long? = null,
        val mime: String? = null,
        val modifiedAt: Instant? = null
    ) : FsEntry()

    data class Directory(override val name: String) : FsEntry()

    data class Special(
        override val name: String,
        val kind: SpecialKind
    ) : FsEntry()

    enum class SpecialKind {
        MagicNew,
        ControlQuery,
        ControlCommit,
        ToolArgs,
        ToolRun
    }
}

data class FsStat(
    val path: FsPath,
    val isDirectory: Boolean,
    val size: Long? = null,
    val mime: String? = null
)

data class ReadOptions(
    val preferText: Boolean = true
)

enum class WriteCommitMode {
    Direct,
    OnClose,
    OnExplicitCommit
}

data class WriteOptions(
    val commitMode: WriteCommitMode = WriteCommitMode.OnClose,
    val contentType: String? = null
)

data class ReadResult(
    val bytes: ByteArray,
    val contentType: String? = null
) {
    fun textOrNull(): String? = runCatching { bytes.decodeToString() }.getOrNull()
}

data class WriteResult(
    val ok: Boolean,
    val message: String? = null
)
