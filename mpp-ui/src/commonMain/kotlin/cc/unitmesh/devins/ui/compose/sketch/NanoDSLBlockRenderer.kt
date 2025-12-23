package cc.unitmesh.devins.ui.compose.sketch

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons
import cc.unitmesh.devins.ui.nano.StatefulNanoRenderer
import cc.unitmesh.devins.ui.platform.createFileChooser
import cc.unitmesh.xuiper.dsl.NanoDSL
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.render.theme.NanoThemeFamily
import cc.unitmesh.devins.ui.nano.theme.ProvideNanoTheme
import cc.unitmesh.devins.ui.nano.theme.parseHexColorOrNull
import cc.unitmesh.devins.ui.nano.theme.rememberNanoThemeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    var isMobileLayout by remember { mutableStateOf(false) }
    var zoomLevel by remember { mutableStateOf(0.75f) }
    var isCapturingScreenshot by remember { mutableStateOf(false) }

    // GraphicsLayer for screenshot capture
    val graphicsLayer = rememberGraphicsLayer()

    val nanoThemeState = rememberNanoThemeState(
        family = NanoThemeFamily.BANK_BLACK_GOLD,
        dark = true,
        customSeedHex = "#FF385C"
    )

    // Apply Nano theme to the whole block (header + preview + borders).
    // This ensures theme switching affects background and not only icons.
    ProvideNanoTheme(state = nanoThemeState) {

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
                    val clipboardManager = LocalClipboardManager.current
                    var showCopied by remember { mutableStateOf(false) }

                    // Auto-hide "Copied!" message after 2 seconds
                    LaunchedEffect(showCopied) {
                        if (showCopied) {
                            kotlinx.coroutines.delay(2000)
                            showCopied = false
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Zoom out button
                        NanoDSLIconButton(
                            icon = AutoDevComposeIcons.ZoomOut,
                            contentDescription = "Zoom Out",
                            isActive = zoomLevel > 0.5f,
                            onClick = { if (zoomLevel > 0.5f) zoomLevel -= 0.1f }
                        )

                        // Zoom in button
                        NanoDSLIconButton(
                            icon = AutoDevComposeIcons.ZoomIn,
                            contentDescription = "Zoom In",
                            isActive = zoomLevel < 2.0f,
                            onClick = { if (zoomLevel < 2.0f) zoomLevel += 0.1f }
                        )

                        Spacer(Modifier.width(4.dp))

                        // Theme mode toggle button (applies real Nano MaterialTheme)
                        NanoDSLIconButton(
                            icon = if (nanoThemeState.dark) AutoDevComposeIcons.DarkMode else AutoDevComposeIcons.LightMode,
                            contentDescription = if (nanoThemeState.dark) "Dark Mode" else "Light Mode",
                            isActive = nanoThemeState.dark,
                            onClick = { nanoThemeState.dark = !nanoThemeState.dark }
                        )

                        // Theme family selector
                        Box {
                            var themeMenuExpanded by remember { mutableStateOf(false) }

                            NanoDSLIconButton(
                                icon = AutoDevComposeIcons.Palette,
                                contentDescription = "Theme Style",
                                isActive = nanoThemeState.family != NanoThemeFamily.BANK_BLACK_GOLD,
                                onClick = { themeMenuExpanded = true }
                            )

                            DropdownMenu(
                                expanded = themeMenuExpanded,
                                onDismissRequest = { themeMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Bank (Black/Gold)") },
                                    onClick = {
                                        nanoThemeState.family = NanoThemeFamily.BANK_BLACK_GOLD
                                        // Keep previous dark choice for bank style; don't force.
                                        themeMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Travel (Airbnb)") },
                                    onClick = {
                                        nanoThemeState.family = NanoThemeFamily.TRAVEL_AIRBNB
                                        // Airbnb style is expected to be light by default.
                                        nanoThemeState.dark = false
                                        themeMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Custom (Seed Color)") },
                                    onClick = {
                                        nanoThemeState.family = NanoThemeFamily.CUSTOM
                                        themeMenuExpanded = false
                                    }
                                )
                            }
                        }

                        if (nanoThemeState.family == NanoThemeFamily.CUSTOM) {
                            val seedColor = parseHexColorOrNull(nanoThemeState.customSeedHex)

                            // Preset swatches
                            Box {
                                var swatchMenuExpanded by remember { mutableStateOf(false) }
                                val swatchColor = seedColor ?: MaterialTheme.colorScheme.primary

                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable { swatchMenuExpanded = true }
                                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                                        .background(swatchColor)
                                )

                                DropdownMenu(
                                    expanded = swatchMenuExpanded,
                                    onDismissRequest = { swatchMenuExpanded = false }
                                ) {
                                    val presets = listOf(
                                        "#FF385C",
                                        "#FFD166",
                                        "#00A699",
                                        "#6366F1",
                                        "#00BCD4",
                                        "#22C55E",
                                        "#A855F7",
                                        "#EF4444"
                                    )
                                    presets.forEach { hex ->
                                        DropdownMenuItem(
                                            text = { Text(hex) },
                                            onClick = {
                                                nanoThemeState.customSeedHex = hex
                                                swatchMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = nanoThemeState.customSeedHex,
                                onValueChange = { nanoThemeState.customSeedHex = it.take(10) },
                                modifier = Modifier.width(140.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.labelSmall,
                                placeholder = { Text("#RRGGBB") }
                            )
                        }

                        // Layout toggle button
                        NanoDSLIconButton(
                            icon = if (isMobileLayout) AutoDevComposeIcons.PhoneAndroid else AutoDevComposeIcons.Computer,
                            contentDescription = if (isMobileLayout) "Mobile Layout" else "Desktop Layout",
                            isActive = isMobileLayout,
                            onClick = { isMobileLayout = !isMobileLayout }
                        )

                        // Preview/Code toggle button
                        NanoDSLIconButton(
                            icon = AutoDevComposeIcons.Code,
                            contentDescription = if (showPreview && nanoIR != null) "Show Code" else "Show Preview",
                            isActive = showPreview && nanoIR != null,
                            onClick = { showPreview = !showPreview }
                        )

                        // Copy button
                        NanoDSLIconButton(
                            icon = if (showCopied) AutoDevComposeIcons.Check else AutoDevComposeIcons.ContentCopy,
                            contentDescription = if (showCopied) "Copied" else "Copy",
                            isActive = showCopied,
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(nanodslCode))
                                showCopied = true
                            }
                        )

                        // Screenshot button (only show when preview is visible)
                        if (showPreview && nanoIR != null) {
                            NanoDSLIconButton(
                                icon = AutoDevComposeIcons.Screenshot,
                                contentDescription = "Screenshot",
                                isActive = isCapturingScreenshot,
                                onClick = {
                                    isCapturingScreenshot = true
                                    CoroutineScope(Dispatchers.Default).launch {
                                        try {
                                            val imageBitmap = graphicsLayer.toImageBitmap()
                                            val pngBytes = encodeImageBitmapToPng(imageBitmap)
                                            if (pngBytes != null) {
                                                val fileChooser = createFileChooser()
                                                val timestamp =
                                                    kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                                                fileChooser.saveFile(
                                                    title = "Save NanoDSL Screenshot",
                                                    defaultFileName = "nanodsl-screenshot-$timestamp.png",
                                                    fileExtension = "png",
                                                    data = pngBytes
                                                )
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        } finally {
                                            isCapturingScreenshot = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Content
            if (showPreview && nanoIR != null) {
                // Live UI Preview with theme and layout control
                val previewModifier = if (isMobileLayout) {
                    Modifier
                        .width(600.dp)  // Fixed mobile width
                        .padding(16.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                }
                val backgroundColor = MaterialTheme.colorScheme.surface
                val borderColor = MaterialTheme.colorScheme.outline

                Box(
                    modifier = previewModifier
                        .background(
                            color = backgroundColor,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(16.dp)
                        // Draw content to GraphicsLayer for screenshot capture
                        .drawWithContent {
                            graphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(graphicsLayer)
                        }
                ) {
                    // Use Layout to properly scale content including layout size
                    ScaledBox(scale = zoomLevel) {
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

/**
 * NanoDSL Icon Button - Theme-aware icon button component
 *
 * Features:
 * - Uses Material icons instead of emoji for better cross-platform support
 * - Theme-aware colors using MaterialTheme.colorScheme
 * - Smooth animations for state changes
 * - Clear visual feedback for active/inactive states
 *
 * @param icon The ImageVector icon to display
 * @param contentDescription Accessibility description for the icon
 * @param isActive Whether the button is in active state
 * @param onClick Callback when button is clicked
 */
@Composable
private fun NanoDSLIconButton(
    icon: ImageVector,
    contentDescription: String,
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

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(4.dp)
            )
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = contentColor
        )
    }
}


/**
 * A Box that scales its content and adjusts its layout size accordingly.
 * Unlike Modifier.scale() which only visually scales content without changing layout,
 * this composable properly scales both the visual appearance and the layout bounds.
 *
 * @param scale The scale factor (e.g., 0.75f for 75% size)
 * @param modifier Modifier for the container
 * @param content The content to scale
 */
@Composable
private fun ScaledBox(
    scale: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        // Measure children with scaled-up constraints so they have room to render
        val scaledConstraints = constraints.copy(
            maxWidth = if (constraints.maxWidth == Int.MAX_VALUE) Int.MAX_VALUE
            else (constraints.maxWidth / scale).toInt(),
            maxHeight = if (constraints.maxHeight == Int.MAX_VALUE) Int.MAX_VALUE
            else (constraints.maxHeight / scale).toInt()
        )

        val placeables = measurables.map { it.measure(scaledConstraints) }

        // Calculate the scaled layout size
        val width = (placeables.maxOfOrNull { it.width } ?: 0)
        val height = (placeables.maxOfOrNull { it.height } ?: 0)
        val scaledWidth = (width * scale).toInt()
        val scaledHeight = (height * scale).toInt()

        layout(scaledWidth, scaledHeight) {
            placeables.forEach { placeable ->
                // Place with scale transform from top-left origin
                placeable.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}
