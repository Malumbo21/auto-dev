package cc.unitmesh.devins.ui.desktop

import cc.unitmesh.agent.logging.AutoDevLogger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Workaround for a known Compose Desktop selection crash:
 * IllegalArgumentException("layouts are not part of the same hierarchy")
 *
 * This exception can be thrown from SelectionManager during pointer/selection handling
 * while the selected layout is being disposed/recomposed (e.g. streaming content).
 *
 * We intentionally only suppress this specific failure to keep the UI alive.
 */
object ComposeSelectionCrashGuard {

    private val installed = AtomicBoolean(false)

    fun install() {
        if (!installed.compareAndSet(false, true)) return

        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isKnownComposeSelectionHierarchyBug(throwable)) {
                AutoDevLogger.warn("ComposeSelectionCrashGuard") {
                    "Suppressed Compose selection crash on ${thread.name}: ${throwable.message}"
                }
                return@setDefaultUncaughtExceptionHandler
            }

            previous?.uncaughtException(thread, throwable) ?: run {
                // Fallback to default behavior.
                throwable.printStackTrace()
            }
        }

        AutoDevLogger.info("ComposeSelectionCrashGuard") { "Installed" }
    }

    private fun isKnownComposeSelectionHierarchyBug(throwable: Throwable): Boolean {
        // Sometimes the IllegalArgumentException is wrapped by higher-level exceptions.
        var current: Throwable? = throwable
        while (current != null) {
            if (current is IllegalArgumentException &&
                current.message?.contains("layouts are not part of the same hierarchy") == true
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
