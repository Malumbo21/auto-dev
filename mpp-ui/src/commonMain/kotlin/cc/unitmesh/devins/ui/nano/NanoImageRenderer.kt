package cc.unitmesh.devins.ui.nano

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cc.unitmesh.agent.tool.impl.http.HttpClientFactory
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.llm.image.ImageGenerationResult
import cc.unitmesh.llm.image.ImageGenerationService
import cc.unitmesh.xuiper.ir.NanoIR
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    var errorMessage by remember(originalSrc) { mutableStateOf<String?>(null) }
    var imageGenerationService by remember { mutableStateOf<ImageGenerationService?>(null) }
    var loadedImageBitmap by remember(originalSrc) { mutableStateOf<ImageBitmap?>(null) }
    var isLoadingImage by remember(originalSrc) { mutableStateOf(false) }

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
            !originalSrc.startsWith("data:") &&
            generatedImageUrl == null &&
            !isGenerating
        ) {
            isGenerating = true
            errorMessage = null

            val prompt = NanoRenderUtils.extractImagePrompt(originalSrc)
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

    LaunchedEffect(generatedImageUrl) {
        if (generatedImageUrl != null && loadedImageBitmap == null && !isLoadingImage) {
            isLoadingImage = true
            try {
                val url = generatedImageUrl!!
                val cacheKey = nanoImageCacheKeyFromSrc(url)
                val imageBytes = NanoImageCache.getOrPut(cacheKey) {
                    downloadImageBytes(url)
                }
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
                contentDescription = originalSrc,
                modifier = Modifier.Companion.fillMaxSize(),
                contentScale = ContentScale.Companion.Crop
            )
        }
        else -> {
            Text("Image: $originalSrc", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

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
