package cc.unitmesh.devti.util

import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class AutoDevCoroutineScopeTest {

    @Test
    fun `project worker scope selector returns worker scope`() {
        val service = AutoDevCoroutineScope()

        try {
            val selected = selectWorkerScope(service)

            assertSame(service.workerScope, selected)
            assertNotSame(service.coroutineScope, selected)
        } finally {
            service.dispose()
        }
    }
}
