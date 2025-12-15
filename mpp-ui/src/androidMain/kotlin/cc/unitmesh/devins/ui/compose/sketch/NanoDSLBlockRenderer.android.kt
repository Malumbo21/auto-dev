package cc.unitmesh.devins.ui.compose.sketch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors
import cc.unitmesh.devins.ui.nano.StatefulNanoRenderer
import cc.unitmesh.xuiper.dsl.NanoDSL
import cc.unitmesh.xuiper.ir.NanoIR
import androidx.compose.material3.Surface as M3Surface

/**
 * Android implementation of NanoDSLBlockRenderer with live UI preview.
 *
 * Features:
 * - Parses NanoDSL source code using xiuper-ui's NanoDSL parser
 * - Renders live UI preview using StatefulNanoRenderer
 * - Toggle between preview and source code view
 * - Shows parse errors with details
 */
@Composable
actual fun NanoDSLBlockRenderer(
    nanodslCode: String,
    isComplete: Boolean,
    modifier: Modifier
) {
    var showPreview by remember { mutableStateOf(true) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var nanoIR by remember { mutableStateOf<NanoIR?>(null) }

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
                    AutoDevColors.Signal.error.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            )
            .background(AutoDevColors.Void.surface1)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AutoDevColors.Void.bg.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "NanoDSL",
                    style = MaterialTheme.typography.labelMedium,
                    color = AutoDevColors.Text.secondary
                )

                if (parseError != null) {
                    Spacer(Modifier.width(8.dp))
                    M3Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = AutoDevColors.Signal.error.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Parse Error",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = AutoDevColors.Signal.error
                        )
                    }
                } else if (nanoIR != null) {
                    Spacer(Modifier.width(8.dp))
                    M3Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = AutoDevColors.Signal.success.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Valid",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = AutoDevColors.Signal.success
                        )
                    }
                } else if (!isComplete) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = AutoDevColors.Text.tertiary
                    )
                }
            }

            // Toggle button (only show if we have valid IR or parse error)
            if (nanoIR != null || parseError != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AutoDevColors.Void.bg)
                        .clickable { showPreview = !showPreview }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showPreview && nanoIR != null) "</>" else "Preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = AutoDevColors.Text.secondary
                    )
                }
            }
        }

        // Content
        if (showPreview && nanoIR != null) {
            // Live UI Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                StatefulNanoRenderer.Render(nanoIR!!)
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
                color = AutoDevColors.Signal.error.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "Error: $parseError",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = AutoDevColors.Signal.error
                )
            }
        }
    }
}

