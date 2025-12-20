package cc.unitmesh.devins.ui.nano

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cc.unitmesh.agent.tool.impl.http.HttpClientFactory
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons
import cc.unitmesh.llm.image.ImageGenerationResult
import cc.unitmesh.llm.image.ImageGenerationService
import cc.unitmesh.xuiper.ir.NanoIR
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.jsonPrimitive

/**
 * Download image bytes from URL.
 */
suspend fun downloadImageBytes(url: String): ByteArray = withContext(Dispatchers.Default) {
    val client = HttpClientFactory.create()
    try {
        val response: HttpResponse = client.get(url)
        response.readRawBytes()
    } finally {
        client.close()
    }
}

@Composable
fun RenderImageContent(
    originalSrc: String
) {
    var generatedImageUrl by remember(originalSrc) { mutableStateOf<String?>(null) }
    var isGenerating by remember(originalSrc) { mutableStateOf(false) }
    var imageGenerationService by remember { mutableStateOf<ImageGenerationService?>(null) }

    val resolvedSrc = remember(originalSrc, generatedImageUrl) { generatedImageUrl ?: originalSrc }
    var errorMessage by remember(resolvedSrc) { mutableStateOf<String?>(null) }
    var loadedImageBitmap by remember(resolvedSrc) { mutableStateOf<ImageBitmap?>(null) }
    var isLoadingImage by remember(resolvedSrc) { mutableStateOf(false) }
    var showPreviewDialog by remember(resolvedSrc) { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val configWrapper = ConfigManager.load()
            val glmConfig = configWrapper.getModelConfigByProvider("glm")
            imageGenerationService = glmConfig?.let { ImageGenerationService.Companion.create(it) }
        } catch (e: Exception) {
            // Ignore; fall back to showing placeholder text.
        }
    }

    LaunchedEffect(originalSrc, imageGenerationService) {
        if (imageGenerationService != null &&
            !isDirectImageSrc(originalSrc) &&
            generatedImageUrl == null &&
            !isGenerating
        ) {
            isGenerating = true
            errorMessage = null

            val prompt = NanoExpressionEvaluator.extractImagePrompt(originalSrc)
            when (val result = imageGenerationService!!.generateImage(prompt)) {
                is ImageGenerationResult.Success -> {
                    generatedImageUrl = result.imageUrl
                    isGenerating = false
                }

                is ImageGenerationResult.Error -> {
                    errorMessage = result.message
                    isGenerating = false
                }
            }
        }
    }

    LaunchedEffect(resolvedSrc) {
        if (!isDirectImageSrc(resolvedSrc) || loadedImageBitmap != null || isLoadingImage) {
            return@LaunchedEffect
        }

        if (loadedImageBitmap == null && !isLoadingImage) {
            isLoadingImage = true
            try {
                val cacheKey = nanoImageCacheKeyFromSrc(stableCacheKeySrc(resolvedSrc))
                val imageBytes = NanoImageCache.getOrPut(cacheKey) { loadImageBytesFromSrc(resolvedSrc) }
                val bitmap = decodeImageBytesToBitmap(imageBytes)
                loadedImageBitmap = bitmap
            } catch (e: Exception) {
                errorMessage = "Failed to load image: ${e.message}"
            } finally {
                isLoadingImage = false
            }
        }
    }

    when {
        isGenerating -> {
            Column(horizontalAlignment = Alignment.Companion.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.Companion.size(32.dp))
                Spacer(modifier = Modifier.Companion.height(8.dp))
                Text("Generating image...", style = MaterialTheme.typography.bodySmall)
            }
        }
        isLoadingImage -> {
            Column(horizontalAlignment = Alignment.Companion.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.Companion.size(32.dp))
                Spacer(modifier = Modifier.Companion.height(8.dp))
                Text("Loading image...", style = MaterialTheme.typography.bodySmall)
            }
        }
        errorMessage != null -> {
            Column(horizontalAlignment = Alignment.Companion.CenterHorizontally) {
                Text("⚠️ Error", color = MaterialTheme.colorScheme.error)
                Text(errorMessage!!, style = MaterialTheme.typography.bodySmall)
            }
        }
        loadedImageBitmap != null -> {
            Image(
                bitmap = loadedImageBitmap!!,
                contentDescription = resolvedSrc,
                modifier = Modifier.Companion
                    .fillMaxSize()
                    .clickable { showPreviewDialog = true },
                contentScale = ContentScale.Companion.Crop
            )

            if (showPreviewDialog) {
                NanoImagePreviewDialog(
                    bitmap = loadedImageBitmap!!,
                    title = resolvedSrc,
                    onDismiss = { showPreviewDialog = false }
                )
            }
        }
        else -> {
            Text("Image: $resolvedSrc", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NanoImagePreviewDialog(
    bitmap: ImageBitmap,
    title: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun zoomTo(newScale: Float) {
        scale = newScale.coerceIn(1f, 5f)
        if (scale == 1f) offset = Offset.Zero
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title.take(80),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { zoomTo(scale - 0.25f) }, enabled = scale > 1f) {
                            Icon(imageVector = AutoDevComposeIcons.ZoomOut, contentDescription = "Zoom Out")
                        }
                        IconButton(onClick = { zoomTo(scale + 0.25f) }, enabled = scale < 5f) {
                            Icon(imageVector = AutoDevComposeIcons.ZoomIn, contentDescription = "Zoom In")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = AutoDevComposeIcons.Close, contentDescription = "Close")
                        }
                    }
                }

                HorizontalDivider()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (scale * zoom).coerceIn(1f, 5f)
                                // Keep pan disabled when not zoomed.
                                if (nextScale == 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = nextScale
                                    offset += pan
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )
                }
            }
        }
    }
}

