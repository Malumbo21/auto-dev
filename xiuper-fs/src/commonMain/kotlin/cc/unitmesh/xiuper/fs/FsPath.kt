package cc.unitmesh.xiuper.fs

/**
 * A minimal POSIX-style path used by xiuper-fs.
 *
 * - Always uses '/' as separator.
 * - Normalization removes '.' segments and resolves '..' within the path.
 * - Does not perform filesystem access.
 */
data class FsPath(val value: String) {
    init {
        require(value.isNotBlank()) { "FsPath must not be blank" }
        require(value.startsWith("/")) { "FsPath must be absolute and start with '/'" }
    }

    fun segments(): List<String> = value.trim('/').takeIf { it.isNotEmpty() }?.split('/') ?: emptyList()

    fun resolve(child: String): FsPath {
        val normalizedChild = child.trim('/').takeIf { it.isNotEmpty() }
            ?: return this
        return FsPath(normalize("$value/$normalizedChild"))
    }

    fun parent(): FsPath? {
        val segs = segments()
        if (segs.isEmpty()) return null
        if (segs.size == 1) return FsPath("/")
        return FsPath("/" + segs.dropLast(1).joinToString("/"))
    }

    companion object {
        fun of(raw: String): FsPath = FsPath(normalize(raw))

        fun normalize(raw: String): String {
            val s = raw.replace('\\', '/').trim()
            val absolute = if (s.startsWith('/')) s else "/$s"

            val out = ArrayDeque<String>()
            for (part in absolute.split('/')) {
                when (part) {
                    "", "." -> Unit
                    ".." -> if (out.isNotEmpty()) out.removeLast() else Unit
                    else -> out.addLast(part)
                }
            }

            return if (out.isEmpty()) "/" else "/" + out.joinToString("/")
        }
    }
}
