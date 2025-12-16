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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cc.unitmesh.agent.parser.NanoDSLValidator
import cc.unitmesh.agent.parser.NanoDSLParseResult
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.DefaultButton

/**
 * IntelliJ IDEA implementation of NanoDSLBlockRenderer with validation.
 *
 * Features:
 * - Validates NanoDSL source code using mpp-core's NanoDSLValidator
 * - Shows validation status and errors
 * - Displays formatted source code
 * - Uses Jewel theme colors for native IntelliJ look and feel
 *
 * Note: This version shows validation only. Full UI preview rendering requires
 * mpp-ui dependency which is not available in mpp-idea to avoid ClassLoader conflicts.
 * For full preview, use the Desktop app or web viewer.
 */
@Composable
fun IdeaNanoDSLBlockRenderer(
    nanodslCode: String,
    isComplete: Boolean = true,
    modifier: Modifier = Modifier
) {
    var parseError by remember { mutableStateOf<String?>(null) }
    var isValid by remember { mutableStateOf(false) }

    // Validate NanoDSL when code changes
    LaunchedEffect(nanodslCode, isComplete) {
        if (isComplete && nanodslCode.isNotBlank()) {
            try {
                val validator = NanoDSLValidator()
                val result = validator.parse(nanodslCode)
                when (result) {
                    is NanoDSLParseResult.Success -> {
                        isValid = true
                        parseError = null
                    }
                    is NanoDSLParseResult.Failure -> {
                        isValid = false
                        parseError = result.errors.joinToString("\n") { error ->
                            "Line ${error.line}: ${error.message}" +
                                (error.suggestion?.let { "\n  💡 $it" } ?: "")
                        }
                    }
                }
            } catch (e: Exception) {
                isValid = false
                parseError = e.message ?: "Unknown validation error"
            }
        } else if (!isComplete) {
            // Reset during streaming
            parseError = null
            isValid = false
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = when {
                    parseError != null -> Color(0xFFE57373) // Red tint for errors
                    isValid -> Color(0xFF81C784) // Green tint for valid
                    else -> JewelTheme.globalColors.borders.normal.copy(alpha = 0.2f)
                },
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
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE57373).copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "❌ Invalid",
                            color = JewelTheme.globalColors.text.normal
                        )
                    }
                } else if (isValid) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF81C784).copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "✓ Valid",
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
        }

        // Source code view
        IdeaCodeBlockRenderer(
            code = nanodslCode,
            language = "nanodsl",
            modifier = Modifier.fillMaxWidth()
        )

        // Show validation error details
        if (parseError != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE57373).copy(alpha = 0.1f))
                    .border(
                        width = 1.dp,
                        color = Color(0xFFE57373).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Validation Errors:",
                        color = JewelTheme.globalColors.text.normal
                    )
                    Text(
                        text = parseError!!,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Show success message for valid code
        if (isValid && parseError == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF81C784).copy(alpha = 0.1f))
                    .border(
                        width = 1.dp,
                        color = Color(0xFF81C784).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = "✓ NanoDSL code is valid. Use Desktop app or web viewer for full UI preview.",
                    color = JewelTheme.globalColors.text.normal
                )
            }
        }
    }
}

