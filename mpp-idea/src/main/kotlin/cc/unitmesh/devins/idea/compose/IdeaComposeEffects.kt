package cc.unitmesh.devins.idea.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.idea.services.CoroutineScopeHolder
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import javax.swing.Timer

/**
 * Compose effect utilities that avoid ClassLoader conflicts with IntelliJ's bundled Compose.
 *
 * These utilities replace Compose's built-in coroutine APIs (LaunchedEffect, rememberCoroutineScope,
 * collectAsState) which cause ClassLoader conflicts because IntelliJ's Compose runtime expects
 * CoroutineScope from its own ClassLoader, but the plugin provides one from a different ClassLoader.
 *
 * Solution: Use DisposableEffect (which doesn't use coroutines internally) combined with
 * IntelliJ's CoroutineScopeHolder service.
 */

// Application-level fallback scope for when no project is available
// Uses Dispatchers.EDT (IntelliJ's EDT dispatcher) to ensure EDT execution for Compose animations
private val appScope: CoroutineScope by lazy {
    CoroutineScope(SupervisorJob() + Dispatchers.EDT)
}

internal class IdeaComposeScopeHandle(
    val scope: CoroutineScope,
    private val ownsScope: Boolean
) {
    fun dispose() {
        if (ownsScope) {
            scope.cancel()
        }
    }
}

internal fun createIdeaComposeScopeHandle(
    project: Project?,
    name: String = "IdeaComposeEffect",
    projectScopeFactory: (Project, String) -> CoroutineScope = { scopeProject, scopeName ->
        scopeProject.service<CoroutineScopeHolder>().createScope(scopeName)
    },
    fallbackScope: CoroutineScope = appScope
): IdeaComposeScopeHandle {
    return if (project != null && !project.isDisposed) {
        IdeaComposeScopeHandle(projectScopeFactory(project, name), ownsScope = true)
    } else {
        IdeaComposeScopeHandle(fallbackScope, ownsScope = false)
    }
}

/**
 * Replacement for rememberCoroutineScope() that uses IntelliJ's coroutine scope.
 * This avoids ClassLoader conflicts with Compose's internal coroutine usage.
 */
@Composable
fun rememberIdeaCoroutineScope(project: Project? = null): CoroutineScope {
    val handle = remember(project) {
        createIdeaComposeScopeHandle(project, name = "IdeaRememberedCoroutineScope")
    }

    DisposableEffect(handle) {
        onDispose { handle.dispose() }
    }

    return handle.scope
}

/**
 * Replacement for LaunchedEffect that uses IntelliJ's coroutine scope.
 * Uses DisposableEffect internally to avoid ClassLoader conflicts.
 *
 * @param key1 Key that triggers re-execution when changed
 * @param project Optional project for project-scoped coroutines
 * @param block The suspend block to execute
 */
@Composable
fun IdeaLaunchedEffect(
    key1: Any?,
    project: Project? = null,
    block: suspend CoroutineScope.() -> Unit
) {
    DisposableEffect(key1, project) {
        val handle = createIdeaComposeScopeHandle(project)
        val job = handle.scope.launch { block() }
        onDispose {
            job.cancel()
            handle.dispose()
        }
    }
}

/**
 * Replacement for LaunchedEffect with two keys.
 */
@Composable
fun IdeaLaunchedEffect(
    key1: Any?,
    key2: Any?,
    project: Project? = null,
    block: suspend CoroutineScope.() -> Unit
) {
    DisposableEffect(key1, key2, project) {
        val handle = createIdeaComposeScopeHandle(project)
        val job = handle.scope.launch { block() }
        onDispose {
            job.cancel()
            handle.dispose()
        }
    }
}

/**
 * Replacement for LaunchedEffect with three keys.
 */
@Composable
fun IdeaLaunchedEffect(
    key1: Any?,
    key2: Any?,
    key3: Any?,
    project: Project? = null,
    block: suspend CoroutineScope.() -> Unit
) {
    DisposableEffect(key1, key2, key3, project) {
        val handle = createIdeaComposeScopeHandle(project)
        val job = handle.scope.launch { block() }
        onDispose {
            job.cancel()
            handle.dispose()
        }
    }
}

/**
 * Custom CircularProgressIndicator that avoids ClassLoader conflicts.
 *
 * Jewel's CircularProgressIndicator uses Dispatchers.getDefault() internally,
 * which causes ClassLoader conflicts between the plugin's coroutines and
 * IntelliJ's bundled Compose runtime.
 *
 * This implementation uses a Swing Timer for animation instead of coroutines.
 */
@Composable
fun IdeaCircularProgressIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    strokeWidth: Dp = 2.dp,
    color: Color? = null
) {
    var rotation by remember { mutableStateOf(0f) }

    // Use Swing Timer instead of coroutines to avoid ClassLoader conflicts
    DisposableEffect(Unit) {
        val timer = Timer(16) { // ~60fps
            rotation = (rotation + 6f) % 360f
        }
        timer.start()
        onDispose { timer.stop() }
    }

    val progressColor = color ?: JewelTheme.globalColors.text.info

    Canvas(modifier = modifier.size(size)) {
        val strokePx = strokeWidth.toPx()
        val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
        val topLeft = Offset(strokePx / 2, strokePx / 2)

        // Draw spinning arc
        drawArc(
            color = progressColor,
            startAngle = rotation,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
    }
}
