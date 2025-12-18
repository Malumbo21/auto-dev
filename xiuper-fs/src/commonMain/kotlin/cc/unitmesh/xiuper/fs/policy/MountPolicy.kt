package cc.unitmesh.xiuper.fs.policy

import cc.unitmesh.xiuper.fs.FsErrorCode
import cc.unitmesh.xiuper.fs.FsException
import cc.unitmesh.xiuper.fs.FsPath

interface MountPolicy {
    suspend fun checkOperation(op: FsOperation, path: FsPath): FsException?
    
    suspend fun checkWrite(path: FsPath): FsException? = 
        checkOperation(FsOperation.WRITE, path)
    
    suspend fun checkDelete(path: FsPath): FsException? = 
        checkOperation(FsOperation.DELETE, path)
    
    companion object {
        val AllowAll = object : MountPolicy {
            override suspend fun checkOperation(op: FsOperation, path: FsPath): FsException? = null
        }
        
        val ReadOnly = object : MountPolicy {
            override suspend fun checkOperation(op: FsOperation, path: FsPath): FsException? {
                return when (op) {
                    FsOperation.WRITE, FsOperation.DELETE, FsOperation.MKDIR, FsOperation.COMMIT ->
                        FsException(FsErrorCode.EACCES, "Read-only policy")
                    else -> null
                }
            }
        }
    }
}

/**
 * Path-based policy: allowlist or denylist.
 */
class PathFilterPolicy(
    private val mode: Mode,
    private val patterns: List<PathPattern>,
    private val delegate: MountPolicy = MountPolicy.AllowAll
) : MountPolicy {
    enum class Mode {
        ALLOWLIST,
        DENYLIST
    }
    
    override suspend fun checkOperation(op: FsOperation, path: FsPath): FsException? {
        val matches = patterns.any { it.matches(path) }
        val allowed = when (mode) {
            Mode.ALLOWLIST -> matches
            Mode.DENYLIST -> !matches
        }
        
        return if (allowed) {
            delegate.checkOperation(op, path)
        } else {
            FsException(FsErrorCode.EACCES, "Path not allowed by policy: ${path.value}")
        }
    }
}

sealed interface PathPattern {
    fun matches(path: FsPath): Boolean
    
    data class Exact(val pattern: String) : PathPattern {
        override fun matches(path: FsPath): Boolean = path.value == pattern
    }
    
    data class Prefix(val prefix: String) : PathPattern {
        override fun matches(path: FsPath): Boolean = 
            path.value == prefix || path.value.startsWith("$prefix/")
    }
    
    data class Wildcard(val pattern: String) : PathPattern {
        private val regex: Regex

        init {
            // Prevent ReDoS attacks by limiting pattern complexity
            require(pattern.length <= 256) { "Pattern too long (max 256 characters)" }
            require(pattern.count { it == '*' } <= 10) { "Too many wildcards (max 10)" }

            // Use [^/]* instead of .* to match single path segments (more secure and semantically correct)
            regex = pattern
                .replace(".", "\\.")
                .replace("*", "[^/]*")  // Non-greedy, single segment match
                .toRegex()
        }

        override fun matches(path: FsPath): Boolean = regex.matches(path.value)
    }
    
    companion object {
        fun parse(pattern: String): PathPattern = when {
            "*" in pattern -> Wildcard(pattern)
            pattern.endsWith("/") -> Prefix(pattern.trimEnd('/'))
            else -> Exact(pattern)
        }
    }
}

/**
 * Delete approval policy: requires explicit confirmation for delete operations.
 */
class DeleteApprovalPolicy(
    private val approvalProvider: suspend (FsPath) -> Boolean,
    private val delegate: MountPolicy = MountPolicy.AllowAll
) : MountPolicy {
    override suspend fun checkOperation(op: FsOperation, path: FsPath): FsException? {
        if (op == FsOperation.DELETE) {
            val approved = approvalProvider(path)
            if (!approved) {
                return FsException(FsErrorCode.EACCES, "Delete not approved: ${path.value}")
            }
        }
        return delegate.checkOperation(op, path)
    }
}
