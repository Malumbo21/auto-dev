package cc.unitmesh.devins.ui.compose.agent.codereview.pr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.agent.review.PRComment
import cc.unitmesh.agent.review.PRCommentThread
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors

/**
 * Compact inline chip showing PR comment count for a line
 * Clicking expands to show the full thread
 */
@Composable
fun InlinePRCommentChip(
    thread: PRCommentThread,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val commentCount = thread.comments.size
    val isResolved = thread.isResolved

    Surface(
        color = when {
            isResolved -> AutoDevColors.Neutral.c600.copy(alpha = 0.15f)
            else -> AutoDevColors.Indigo.c600.copy(alpha = 0.15f)
        },
        shape = RoundedCornerShape(4.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isResolved) AutoDevComposeIcons.CheckCircle else AutoDevComposeIcons.Chat,
                contentDescription = if (isResolved) "Resolved" else "Comment",
                tint = if (isResolved) AutoDevColors.Neutral.c600 else AutoDevColors.Indigo.c600,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = if (commentCount > 1) "$commentCount" else "",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isResolved) AutoDevColors.Neutral.c600 else AutoDevColors.Indigo.c600,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Expanded view of a PR comment thread
 * Shows all comments in the thread with author info and timestamps
 */
@Composable
fun PRCommentThreadView(
    thread: PRCommentThread,
    modifier: Modifier = Modifier,
    onReply: ((String) -> Unit)? = null,
    onResolve: (() -> Unit)? = null,
    onUnresolve: (() -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Thread header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AutoDevComposeIcons.Chat,
                        contentDescription = "Comments",
                        tint = AutoDevColors.Indigo.c500,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${thread.comments.size} comment${if (thread.comments.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (thread.isResolved) {
                        Surface(
                            color = AutoDevColors.Green.c600.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Resolved",
                                style = MaterialTheme.typography.labelSmall,
                                color = AutoDevColors.Green.c600,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (thread.isOutdated) {
                        Surface(
                            color = AutoDevColors.Amber.c600.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Outdated",
                                style = MaterialTheme.typography.labelSmall,
                                color = AutoDevColors.Amber.c600,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Expand/collapse and actions
                Row {
                    if (onResolve != null && !thread.isResolved) {
                        IconButton(
                            onClick = onResolve,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = AutoDevComposeIcons.CheckCircle,
                                contentDescription = "Resolve",
                                tint = AutoDevColors.Green.c500,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (onUnresolve != null && thread.isResolved) {
                        IconButton(
                            onClick = onUnresolve,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = AutoDevComposeIcons.Refresh,
                                contentDescription = "Unresolve",
                                tint = AutoDevColors.Amber.c500,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) AutoDevComposeIcons.ExpandLess else AutoDevComposeIcons.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Comments list
            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))

                thread.comments.forEachIndexed { index, comment ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                    PRCommentView(comment = comment)
                }
            }
        }
    }
}

/**
 * Single PR comment view
 */
@Composable
fun PRCommentView(
    comment: PRComment,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Author and timestamp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar placeholder
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(AutoDevColors.Indigo.c500.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = comment.author.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AutoDevColors.Indigo.c600,
                        fontSize = 10.sp
                    )
                }
                Text(
                    text = comment.author,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = formatTimestamp(comment.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Comment body
        Text(
            text = comment.body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 18.sp
        )
    }
}

/**
 * Format timestamp for display
 */
private fun formatTimestamp(timestamp: String): String {
    // Simple formatting - just show date part
    return timestamp.substringBefore("T")
}

/**
 * Inline PR comment indicator shown next to diff lines
 * Shows a small chip that expands to full thread on click
 */
@Composable
fun InlinePRCommentIndicator(
    threads: List<PRCommentThread>,
    modifier: Modifier = Modifier,
    onThreadClick: ((PRCommentThread) -> Unit)? = null
) {
    if (threads.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        threads.forEach { thread ->
            InlinePRCommentChip(
                thread = thread,
                onClick = { onThreadClick?.invoke(thread) }
            )
        }
    }
}

