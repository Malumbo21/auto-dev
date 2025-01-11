package cc.unitmesh.devti.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Service(Service.Level.PROJECT)
class AutoDevCoroutineScope {
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Logger.getInstance(AutoDevCoroutineScope::class.java).error(throwable)
    }

    val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + coroutineExceptionHandler)

    companion object {
        fun scope(project: Project): CoroutineScope = project.service<AutoDevCoroutineScope>().coroutineScope
    }
}