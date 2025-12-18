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
