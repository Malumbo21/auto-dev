package cc.unitmesh.devins.idea.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import cc.unitmesh.devins.idea.utils.ComposeSelectionCrashGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineName

/**
 * A service-level class that provides and manages coroutine scopes for a given project.
 * Implements [Disposable] to properly cancel all coroutines when the project is closed.
 *
 * Uses Dispatchers.EDT (IntelliJ's EDT dispatcher) to ensure compatibility with Compose animations
 * which require a MonotonicFrameClock in the coroutine context.
 *
 * @constructor Initializes the [CoroutineScopeHolder] with a project instance.
 * @param project The project this service is associated with.
 */
@Service(Level.PROJECT)
class CoroutineScopeHolder(private val project: Project) : Disposable {

    init {
        // Guard against a known Compose selection crash on the EDT when layouts are disposed
        // during selection/pointer handling (common with rapidly-updating content).
        ComposeSelectionCrashGuard.install()
    }

    private val parentJob = SupervisorJob()

    /**
     * Creates a new coroutine scope as a child of the project-wide coroutine scope with the specified name.
     *
     * Uses Dispatchers.EDT (IntelliJ's EDT dispatcher) to ensure Compose animations work correctly.
     * Compose's animateScrollToItem and other animation APIs require a MonotonicFrameClock
     * which is only available when running on the EDT.
     *
     * @param name The name for the newly created coroutine scope.
     * @return a scope with a Job which parent is the Job of projectWideCoroutineScope scope.
     *
     * The returned scope can be completed only by cancellation.
     * projectWideCoroutineScope scope will cancel the returned scope when canceled.
     * If the child scope has a narrower lifecycle than projectWideCoroutineScope scope,
     * then it should be canceled explicitly when not needed,
     * otherwise, it will continue to live in the Job hierarchy until termination of the CoroutineScopeHolder service.
     */
    fun createScope(name: String): CoroutineScope {
        val childJob = SupervisorJob(parentJob)
        return CoroutineScope(childJob + Dispatchers.EDT + CoroutineName(name))
    }

    /**
     * Cancels all coroutines when the project is disposed.
     */
    override fun dispose() {
        parentJob.cancel()
    }
}

