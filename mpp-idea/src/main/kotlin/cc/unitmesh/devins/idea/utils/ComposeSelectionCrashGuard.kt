package cc.unitmesh.devins.idea.utils

import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Workaround for a known Compose (IntelliJ Compose / Desktop) selection crash:
 * IllegalArgumentException("layouts are not part of the same hierarchy")
 *
 * This can happen during selection/pointer handling while layouts are disposed
 * (e.g. rapidly updating/streaming content). We only suppress this exact crash.
 */
object ComposeSelectionCrashGuard {

    private val installed = AtomicBoolean(false)
    private val logger = Logger.getInstance(ComposeSelectionCrashGuard::class.java)

    fun install() {
        if (!installed.compareAndSet(false, true)) return

        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isKnownComposeSelectionHierarchyBug(throwable)) {
                logger.warn("Suppressed Compose selection crash on ${thread.name}: ${throwable.message}", throwable)
                return@setDefaultUncaughtExceptionHandler
            }

            previous?.uncaughtException(thread, throwable) ?: run {
                throwable.printStackTrace()
            }
        }

        logger.info("ComposeSelectionCrashGuard installed")
    }

    private fun isKnownComposeSelectionHierarchyBug(throwable: Throwable): Boolean {
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
