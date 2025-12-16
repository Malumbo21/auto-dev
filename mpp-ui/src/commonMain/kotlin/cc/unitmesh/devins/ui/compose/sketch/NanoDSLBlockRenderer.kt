package cc.unitmesh.devins.ui.compose.sketch

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cc.unitmesh.agent.Platform
import cc.unitmesh.devins.ui.nano.StatefulNanoRenderer
import cc.unitmesh.xuiper.dsl.NanoDSL
import cc.unitmesh.xuiper.ir.NanoIR
import androidx.compose.material3.Surface as M3Surface

/**
 * NanoDSL Block Renderer - Cross-platform component for rendering NanoDSL code blocks.
 *
 * Features:
 * - Parses NanoDSL source code using xiuper-ui's NanoDSL parser (now multiplatform!)
 * - Renders live UI preview using StatefulNanoRenderer
 * - Toggle between preview and source code view
 * - Shows parse errors with details
 * - Gracefully falls back to code display if parsing fails
 *
 * Platform support:
 * - JVM, Android, iOS, WasmJS: Full NanoDSL parsing and live preview
 * - JS (Node.js): Code display only (parser throws UnsupportedOperationException)
 *
 * Usage in SketchRenderer:
 * ```kotlin
 * "nanodsl", "nano" -> {
 *     NanoDSLBlockRenderer(
 *         nanodslCode = fence.text,
 *         isComplete = blockIsComplete,
 *         modifier = Modifier.fillMaxWidth()
 *     )
 * }
 * ```
 *
 * @param nanodslCode The NanoDSL source code to render
 * @param isComplete Whether the code block streaming is complete
 * @param modifier Compose modifier for the component
 */
@Composable
fun NanoDSLBlockRenderer(
    nanodslCode: String,
    isComplete: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showPreview by remember { mutableStateOf(true) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var nanoIR by remember { mutableStateOf<NanoIR?>(null) }
    var isDarkTheme by remember { mutableStateOf(true) }
    var isMobileLayout by remember { mutableStateOf(false) }

    // Parse NanoDSL to IR when code changes
    LaunchedEffect(nanodslCode, isComplete) {
        if (isComplete && nanodslCode.isNotBlank()) {
            try {
                val ir = NanoDSL.toIR(nanodslCode)
                nanoIR = ir
                parseError = null
            } catch (e: Exception) {
                parseError = e.message ?: "Unknown parse error"
                nanoIR = null
            }
        } else if (!isComplete) {
            // Reset during streaming
            parseError = null
            nanoIR = null
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = if (parseError != null)
                    MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            )
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "NanoDSL",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (parseError != null) {
                    Spacer(Modifier.width(8.dp))
                    M3Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = "Parse Error",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else if (nanoIR != null) {
                    Spacer(Modifier.width(8.dp))
                    M3Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = "Valid",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                } else if (!isComplete) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Toggle buttons (only show if we have valid IR or parse error)
            if (nanoIR != null || parseError != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Theme toggle button
                    NanoDSLToggleButton(
                        text = if (isDarkTheme) "🌙" else "☀️",
                        isActive = isDarkTheme,
                        onClick = { isDarkTheme = !isDarkTheme }
                    )

                    // Layout toggle button
                    NanoDSLToggleButton(
                        text = if (isMobileLayout) "📱" else "🖥️",
                        isActive = isMobileLayout,
                        onClick = { isMobileLayout = !isMobileLayout }
                    )

                    // Preview/Code toggle button
                    NanoDSLToggleButton(
                        text = if (showPreview && nanoIR != null) "</>" else "Preview",
                        isActive = showPreview && nanoIR != null,
                        onClick = { showPreview = !showPreview }
                    )
                }
            }
        }

        // Content
        if (showPreview && nanoIR != null) {
            // Live UI Preview with theme and layout control
            val previewModifier = if (isMobileLayout) {
                Modifier
                    .width(400.dp)  // Fixed mobile width
                    .padding(16.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            }

            val backgroundColor = if (isDarkTheme) {
                Color(0xFF1E1E1E)  // Dark background
            } else {
                Color(0xFFF5F5F5)  // Light background
            }

            Box(
                modifier = previewModifier
                    .background(
                        color = backgroundColor,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isDarkTheme) {
                            Color(0xFF3C3C3C)
                        } else {
                            Color(0xFFE0E0E0)
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(16.dp)
            ) {
                // Apply theme to the content
                CompositionLocalProvider(
                    LocalContentColor provides if (isDarkTheme) Color.White else Color.Black
                ) {
                    StatefulNanoRenderer.Render(nanoIR!!)
                }
            }
        } else {
            // Source code view
            CodeBlockRenderer(
                code = nanodslCode,
                language = "nanodsl",
                displayName = "NanoDSL"
            )
        }

        // Show parse error details
        if (parseError != null && !showPreview) {
            M3Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "Error: $parseError",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * NanoDSL Toggle Button - Theme-aware toggle button component
 *
 * Features:
 * - Theme-aware colors using MaterialTheme.colorScheme
 * - Smooth animations for state changes
 * - Clear visual feedback for active/inactive states
 *
 * @param text Button text or emoji
 * @param isActive Whether the button is in active state
 * @param onClick Callback when button is clicked
 */
@Composable
private fun NanoDSLToggleButton(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Theme-aware colors with animation
    val backgroundColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    )

    val contentColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isActive) 1.dp else 0.dp
    )

    val borderColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.0f)
        }
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(4.dp)
            )
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

