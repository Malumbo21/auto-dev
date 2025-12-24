package cc.unitmesh.devins.idea.components.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.idea.toolwindow.IdeaComposeIcons
import cc.unitmesh.devins.idea.theme.IdeaAutoDevColors
import com.intellij.openapi.ide.CopyPasteManager
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import java.awt.datatransfer.StringSelection

/**
 * Error bubble for displaying error messages.
 * Uses IdeaAutoDevColors.design system for consistent error styling.
 */
@Composable
fun IdeaErrorBubble(
    message: String,
    modifier: Modifier = Modifier
) {

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .background(IdeaAutoDevColors.Red.c400.copy(alpha = 0.2f))
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = IdeaComposeIcons.Error,
                        contentDescription = "Error",
                        modifier = Modifier.size(16.dp),
                        tint = IdeaAutoDevColors.Red.c400
                    )
                    Text(
                        text = message,
                        style = JewelTheme.defaultTextStyle.copy(
                            color = IdeaAutoDevColors.Red.c400
                        )
                    )
                }

                // Copy button
                IconButton(
                    onClick = {
                        try {
                            CopyPasteManager.getInstance().setContents(StringSelection(message))
                        } catch (e: Exception) {
                            // Ignore clipboard errors
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = IdeaComposeIcons.ContentCopy,
                        contentDescription = "Copy error",
                        modifier = Modifier.size(16.dp),
                        tint = IdeaAutoDevColors.Red.c400
                    )
                }
            }
        }
    }
}

