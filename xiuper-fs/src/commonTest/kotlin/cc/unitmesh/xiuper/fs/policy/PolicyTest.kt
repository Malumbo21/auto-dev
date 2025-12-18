package cc.unitmesh.xiuper.fs.policy

import cc.unitmesh.xiuper.fs.FsErrorCode
import cc.unitmesh.xiuper.fs.FsPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class PathFilterPolicyTest {
    @Test
    fun allowlistAllowsMatchingPaths() = runTest {
        val policy = PathFilterPolicy(
            mode = PathFilterPolicy.Mode.ALLOWLIST,
            patterns = listOf(
                PathPattern.Prefix("/allowed"),
                PathPattern.Exact("/special")
            )
        )
        
        // Should allow
        assertNull(policy.checkOperation(FsOperation.READ, FsPath("/allowed/file.txt")))
        assertNull(policy.checkOperation(FsOperation.READ, FsPath("/special")))
        
        // Should deny
        val error = policy.checkOperation(FsOperation.READ, FsPath("/denied/file.txt"))
        assertNotNull(error)
        assertEquals(FsErrorCode.EACCES, error.code)
    }
    
    @Test
    fun denylistDeniesMatchingPaths() = runTest {
        val policy = PathFilterPolicy(
            mode = PathFilterPolicy.Mode.DENYLIST,
            patterns = listOf(
                PathPattern.Prefix("/tmp"),
                PathPattern.Wildcard("/secrets/*")
            )
        )
        
        // Should deny
        assertNotNull(policy.checkOperation(FsOperation.READ, FsPath("/tmp/file.txt")))
        assertNotNull(policy.checkOperation(FsOperation.READ, FsPath("/secrets/api-key")))
        
        // Should allow
        assertNull(policy.checkOperation(FsOperation.READ, FsPath("/public/file.txt")))
    }
    
    @Test
    fun pathPatternParsing() {
        val exact = PathPattern.parse("/exact/path")
        assert(exact is PathPattern.Exact)
        
        val prefix = PathPattern.parse("/prefix/")
        assert(prefix is PathPattern.Prefix)
        
        val wildcard = PathPattern.parse("/path/*/file")
        assert(wildcard is PathPattern.Wildcard)
    }
    
    @Test
    fun wildcardPatternMatching() {
        val pattern = PathPattern.Wildcard("/api/*/users")
        
        assert(pattern.matches(FsPath("/api/v1/users")))
        assert(pattern.matches(FsPath("/api/v2/users")))
        assert(!pattern.matches(FsPath("/api/v1/posts")))
    }
}

class DeleteApprovalPolicyTest {
    @Test
    fun requiresApprovalForDelete() = runTest {
        var approvalRequested = false
        var pathRequested: FsPath? = null
        
        val policy = DeleteApprovalPolicy(
            approvalProvider = { path ->
                approvalRequested = true
                pathRequested = path
                false // Deny
            }
        )
        
        val error = policy.checkOperation(FsOperation.DELETE, FsPath("/important/file"))
        
        assert(approvalRequested)
        assertEquals("/important/file", pathRequested?.value)
        assertNotNull(error)
        assertEquals(FsErrorCode.EACCES, error.code)
    }
    
    @Test
    fun allowsDeleteWhenApproved() = runTest {
        val policy = DeleteApprovalPolicy(
            approvalProvider = { true } // Approve
        )
        
        val error = policy.checkOperation(FsOperation.DELETE, FsPath("/file"))
        assertNull(error)
    }
    
    @Test
    fun doesNotRequireApprovalForOtherOperations() = runTest {
        var approvalRequested = false
        
        val policy = DeleteApprovalPolicy(
            approvalProvider = {
                approvalRequested = true
                false
            }
        )
        
        // Read should not require approval
        val error = policy.checkOperation(FsOperation.READ, FsPath("/file"))
        assert(!approvalRequested)
        assertNull(error)
    }
}
