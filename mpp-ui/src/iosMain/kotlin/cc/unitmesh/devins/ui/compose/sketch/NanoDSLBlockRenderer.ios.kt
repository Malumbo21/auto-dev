package cc.unitmesh.devins.ui.compose.sketch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors

/**
 * iOS implementation of NanoDSLBlockRenderer.
 *
 * Displays NanoDSL code with syntax highlighting.
 * Live preview is not available on iOS platform as xiuper-ui (full parser) is JVM-only.
 */
@Composable
actual fun NanoDSLBlockRenderer(
    nanodslCode: String,
    isComplete: Boolean,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
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
            Text(
                text = "NanoDSL",
                style = MaterialTheme.typography.labelMedium,
                color = AutoDevColors.Text.secondary
            )

            if (!isComplete) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = AutoDevColors.Text.tertiary
                )
            }
        }

        // Source code view
        CodeBlockRenderer(
            code = nanodslCode,
            language = "nanodsl",
            displayName = "NanoDSL"
        )
    }
}

