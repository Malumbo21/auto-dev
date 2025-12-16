package cc.unitmesh.devins.ui.nano

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors
import cc.unitmesh.llm.image.ImageGenerationResult
import cc.unitmesh.llm.image.ImageGenerationService
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.ir.NanoStateIR
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Stateful NanoUI Compose Renderer
 *
 * This renderer maintains state and handles actions for interactive NanoDSL components.
 * It wraps the component rendering with a state context that:
 * 1. Initializes state from NanoIR state definitions
 * 2. Passes state values to components via bindings
 * 3. Updates state when actions are triggered
 * 4. Generates images for Image components if ImageGenerationService is provided
 */
object StatefulNanoRenderer {

    /**
     * Render a NanoIR tree with state management.
     * Automatically initializes state from the IR and provides action handlers.
     *
     * @param ir The NanoIR tree to render
     * @param modifier Modifier for the root component
     */
    @Composable
    fun Render(
        ir: NanoIR,
        modifier: Modifier = Modifier
    ) {
        // Initialize state from IR
        val stateMap = remember { mutableStateMapOf<String, Any>() }

        // Initialize state values from IR state definitions
        LaunchedEffect(ir) {
            ir.state?.variables?.forEach { (name, varDef) ->
                val defaultValue = varDef.defaultValue
                stateMap[name] = when (varDef.type) {
                    "int" -> defaultValue?.jsonPrimitive?.intOrNull ?: 0
                    "float" -> defaultValue?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                    "bool" -> defaultValue?.jsonPrimitive?.booleanOrNull ?: false
                    "str" -> defaultValue?.jsonPrimitive?.content ?: ""
                    else -> defaultValue?.jsonPrimitive?.content ?: ""
                }
            }
        }

        // Create action handler
        val handleAction: (NanoActionIR) -> Unit = handleAction@{ action ->
            when (action.type) {
                "stateMutation" -> {
                    val payload = action.payload ?: return@handleAction
                    val path = payload["path"]?.jsonPrimitive?.content ?: return@handleAction
                    val operation = payload["operation"]?.jsonPrimitive?.content ?: "SET"
                    val valueStr = payload["value"]?.jsonPrimitive?.content ?: ""

                    val currentValue = stateMap[path]
                    val newValue = when (operation) {
                        "ADD" -> {
                            when (currentValue) {
                                is Int -> currentValue + (valueStr.toIntOrNull() ?: 1)
                                is Float -> currentValue + (valueStr.toFloatOrNull() ?: 1f)
                                else -> currentValue
                            }
                        }
                        "SUBTRACT" -> {
                            when (currentValue) {
                                is Int -> currentValue - (valueStr.toIntOrNull() ?: 1)
                                is Float -> currentValue - (valueStr.toFloatOrNull() ?: 1f)
                                else -> currentValue
                            }
                        }
                        "SET" -> {
                            when (currentValue) {
                                is Int -> valueStr.toIntOrNull() ?: 0
                                is Float -> valueStr.toFloatOrNull() ?: 0f
                                is Boolean -> valueStr.toBooleanStrictOrNull() ?: false
                                else -> valueStr
                            }
                        }
                        else -> valueStr
                    }

                    if (newValue != null) {
                        stateMap[path] = newValue
                    }
                }
            }
        }

        RenderNode(ir, stateMap, handleAction, modifier)
    }

    @Composable
    private fun RenderNode(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier = Modifier
    ) {
        when (ir.type) {
            "VStack" -> RenderVStack(ir, state, onAction, modifier)
            "HStack" -> RenderHStack(ir, state, onAction, modifier)
            "Card" -> RenderCard(ir, state, onAction, modifier)
            "Form" -> RenderForm(ir, state, onAction, modifier)
            "Text" -> RenderText(ir, state, modifier)
            "Image" -> RenderImage(ir, modifier)
            "Badge" -> RenderBadge(ir, modifier)
            "Icon" -> RenderIcon(ir, modifier)
            "Divider" -> RenderDivider(modifier)
            "Button" -> RenderButton(ir, onAction, modifier)
            "Input" -> RenderInput(ir, state, onAction, modifier)
            "Checkbox" -> RenderCheckbox(ir, state, onAction, modifier)
            "TextArea" -> RenderTextArea(ir, state, onAction, modifier)
            "Select" -> RenderSelect(ir, state, onAction, modifier)
            "Component" -> RenderComponent(ir, state, onAction, modifier)
            else -> RenderUnknown(ir, modifier)
        }
    }

    // Layout Components

    @Composable
    private fun RenderVStack(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val spacing = ir.props["spacing"]?.jsonPrimitive?.content?.toSpacing() ?: 8.dp
        val padding = ir.props["padding"]?.jsonPrimitive?.content?.toPadding()
        val finalModifier = if (padding != null) modifier.padding(padding) else modifier
        Column(modifier = finalModifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
            ir.children?.forEach { child -> RenderNode(child, state, onAction) }
        }
    }

