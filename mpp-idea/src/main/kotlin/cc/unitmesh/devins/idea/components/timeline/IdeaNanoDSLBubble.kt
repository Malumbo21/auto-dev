package cc.unitmesh.devins.idea.components.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.agent.render.TimelineItem
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors
import com.intellij.openapi.project.Project
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * NanoDSL timeline bubble - displays generated NanoDSL code with optional preview.
 * Uses Jewel theming for IntelliJ look and feel.
 */
@Composable
fun IdeaNanoDSLBubble(
    item: TimelineItem.NanoDSLItem,
    project: Project? = null,
    modifier: Modifier = Modifier
) {
    var showPreview by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Bubble container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AutoDevColors.Energy.aiDim)
                .padding(12.dp)
        ) {
            Column {
                // Header row with component name and status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎨",
                            style = JewelTheme.defaultTextStyle.copy(fontSize = 16.sp)
                        )
                        Text(
                            text = item.componentName ?: "Generated UI",
                            style = JewelTheme.defaultTextStyle.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        )
                        if (item.generationAttempts > 1) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        AutoDevColors.Amber.c400.copy(alpha = 0.2f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${item.generationAttempts} attempts",
                                    style = JewelTheme.defaultTextStyle.copy(
                                        fontSize = 10.sp,
                                        color = AutoDevColors.Amber.c600
                                    )
                                )
                            }
                        }
                    }
                    
                    // Validity indicator
                    Text(
                        text = if (item.isValid) "✅ Valid" else "⚠️ Invalid",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            color = if (item.isValid) AutoDevColors.Green.c400 
                                    else AutoDevColors.Red.c400
                        )
                    )
                }
                
                // Warnings
                if (item.warnings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    item.warnings.forEach { warning ->
                        Text(
                            text = "⚠ $warning",
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 11.sp,
                                color = AutoDevColors.Red.c400.copy(alpha = 0.8f)
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Code display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(JewelTheme.globalColors.panelBackground)
                        .padding(8.dp)
                ) {
                    Column {
                        val lines = item.source.lines()
                        val maxLineNumWidth = lines.size.toString().length
                        
                        lines.forEachIndexed { index, line ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = (index + 1).toString().padStart(maxLineNumWidth, ' '),
                                    style = JewelTheme.defaultTextStyle.copy(
                                        color = JewelTheme.globalColors.text.info.copy(alpha = 0.4f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = line,
                                    style = JewelTheme.defaultTextStyle.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }
                
                // Footer with line count
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${item.source.lines().size} lines of NanoDSL code",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 10.sp,
                        color = JewelTheme.globalColors.text.info.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}

