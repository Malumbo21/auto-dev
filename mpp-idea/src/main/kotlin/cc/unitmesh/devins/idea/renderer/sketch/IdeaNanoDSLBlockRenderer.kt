package cc.unitmesh.devins.idea.renderer.sketch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.nano.StatefulNanoRenderer
import cc.unitmesh.xuiper.dsl.NanoDSL
import cc.unitmesh.xuiper.ir.NanoIR
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import androidx.compose.material3.Surface as M3Surface

/**
 * IntelliJ IDEA implementation of NanoDSLBlockRenderer with live UI preview.
 *
 * Features:
 * - Parses NanoDSL source code using xiuper-ui's NanoDSL parser
 * - Renders live UI preview using StatefulNanoRenderer
 * - Toggle between preview and source code view
 * - Shows parse errors with details
 * - Uses Jewel theme colors for native IntelliJ look and feel
 */
@Composable
fun IdeaNanoDSLBlockRenderer(
    nanodslCode: String,
    isComplete: Boolean = true,
    modifier: Modifier = Modifier
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
                    JewelTheme.globalColors.borders.normal.copy(alpha = 0.5f)
                else
                    JewelTheme.globalColors.borders.normal.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            )
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "NanoDSL",
                    color = JewelTheme.globalColors.text.normal
                )

                if (parseError != null) {
                    Spacer(Modifier.width(8.dp))
                    M3Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = JewelTheme.globalColors.borders.normal.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Parse Error",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = JewelTheme.globalColors.text.normal
                        )
                    }
                } else if (nanoIR != null) {
                    Spacer(Modifier.width(8.dp))
                    M3Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = JewelTheme.globalColors.borders.normal.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Valid",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = JewelTheme.globalColors.text.normal
                        )
                    }
                } else if (!isComplete) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Toggle button (only show if we have valid IR or parse error)
            if (nanoIR != null || parseError != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(JewelTheme.globalColors.panelBackground)
                        .clickable { showPreview = !showPreview }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showPreview && nanoIR != null) "</>" else "Preview",
                        color = JewelTheme.globalColors.text.normal
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
            IdeaCodeBlockRenderer(
                code = nanodslCode,
                language = "nanodsl",
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Show parse error details
        if (parseError != null && !showPreview) {
            M3Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                color = JewelTheme.globalColors.borders.normal.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "Error: $parseError",
                    modifier = Modifier.padding(8.dp),
                    color = JewelTheme.globalColors.text.normal
                )
            }
        }
    }
}