    @Composable
    private fun RenderHStack(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val spacing = ir.props["spacing"]?.jsonPrimitive?.content?.toSpacing() ?: 8.dp
        val padding = ir.props["padding"]?.jsonPrimitive?.content?.toPadding()
        val align = ir.props["align"]?.jsonPrimitive?.content
        val justify = ir.props["justify"]?.jsonPrimitive?.content

        val verticalAlignment = when (align) {
            "center" -> Alignment.CenterVertically
            "start", "top" -> Alignment.Top
            "end", "bottom" -> Alignment.Bottom
            else -> Alignment.CenterVertically
        }
        val horizontalArrangement = when (justify) {
            "center" -> Arrangement.Center
            "between" -> Arrangement.SpaceBetween
            "around" -> Arrangement.SpaceAround
            "evenly" -> Arrangement.SpaceEvenly
            "end" -> Arrangement.End
            else -> Arrangement.spacedBy(spacing)
        }

        // Apply fillMaxWidth when justify is specified to make space distribution work
        val baseModifier = if (justify != null) modifier.fillMaxWidth() else modifier
        val finalModifier = if (padding != null) baseModifier.padding(padding) else baseModifier

        Row(
            modifier = finalModifier,
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment
        ) {
            ir.children?.forEach { child ->
                // Check if child has explicit flex/weight property
                val childFlex = child.props["flex"]?.jsonPrimitive?.content?.toFloatOrNull()
                val childWeight = child.props["weight"]?.jsonPrimitive?.content?.toFloatOrNull()
                val weight = childFlex ?: childWeight

                if (weight != null && weight > 0f) {
                    // Explicit weight specified
                    Box(modifier = Modifier.weight(weight).wrapContentHeight(unbounded = true)) {
                        RenderNode(child, state, onAction)
                    }
                } else if (child.type == "VStack" && justify == "between") {
                    // VStack in HStack with justify=between should share space equally
                    Box(modifier = Modifier.weight(1f).wrapContentHeight(unbounded = true)) {
                        RenderNode(child, state, onAction)
                    }
                } else {
                    // Default: size to content
                    RenderNode(child, state, onAction)
                }
            }
        }
    }

    @Composable
    private fun RenderCard(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val padding = ir.props["padding"]?.jsonPrimitive?.content?.toPadding() ?: 16.dp
        val shadow = ir.props["shadow"]?.jsonPrimitive?.content

        val elevation = when (shadow) {
            "sm" -> CardDefaults.cardElevation(defaultElevation = 2.dp)
            "md" -> CardDefaults.cardElevation(defaultElevation = 4.dp)
            "lg" -> CardDefaults.cardElevation(defaultElevation = 8.dp)
            else -> CardDefaults.cardElevation()
        }

        Card(modifier = modifier.fillMaxWidth(), elevation = elevation) {
            Column(modifier = Modifier.padding(padding)) {
                ir.children?.forEach { child -> RenderNode(child, state, onAction) }
            }
        }
    }