private fun isDirectImageSrc(src: String): Boolean {
    val trimmed = src.trim()
    if (trimmed.isEmpty()) return false

    if (trimmed.startsWith("data:image/", ignoreCase = true)) return true
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) return true
    if (trimmed.startsWith("file://", ignoreCase = true)) return true

    val lower = trimmed.lowercase()
    if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") ||
        lower.endsWith(".webp") || lower.endsWith(".bmp") || lower.endsWith(".svg")
    ) {
        return true
    }

    // Raw base64 payload (no data: prefix). Keep this conservative so normal prompts don't get misdetected.
    if (!trimmed.contains(' ') && trimmed.length >= 256 && BASE64_PAYLOAD_REGEX.matches(trimmed)) return true

    return false
}

private fun stableCacheKeySrc(src: String): String {
    val trimmed = src.trim()
    // Avoid hashing huge inline base64/data-uri strings.
    return when {
        trimmed.startsWith("data:", ignoreCase = true) -> "data:${trimmed.length}:${trimmed.take(160)}"
        !trimmed.contains(' ') && trimmed.length >= 256 && BASE64_PAYLOAD_REGEX.matches(trimmed) -> "b64:${trimmed.length}:${trimmed.take(160)}"
        else -> trimmed
    }
}

@OptIn(ExperimentalEncodingApi::class)
private suspend fun loadImageBytesFromSrc(src: String): ByteArray {
    val trimmed = src.trim()
    return when {
        trimmed.startsWith("data:", ignoreCase = true) -> decodeDataUriToBytes(trimmed)
        trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> downloadImageBytes(trimmed)
        // Raw base64 without data: prefix
        !trimmed.contains(' ') && trimmed.length >= 256 && BASE64_PAYLOAD_REGEX.matches(trimmed) -> Base64.decode(trimmed)
        else -> error("Unsupported image src: ${trimmed.take(60)}")
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun decodeDataUriToBytes(dataUri: String): ByteArray {
    val commaIndex = dataUri.indexOf(',')
    require(commaIndex >= 0) { "Invalid data URI (missing comma)" }

    val meta = dataUri.substring(5, commaIndex) // after "data:"
    val payload = dataUri.substring(commaIndex + 1)
    val isBase64 = meta.contains(";base64", ignoreCase = true)
    require(isBase64) { "Unsupported data URI encoding (expected base64)" }

    return Base64.decode(payload)
}

private val BASE64_PAYLOAD_REGEX = Regex("^[A-Za-z0-9+/\\r\\n]+=*$")

@Composable
fun RenderImage(
    ir: NanoIR,
    modifier: Modifier
) {
     val originalSrc = ir.props["src"]?.jsonPrimitive?.content ?: ""

    val widthPx = ir.props["width"]?.jsonPrimitive?.content?.toIntOrNull()
    val aspectStr = ir.props["aspect"]?.jsonPrimitive?.content
    val radiusStr = ir.props["radius"]?.jsonPrimitive?.content

    val aspectRatio = remember(aspectStr) {
        val trimmed = aspectStr?.trim().orEmpty()
        when {
            trimmed.contains('/') -> {
                val parts = trimmed.split('/', limit = 2)
                val a = parts.getOrNull(0)?.trim()?.toFloatOrNull()
                val b = parts.getOrNull(1)?.trim()?.toFloatOrNull()
                if (a != null && b != null && b != 0f) a / b else null
            }
            trimmed.isNotEmpty() -> trimmed.toFloatOrNull()
            else -> null
        }
    }

    val cornerRadius = when (radiusStr) {
        "sm" -> 4.dp
        "md" -> 8.dp
        "lg" -> 12.dp
        "xl" -> 16.dp
        else -> 8.dp
    }

    // Keep the image visually present even when its parent gives it a narrow width
    // (e.g., in HStack with a text column). We compute a single target height based on
    // available width + aspect ratio, then enforce a minimum height reliably.
    val minHeight = 240.dp
    val fallbackHeight = 280.dp

    val chromeModifier = Modifier.Companion
        .clip(RoundedCornerShape(cornerRadius))
        .background(MaterialTheme.colorScheme.surfaceVariant)

    if (widthPx != null) {
        val width = widthPx.dp
        val height = when {
            aspectRatio != null && aspectRatio > 0f -> (width / aspectRatio).coerceAtLeast(minHeight)
            else -> fallbackHeight
        }

        Box(
            modifier = modifier
                .width(width)
                .height(height)
                .then(chromeModifier),
            contentAlignment = Alignment.Center
        ) {
            RenderImageContent(originalSrc)
        }
    } else {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            val height = when {
                aspectRatio != null && aspectRatio > 0f -> (maxWidth / aspectRatio).coerceAtLeast(minHeight)
                else -> fallbackHeight
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .then(chromeModifier),
                contentAlignment = Alignment.Center
            ) {
                RenderImageContent(originalSrc)
            }
        }
    }
}
