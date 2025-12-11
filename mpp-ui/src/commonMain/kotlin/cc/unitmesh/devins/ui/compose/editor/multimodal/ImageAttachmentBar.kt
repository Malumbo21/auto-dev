package cc.unitmesh.devins.ui.compose.editor.multimodal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons

/**
 * Bar showing attached images with thumbnails and remove buttons.
 * Clicking on an image shows the preview dialog.
 */
@Composable
fun ImageAttachmentBar(
    images: List<AttachedImage>,
    onRemoveImage: (AttachedImage) -> Unit,
    onImageClick: (AttachedImage) -> Unit,
    onRetryUpload: ((AttachedImage) -> Unit)? = null,
    isAnalyzing: Boolean = false,
    isUploading: Boolean = false,
    uploadedCount: Int = 0,
    analysisProgress: String? = null,
    visionModel: String,
    modifier: Modifier = Modifier
) {
    if (images.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Vision model indicator and upload status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = AutoDevComposeIcons.Vision,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Vision: $visionModel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Upload status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Uploading...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else if (uploadedCount == images.size && images.isNotEmpty()) {
                    Icon(
                        imageVector = AutoDevComposeIcons.CloudDone,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Ready",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "$uploadedCount/${images.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Image thumbnails
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(images, key = { it.id }) { image ->
                ImageThumbnail(
                    image = image,
                    onRemove = { onRemoveImage(image) },
                    onClick = { onImageClick(image) },
                    onRetry = if (image.isFailed && onRetryUpload != null) {
                        { onRetryUpload(image) }
                    } else null,
                    isAnalyzing = isAnalyzing
                )
            }
        }

        // Analysis progress indicator
        if (isAnalyzing && analysisProgress != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = analysisProgress,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Single image thumbnail with remove button.
 */
@Composable
private fun ImageThumbnail(
    image: AttachedImage,
    onRemove: () -> Unit,
    onClick: () -> Unit,
    onRetry: (() -> Unit)? = null,
    isAnalyzing: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        image.isFailed -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        image.isUploaded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        image.isUploading -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Box(
        modifier = modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(enabled = !isAnalyzing && !image.isUploading) { onClick() }
    ) {
        // Background - different for uploaded vs local
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (image.isUploaded) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(4.dp)
            ) {
                // Show upload progress or image icon
                when {
                    image.isUploading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    image.isFailed -> {
                        Icon(
                            imageVector = AutoDevComposeIcons.Error,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    image.isUploaded -> {
                        // Show cloud icon for uploaded images
                        Icon(
                            imageVector = AutoDevComposeIcons.CloudDone,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = AutoDevComposeIcons.Image,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Show file name (truncated)
                Text(
                    text = when {
                        image.uploadStatus == ImageUploadStatus.COMPRESSING -> "Compressing..."
                        image.uploadStatus == ImageUploadStatus.UPLOADING -> "Uploading..."
                        image.isFailed -> "Failed"
                        else -> image.name.take(12) + if (image.name.length > 12) "…" else ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        image.isFailed -> MaterialTheme.colorScheme.error
                        image.isUploaded -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Show size info - use compressedSize if available, otherwise originalSize
                if (!image.isUploading && !image.isFailed) {
                    val sizeText = when {
                        image.compressedSize != null && image.compressedSize!! > 0 -> {
                            formatSize(image.compressedSize!!)
                        }
                        image.originalSize > 0 -> {
                            formatSize(image.originalSize)
                        }
                        else -> null
                    }

                    if (sizeText != null) {
                        Text(
                            text = sizeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }

                    // Show compression savings if available
                    image.compressionSavings?.let { savings ->
                        Text(
                            text = "-$savings",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }

        // Retry button for failed uploads
        if (image.isFailed && onRetry != null) {
            IconButton(
                onClick = onRetry,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(20.dp)
                    .background(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = AutoDevComposeIcons.Refresh,
                    contentDescription = "Retry",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        // Remove button (always show unless analyzing)
        if (!isAnalyzing) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/**
 * Format file size to human readable string.
 */
private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
        bytes >= 1024 -> "${bytes / 1024}KB"
        else -> "${bytes}B"
    }
}

