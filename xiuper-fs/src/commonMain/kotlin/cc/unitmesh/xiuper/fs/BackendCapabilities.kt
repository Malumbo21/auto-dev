package cc.unitmesh.xiuper.fs

/**
 * Declares which POSIX-subset operations a backend implements.
 *
 * Conformance tests use this to decide whether to assert full POSIX semantics
 * or only verify that unsupported operations fail with ENOTSUP.
 */
data class BackendCapabilities(
    val supportsMkdir: Boolean = true,
    val supportsDelete: Boolean = true
)

interface CapabilityAwareBackend {
    val capabilities: BackendCapabilities
}