    @Composable
    private fun RenderForm(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ir.children?.forEach { child -> RenderNode(child, state, onAction) }
        }
    }

    @Composable
    private fun RenderComponent(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        Column(modifier = modifier) {
            ir.children?.forEach { child -> RenderNode(child, state, onAction) }
        }
    }

    // Content Components

    @Composable
    private fun RenderText(ir: NanoIR, state: Map<String, Any>, modifier: Modifier) {
        // Check for binding first
        val binding = ir.bindings?.get("content")
        val content = if (binding != null) {
            // Get value from state based on binding expression
            val expr = binding.expression.removePrefix("state.")
            state[expr]?.toString() ?: ""
        } else {
            ir.props["content"]?.jsonPrimitive?.content ?: ""
        }

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
    private fun RenderImage(
        ir: NanoIR,
        modifier: Modifier
    ) {
        val originalSrc = ir.props["src"]?.jsonPrimitive?.content ?: ""
        val scope = rememberCoroutineScope()

        // State to hold the generated image URL and loaded bitmap
        var generatedImageUrl by remember(originalSrc) { mutableStateOf<String?>(null) }
        var isGenerating by remember(originalSrc) { mutableStateOf(false) }
        var errorMessage by remember(originalSrc) { mutableStateOf<String?>(null) }
        var imageGenerationService by remember { mutableStateOf<ImageGenerationService?>(null) }
        var loadedImageBitmap by remember(originalSrc) { mutableStateOf<ImageBitmap?>(null) }
        var isLoadingImage by remember(originalSrc) { mutableStateOf(false) }

        // Initialize ImageGenerationService from ConfigManager
        LaunchedEffect(Unit) {
            try {
                val configWrapper = ConfigManager.load()
                val glmConfig = configWrapper.getModelConfigByProvider("glm")
                imageGenerationService = glmConfig?.let { ImageGenerationService.create(it) }
            } catch (e: Exception) {
                // Failed to load config, imageGenerationService remains null
            }
        }

        // Generate image when service is ready
        LaunchedEffect(originalSrc, imageGenerationService) {
            if (imageGenerationService != null &&
                !originalSrc.startsWith("data:") &&
                generatedImageUrl == null &&
                !isGenerating) {

                isGenerating = true
                errorMessage = null

                // Extract prompt from surrounding context or URL
                val prompt = extractImagePrompt(originalSrc)

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

        // Load image from URL when generatedImageUrl is available
        LaunchedEffect(generatedImageUrl) {
            if (generatedImageUrl != null && loadedImageBitmap == null && !isLoadingImage) {
                isLoadingImage = true
                try {
                    val bitmap = loadImageFromUrl(generatedImageUrl!!)
                    loadedImageBitmap = bitmap
                } catch (e: Exception) {
                    errorMessage = "Failed to load image: ${e.message}"
                } finally {
                    isLoadingImage = false
                }
            }
        }

        // Display the image
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
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
    }

    /**
     * Load an image from a URL and decode it to ImageBitmap.
     * This is a suspend function that uses Ktor to download the image.
     */
    private suspend fun loadImageFromUrl(url: String): ImageBitmap = withContext(Dispatchers.Default) {
        val client = cc.unitmesh.agent.tool.impl.http.HttpClientFactory.create()
        try {
            val response: io.ktor.client.statement.HttpResponse = client.get(url)
            val bytes = response.readBytes()
            decodeImageBytesToBitmap(bytes)
        } finally {
            client.close()
        }
    }

    /**
     * Extract a meaningful prompt from the image src URL or path.
     * This is a simplified version of NanoDSLAgent.extractImagePrompt.
     */
    private fun extractImagePrompt(src: String): String {
        // Clean up the URL
        val cleaned = src
            .replace(Regex("https?://[^/]+/"), "") // Remove domain
            .replace(Regex("[?#].*"), "") // Remove query params
            .replace(Regex("[-_/.]"), " ") // Replace separators with spaces
            .replace(Regex("\\d+"), "") // Remove numbers
            .trim()

        // If we got a meaningful string, use it
        if (cleaned.length > 3 && cleaned.any { it.isLetter() }) {
            return cleaned.take(100) // Limit length
        }

        // Fallback
        return "high quality image"
    }

    @Composable
    private fun RenderBadge(ir: NanoIR, modifier: Modifier) {
        val text = ir.props["text"]?.jsonPrimitive?.content ?: ""
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
    private fun RenderIcon(ir: NanoIR, modifier: Modifier) {
        val iconName = ir.props["name"]?.jsonPrimitive?.content ?: ""
        val sizeName = ir.props["size"]?.jsonPrimitive?.content
        val colorName = ir.props["color"]?.jsonPrimitive?.content

        // Map icon names to Material Icons
        val iconVector: ImageVector = when (iconName.lowercase()) {
            "flight", "airplane", "plane" -> Icons.Default.Flight
            "hotel", "bed" -> Icons.Default.Hotel
            "restaurant", "food", "dining" -> Icons.Default.Restaurant
            "attractions", "place", "location" -> Icons.Default.Place
            "map" -> Icons.Default.Map
            "calendar", "event" -> Icons.Default.Event
            "time", "schedule" -> Icons.Default.Schedule
            "check", "done" -> Icons.Default.Check
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
    private fun RenderDivider(modifier: Modifier) {
        HorizontalDivider(modifier.padding(vertical = 8.dp))
    }

    // Input Components

    @Composable
    private fun RenderButton(ir: NanoIR, onAction: (NanoActionIR) -> Unit, modifier: Modifier) {
        val label = ir.props["label"]?.jsonPrimitive?.content ?: "Button"
        val intent = ir.props["intent"]?.jsonPrimitive?.content
        val onClick = ir.actions?.get("onClick")

        when (intent) {
            "secondary" -> OutlinedButton(
                onClick = { onClick?.let { onAction(it) } },
                modifier = modifier
            ) {
                Text(label)
            }
            else -> {
                val colors = when (intent) {
                    "danger" -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    else -> ButtonDefaults.buttonColors()
                }
                Button(
                    onClick = { onClick?.let { onAction(it) } },
                    colors = colors,
                    modifier = modifier
                ) {
                    Text(label)
                }
            }
        }
    }

    @Composable
    private fun RenderInput(
        ir: NanoIR, state: Map<String, Any>, onAction: (NanoActionIR) -> Unit, modifier: Modifier
    ) {
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: ""
        val binding = ir.bindings?.get("value")
        val statePath = binding?.expression?.removePrefix("state.")

        var value by remember(statePath, state[statePath]) {
            mutableStateOf(state[statePath]?.toString() ?: "")
        }

        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                value = newValue
                if (statePath != null) {
                    onAction(NanoActionIR(
                        type = "stateMutation",
                        payload = mapOf(
                            "path" to JsonPrimitive(statePath),
                            "operation" to JsonPrimitive("SET"),
                            "value" to JsonPrimitive(newValue)
                        )
                    ))
                }
            },
            placeholder = { Text(placeholder) },
            modifier = modifier.fillMaxWidth(),
            singleLine = true
        )
    }

    @Composable
    private fun RenderCheckbox(
        ir: NanoIR, state: Map<String, Any>, onAction: (NanoActionIR) -> Unit, modifier: Modifier
    ) {
        val binding = ir.bindings?.get("checked")
        val statePath = binding?.expression?.removePrefix("state.")
        val checked = (state[statePath] as? Boolean) ?: false

        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checked,
                onCheckedChange = { newValue ->
                    if (statePath != null) {
                        onAction(NanoActionIR(
                            type = "stateMutation",
                            payload = mapOf(
                                "path" to JsonPrimitive(statePath),
                                "operation" to JsonPrimitive("SET"),
                                "value" to JsonPrimitive(newValue.toString())
                            )
                        ))
                    }
                }
            )
        }
    }

    @Composable
    private fun RenderTextArea(
        ir: NanoIR, state: Map<String, Any>, onAction: (NanoActionIR) -> Unit, modifier: Modifier
    ) {
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: ""
        val rows = ir.props["rows"]?.jsonPrimitive?.intOrNull ?: 4
        val binding = ir.bindings?.get("value")
        val statePath = binding?.expression?.removePrefix("state.")

        var value by remember(statePath, state[statePath]) {
            mutableStateOf(state[statePath]?.toString() ?: "")
        }

        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                value = newValue
                if (statePath != null) {
                    onAction(NanoActionIR(
                        type = "stateMutation",
                        payload = mapOf(
                            "path" to JsonPrimitive(statePath),
                            "operation" to JsonPrimitive("SET"),
                            "value" to JsonPrimitive(newValue)
                        )
                    ))
                }
            },
            placeholder = { Text(placeholder) },
            modifier = modifier.fillMaxWidth().height((rows * 24).dp),
            minLines = rows
        )
    }

    @Composable
    private fun RenderSelect(
        ir: NanoIR, state: Map<String, Any>, onAction: (NanoActionIR) -> Unit, modifier: Modifier
    ) {
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: "Select..."
        val binding = ir.bindings?.get("value")
        val statePath = binding?.expression?.removePrefix("state.")
        val selectedValue = state[statePath]?.toString() ?: ""
        var expanded by remember { mutableStateOf(false) }

        // Read options from IR props
        val options: List<String> = ir.props["options"]?.let { optionsElement ->
            try {
                (optionsElement as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            } catch (e: Exception) { null }
        } ?: emptyList()

        Box(modifier = modifier) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (selectedValue.isNotEmpty()) selectedValue else placeholder)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            if (statePath != null) {
                                onAction(NanoActionIR(
                                    type = "stateMutation",
                                    payload = mapOf(
                                        "path" to JsonPrimitive(statePath),
                                        "operation" to JsonPrimitive("SET"),
                                        "value" to JsonPrimitive(option)
                                    )
                                ))
                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun RenderUnknown(ir: NanoIR, modifier: Modifier) {
        Surface(
            modifier = modifier.border(1.dp, Color.Red, RoundedCornerShape(4.dp)),
            color = Color.Red.copy(alpha = 0.1f)
        ) {
            Text(
                text = "Unknown: ${ir.type}",
                modifier = Modifier.padding(8.dp),
                color = Color.Red
            )
        }
    }

    // Utility extensions
    private fun String.toSpacing() = when (this) {
        "xs" -> 4.dp
        "sm" -> 8.dp
        "md" -> 16.dp
        "lg" -> 24.dp
        "xl" -> 32.dp
        "none" -> 0.dp
        else -> 8.dp
    }

    private fun String.toPadding() = when (this) {
        "xs" -> 4.dp
        "sm" -> 8.dp
        "md" -> 16.dp
        "lg" -> 24.dp
        "xl" -> 32.dp
        "none" -> 0.dp
        else -> 16.dp
    }
}

/**
 * Decode image bytes to ImageBitmap.
 * Platform-specific implementation.
 */
internal expect fun decodeImageBytesToBitmap(bytes: ByteArray): ImageBitmap
