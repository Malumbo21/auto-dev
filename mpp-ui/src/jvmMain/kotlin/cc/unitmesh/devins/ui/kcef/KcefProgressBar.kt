package cc.unitmesh.devins.ui.kcef

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * KCEF Download Progress Bar
 *
 * Shows download progress at the bottom of the window when KCEF is being downloaded.
 * User can dismiss the notification but download continues in background.
 */
@Composable
fun KcefProgressBar(
    initState: KcefInitState,
    downloadProgress: Float,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    var dismissed by remember { mutableStateOf(false) }

    // Reset dismissed state when state changes to initializing
    LaunchedEffect(initState) {
        if (initState is KcefInitState.Initializing) {
            dismissed = false
        }
    }

    // Only show when initializing and not dismissed
    val shouldShow = !dismissed && when (initState) {
        is KcefInitState.Initializing -> true
        is KcefInitState.Error -> true
        else -> false
    }

    AnimatedVisibility(
        visible = shouldShow,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Icon - smaller
                when (initState) {
                    is KcefInitState.Initializing -> {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Downloading",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    is KcefInitState.Error -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    else -> {}
                }

                // Message and Progress - more compact
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (initState) {
                        is KcefInitState.Initializing -> {
                            Text(
                                text = "正在下载 WebView (${downloadProgress.toInt()}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.width(120.dp).height(3.dp),
                            )

                            Text(
                                text = "后台进行",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        is KcefInitState.Error -> {
                            Text(
                                text = "WebView 下载失败: ${initState.exception.message ?: "未知错误"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        else -> {}
                    }
                }

                // Dismiss button - smaller
                IconButton(
                    onClick = {
                        dismissed = true
                        onDismiss()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

