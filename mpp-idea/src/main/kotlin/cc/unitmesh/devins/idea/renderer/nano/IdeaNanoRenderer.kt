package cc.unitmesh.devins.idea.renderer.nano

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.dsl.NanoDSL
import cc.unitmesh.xuiper.render.stateful.NanoNodeRegistry
import cc.unitmesh.xuiper.render.stateful.StatefulNanoNodeDispatcher
import cc.unitmesh.xuiper.render.stateful.StatefulNanoSession
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * IntelliJ IDEA implementation of NanoRenderer using Jewel components.
 *
 * This renderer parses NanoDSL source code and renders it to Jewel UI components.
 * It provides a native IntelliJ look and feel for NanoDSL previews.
 *
 * Architecture:
 * - Uses [JewelNanoRegistry] for component type -> renderer mapping
 * - Uses [StatefulNanoNodeDispatcher] for tree traversal with state management
 * - Reuses the entire stateful rendering mechanism from xiuper-ui
 *
 * Supported components:
 * - Layout: VStack, HStack, Card, Form, Component
 * - Content: Text, Badge, Icon, Divider
 * - Input: Button, Checkbox
 * - Selection: Select, Radio, RadioGroup (via [JewelSelectionRenderer])
 * - Feedback: Alert, Progress, Spinner
 */
object IdeaNanoRenderer {

    /**
     * Parse NanoDSL source code and render it.
     */
    @Composable
    fun RenderFromSource(
        source: String,
        modifier: Modifier = Modifier
    ) {
        if (source.isBlank()) {
            RenderEmpty(modifier)
            return
        }

        val ir = remember(source) {
            try {
                NanoDSL.toIR(source)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        if (ir == null) {
            RenderError("Failed to parse NanoDSL", modifier)
            return
        }

        Render(ir, modifier)
    }

    /**
     * Render a NanoIR tree with state management.
     * Uses the registry-based dispatcher for consistent state handling across platforms.
     */
    @Composable
    fun Render(
        ir: NanoIR,
        modifier: Modifier = Modifier
    ) {
        // Use the default Jewel registry
        val registry = remember { JewelNanoRegistry.create() }
        RenderWithRegistry(ir, registry, modifier)
    }

    /**
     * Render a NanoIR tree with a custom registry.
     * Allows extending or customizing the component renderers.
     *
     * @param ir The NanoIR tree to render
     * @param registry Custom component registry
     * @param modifier Modifier for the root component
     */
    @Composable
    fun RenderWithRegistry(
        ir: NanoIR,
        registry: NanoNodeRegistry<Modifier, @Composable () -> Unit>,
        modifier: Modifier = Modifier
    ) {
        val sessionResult = remember(ir) { StatefulNanoSession.create(ir) }
        val session = sessionResult.getOrNull()
        val runtimeError = sessionResult.exceptionOrNull()

        if (session == null) {
            RenderError("Init failed: ${runtimeError?.message ?: "Unknown error"}", modifier)
            return
        }

        // Subscribe to declared state keys for recomposition
        val observedKeys = remember(session) { session.observedKeys }
        observedKeys.forEach { key ->
            session.flow(key).collectAsState().value
        }

        // Create dispatcher from registry
        val dispatcher = remember(registry) { StatefulNanoNodeDispatcher(registry) }

        dispatcher.render(session, ir, modifier).invoke()
    }

    // ==================== Utility ====================

    @Composable
    private fun RenderEmpty(modifier: Modifier) {
        Box(
            modifier = modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No NanoDSL content",
                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
            )
        }
    }

    @Composable
    private fun RenderError(message: String, modifier: Modifier) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE57373).copy(alpha = 0.1f))
                .padding(16.dp)
        ) {
            Text(
                text = message,
                color = JewelTheme.globalColors.text.normal
            )
        }
    }
}

