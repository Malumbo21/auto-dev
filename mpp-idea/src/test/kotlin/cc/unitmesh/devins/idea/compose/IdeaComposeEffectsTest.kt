package cc.unitmesh.devins.idea.compose

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdeaComposeEffectsTest {

    @Test
    fun `project compose scope handle cancels owned project scope on dispose`() {
        val project = projectProxy(isDisposed = false)
        val job = SupervisorJob()
        val ownedScope = CoroutineScope(job + Dispatchers.Unconfined)

        val handle = createIdeaComposeScopeHandle(
            project = project,
            name = "test-scope",
            projectScopeFactory = { _, _ -> ownedScope },
            fallbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )

        assertTrue(job.isActive)
        assertTrue(handle.scope.coroutineContext[Job]!!.isActive)

        handle.dispose()

        assertFalse(job.isActive)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `fallback compose scope handle does not cancel shared fallback scope on dispose`() {
        val fallbackJob = SupervisorJob()
        val fallbackScope = CoroutineScope(fallbackJob + Dispatchers.Unconfined)

        val handle = createIdeaComposeScopeHandle(
            project = null,
            name = "test-scope",
            projectScopeFactory = { _, _ -> error("project scope should not be requested") },
            fallbackScope = fallbackScope
        )

        handle.dispose()

        assertTrue(fallbackJob.isActive)
    }

    private fun projectProxy(isDisposed: Boolean): Project {
        return Proxy.newProxyInstance(
            Project::class.java.classLoader,
            arrayOf(Project::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "isDisposed" -> isDisposed
                "getName" -> "test-project"
                "toString" -> "ProjectProxy(test-project)"
                else -> null
            }
        } as Project
    }
}
