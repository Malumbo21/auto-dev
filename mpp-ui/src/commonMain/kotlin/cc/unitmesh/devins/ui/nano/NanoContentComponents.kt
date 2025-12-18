package cc.unitmesh.devins.ui.nano

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors
import cc.unitmesh.llm.image.ImageGenerationResult
import cc.unitmesh.llm.image.ImageGenerationService
import cc.unitmesh.xuiper.ir.NanoIR
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive

/**
 * Content components for NanoUI Compose renderer.
 * Includes: Text, Image, Badge, Icon, Divider
 */
object NanoContentComponents {

    @Composable
    fun RenderText(ir: NanoIR, state: Map<String, Any>, modifier: Modifier) {
        // Check for binding first
        val binding = ir.bindings?.get("content")
        val rawContent = if (binding != null) {
            // Get value from state based on binding expression (supports dotted paths)
            NanoRenderUtils.evaluateExpression(binding.expression, state)
        } else {
            ir.props["content"]?.jsonPrimitive?.content ?: ""
        }

        // Interpolate {state.xxx} or {state.xxx + 1} expressions in content
        val content = NanoRenderUtils.interpolateText(rawContent, state)

        val style = ir.props["style"]?.jsonPrimitive?.content

        val textStyle = when (style) {
            "h1" -> MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
            "h2" -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold)
            "h3" -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium)
            "h4" -> MaterialTheme.typography.titleLarge
            "body" -> MaterialTheme.typography.bodyLarge
            "caption" -> MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> MaterialTheme.typography.bodyMedium
        }

        Text(text = content, style = textStyle, modifier = modifier)
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

        val chromeModifier = Modifier
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

    @Composable
    private fun RenderImageContent(
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
                imageGenerationService = glmConfig?.let { ImageGenerationService.create(it) }
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Generating image...", style = MaterialTheme.typography.bodySmall)
                }
            }
            isLoadingImage -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Loading image...", style = MaterialTheme.typography.bodySmall)
                }
            }
            errorMessage != null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠️ Error", color = MaterialTheme.colorScheme.error)
                    Text(errorMessage!!, style = MaterialTheme.typography.bodySmall)
                }
            }
            loadedImageBitmap != null -> {
                Image(
                    bitmap = loadedImageBitmap!!,
                    contentDescription = originalSrc,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                Text("Image: $originalSrc", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    /**
     * Download image bytes from URL.
     */
    private suspend fun downloadImageBytes(url: String): ByteArray = withContext(Dispatchers.Default) {
        val client = cc.unitmesh.agent.tool.impl.http.HttpClientFactory.create()
        try {
            val response: io.ktor.client.statement.HttpResponse = client.get(url)
            response.readBytes()
        } finally {
            client.close()
        }
    }

    @Composable
    fun RenderBadge(ir: NanoIR, state: Map<String, Any>, modifier: Modifier) {
        val rawText = NanoRenderUtils.resolveStringProp(ir, "text", state)
        val text = NanoRenderUtils.interpolateText(rawText, state)
        val colorName = ir.props["color"]?.jsonPrimitive?.content

        val bgColor = when (colorName) {
            "green" -> AutoDevColors.Signal.success
            "red" -> AutoDevColors.Signal.error
            "blue" -> AutoDevColors.Signal.info
            "yellow" -> AutoDevColors.Signal.warn
            "orange" -> AutoDevColors.Signal.warn
            else -> MaterialTheme.colorScheme.primaryContainer
        }

        val textColor = if (colorName == "yellow") Color.Black else Color.White

        Surface(modifier = modifier, shape = RoundedCornerShape(4.dp), color = bgColor) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = textColor,
                fontSize = 12.sp
            )
        }
    }

    @Composable
    fun RenderIcon(ir: NanoIR, modifier: Modifier) {
        val iconName = ir.props["name"]?.jsonPrimitive?.content ?: ""
        val sizeName = ir.props["size"]?.jsonPrimitive?.content
        val colorName = ir.props["color"]?.jsonPrimitive?.content

        // Map icon names to Material Icons
        val iconVector: ImageVector = when (iconName.lowercase()) {
            "flight", "airplane", "plane" -> Icons.Default.Flight
            "arrow-right" -> Icons.AutoMirrored.Filled.ArrowForward
            "arrow-left" -> Icons.AutoMirrored.Filled.ArrowBack
            "arrow-down" -> Icons.Default.ArrowDropDown
            "arrow-up" -> Icons.Default.ArrowDropUp
            "hotel", "bed" -> Icons.Default.Hotel
            "restaurant", "food", "dining" -> Icons.Default.Restaurant
            "attractions", "place", "location" -> Icons.Default.Place
            "map" -> Icons.Default.Map
            "calendar", "event" -> Icons.Default.Event
            "time", "schedule" -> Icons.Default.Schedule
            "check", "done" -> Icons.Default.Check
            "check-circle", "check_circle", "checkcircle" -> Icons.Default.CheckCircle
            "star", "favorite" -> Icons.Default.Star
            "info" -> Icons.Default.Info
            "warning" -> Icons.Default.Warning
            "error" -> Icons.Default.Error
            "home" -> Icons.Default.Home
            "person", "user" -> Icons.Default.Person
            "settings" -> Icons.Default.Settings
            "search" -> Icons.Default.Search
            "menu" -> Icons.Default.Menu
            "close" -> Icons.Default.Close
            "add" -> Icons.Default.Add
            "edit" -> Icons.Default.Edit
            "delete" -> Icons.Default.Delete
            "share" -> Icons.Default.Share
            "send" -> Icons.Default.Send
            "email", "mail" -> Icons.Default.Email
            "phone" -> Icons.Default.Phone
            "camera" -> Icons.Default.CameraAlt
            "image", "photo" -> Icons.Default.Image
            "attach", "attachment" -> Icons.Default.AttachFile
            "download" -> Icons.Default.Download
            "upload" -> Icons.Default.Upload
            "refresh" -> Icons.Default.Refresh
            "arrow_back", "back" -> Icons.Default.ArrowBack
            "arrow_forward", "forward" -> Icons.Default.ArrowForward
            else -> Icons.Default.Info // Default fallback
        }

        // Map size to dp
        val iconSize = when (sizeName) {
            "sm", "small" -> 16.dp
            "md", "medium" -> 24.dp
            "lg", "large" -> 32.dp
            "xl" -> 48.dp
            else -> 24.dp
        }

        // Map color name to Color
        val iconColor = when (colorName) {
            "primary" -> MaterialTheme.colorScheme.primary
            "secondary" -> MaterialTheme.colorScheme.secondary
            "green" -> AutoDevColors.Signal.success
            "red" -> AutoDevColors.Signal.error
            "blue" -> AutoDevColors.Signal.info
            "yellow", "orange" -> AutoDevColors.Signal.warn
            else -> MaterialTheme.colorScheme.onSurface
        }

        Icon(
            imageVector = iconVector,
            contentDescription = iconName,
            modifier = modifier.size(iconSize),
            tint = iconColor
        )
    }

    @Composable
    fun RenderDivider(modifier: Modifier) {
        HorizontalDivider(modifier.padding(vertical = 8.dp))
    }
}
