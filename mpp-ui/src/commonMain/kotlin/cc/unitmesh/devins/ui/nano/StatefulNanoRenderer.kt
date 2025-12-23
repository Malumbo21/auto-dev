package cc.unitmesh.devins.ui.nano

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.devins.ui.nano.theme.NanoThemeState
import cc.unitmesh.devins.ui.nano.theme.LocalNanoThemeApplied
import cc.unitmesh.devins.ui.nano.theme.ProvideNanoTheme
import cc.unitmesh.devins.ui.nano.theme.rememberNanoThemeState
import cc.unitmesh.xuiper.render.stateful.NanoNodeRegistry
import cc.unitmesh.xuiper.render.stateful.StatefulNanoNodeDispatcher
import cc.unitmesh.xuiper.render.stateful.StatefulNanoSession

/**
 * Stateful NanoUI Compose Renderer
 *
 * This renderer maintains state and handles actions for interactive NanoDSL components.
 * It wraps the component rendering with a state context that:
 * 1. Initializes state from NanoIR state definitions
 * 2. Passes state values to components via bindings
 * 3. Updates state when actions are triggered
 * 4. Generates images for Image components if ImageGenerationService is provided
 *
 * Architecture:
 * - Uses [NanoNodeRegistry] for component type -> renderer mapping
 * - Uses [StatefulNanoNodeDispatcher] for tree traversal with state management
 * - Platform implementations (Compose, Jewel, HTML) provide their own registries
 *
 * Components are organized into separate files:
 * - [NanoLayoutComponents] - VStack, HStack, Card, Form, Component, SplitView
 * - [NanoContentComponents] - Text, Image, Badge, Icon, Divider
 * - [NanoInputComponents] - Button, Input, Checkbox, TextArea, Select, DatePicker, Radio, Switch, etc.
 * - [NanoFeedbackComponents] - Modal, Alert, Progress, Spinner
 * - [NanoDataComponents] - DataChart, DataTable
 * - [NanoControlFlowComponents] - Conditional, ForLoop
 * - [cc.unitmesh.xuiper.eval.evaluator.NanoExpressionEvaluator] - Helper functions and utilities
 */
object StatefulNanoRenderer {

    /**
     * Render a NanoIR tree with state management.
     * Automatically initializes state from the IR and provides action handlers.
     *
     * @param ir The NanoIR tree to render
     * @param modifier Modifier for the root component
     * @param themeState Optional theme state for customization
     */
    @Composable
    fun Render(
        ir: NanoIR,
        modifier: Modifier = Modifier,
        themeState: NanoThemeState? = null
    ) {
        when {
            themeState != null -> ProvideNanoTheme(state = themeState) {
                RenderInternal(ir = ir, modifier = modifier)
            }

            // Avoid overriding an already-applied NanoTheme upstream.
            LocalNanoThemeApplied.current -> RenderInternal(ir = ir, modifier = modifier)

            else -> {
                val defaultThemeState = rememberNanoThemeState()
                ProvideNanoTheme(state = defaultThemeState) {
                    RenderInternal(ir = ir, modifier = modifier)
                }
            }
        }
    }

    /**
     * Render a NanoIR tree with a custom registry.
     * Allows platform implementations to provide their own component renderers.
     *
     * @param ir The NanoIR tree to render
     * @param registry Custom component registry
     * @param modifier Modifier for the root component
     * @param themeState Optional theme state for customization
     */
    @Composable
    fun RenderWithRegistry(
        ir: NanoIR,
        registry: NanoNodeRegistry<Modifier, @Composable () -> Unit>,
        modifier: Modifier = Modifier,
        themeState: NanoThemeState? = null
    ) {
        when {
            themeState != null -> ProvideNanoTheme(state = themeState) {
                RenderInternalWithRegistry(ir = ir, registry = registry, modifier = modifier)
            }

            LocalNanoThemeApplied.current -> RenderInternalWithRegistry(ir = ir, registry = registry, modifier = modifier)

            else -> {
                val defaultThemeState = rememberNanoThemeState()
                ProvideNanoTheme(state = defaultThemeState) {
                    RenderInternalWithRegistry(ir = ir, registry = registry, modifier = modifier)
                }
            }
        }
    }

    @Composable
    private fun RenderInternal(
        ir: NanoIR,
        modifier: Modifier = Modifier
    ) {
        // Use the default Compose registry
        val registry = remember { ComposeNanoRegistry.create() }
        RenderInternalWithRegistry(ir, registry, modifier)
    }

    @Composable
    private fun RenderInternalWithRegistry(
        ir: NanoIR,
        registry: NanoNodeRegistry<Modifier, @Composable () -> Unit>,
        modifier: Modifier = Modifier
    ) {
        // Runtime wraps NanoState + action application logic.
        // Recreated when IR changes (e.g. live preview re-parses NanoDSL).
        // NOTE: Compose doesn't allow try/catch around composable invocations, so we catch
        // failures in non-composable initialization here to keep UI alive.
        val sessionResult = remember(ir) { StatefulNanoSession.create(ir) }
        val session = sessionResult.getOrNull()
        val runtimeError = sessionResult.exceptionOrNull()

        if (session == null) {
            println("StatefulNanoRenderer init error: $runtimeError")
            Surface(
                modifier = modifier,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = "Render init failed: ${runtimeError?.message ?: runtimeError?.let { it::class.simpleName }.orEmpty()}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            return
        }

        // Subscribe to declared state keys so Compose recomposes when they change.
        // IMPORTANT: we must *read* the collected State's `.value` so Compose tracks it.
        // Use a stable key order to keep hook ordering deterministic across recompositions.
        val observedKeys = remember(session) { session.observedKeys }
        observedKeys.forEach { key ->
            session.flow(key).collectAsState().value
        }

        // Create dispatcher from registry
        val dispatcher = remember(registry) { StatefulNanoNodeDispatcher(registry) }

        dispatcher.render(session, ir, modifier).invoke()
    }
}

/**
 * Decode image bytes to ImageBitmap.
 * Platform-specific implementation.
 */
internal expect fun decodeImageBytesToBitmap(bytes: ByteArray): ImageBitmap
