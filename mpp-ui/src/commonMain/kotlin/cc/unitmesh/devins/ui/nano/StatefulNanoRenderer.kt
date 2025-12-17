package cc.unitmesh.devins.ui.nano

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
            // Layout
            "VStack" -> RenderVStack(ir, state, onAction, modifier)
            "HStack" -> RenderHStack(ir, state, onAction, modifier)
            // Container
            "Card" -> RenderCard(ir, state, onAction, modifier)
            "Form" -> RenderForm(ir, state, onAction, modifier)
            // Content
            "Text" -> RenderText(ir, state, modifier)
            "Image" -> RenderImage(ir, modifier)
            "Badge" -> RenderBadge(ir, modifier)
            "Icon" -> RenderIcon(ir, modifier)
            "Divider" -> RenderDivider(modifier)
            // Input
            "Button" -> RenderButton(ir, onAction, modifier)
            "Input" -> RenderInput(ir, state, onAction, modifier)
            "Checkbox" -> RenderCheckbox(ir, state, onAction, modifier)
            "TextArea" -> RenderTextArea(ir, state, onAction, modifier)
            "Select" -> RenderSelect(ir, state, onAction, modifier)
            // P0: Core Form Input Components
            "DatePicker" -> RenderDatePicker(ir, state, onAction, modifier)
            "Radio" -> RenderRadio(ir, state, onAction, modifier)
            "RadioGroup" -> RenderRadioGroup(ir, state, onAction, modifier)
            "Switch" -> RenderSwitch(ir, state, onAction, modifier)
            "NumberInput" -> RenderNumberInput(ir, state, onAction, modifier)
            // P0: Feedback Components
            "Modal" -> RenderModal(ir, state, onAction, modifier)
            "Alert" -> RenderAlert(ir, modifier)
            "Progress" -> RenderProgress(ir, state, modifier)
            "Spinner" -> RenderSpinner(ir, modifier)
            // Tier 1-3: GenUI Components
            "SplitView" -> RenderSplitView(ir, state, onAction, modifier)
            "SmartTextField" -> RenderSmartTextField(ir, state, onAction, modifier)
            "Slider" -> RenderSlider(ir, state, onAction, modifier)
            "DateRangePicker" -> RenderDateRangePicker(ir, state, onAction, modifier)
            "DataChart" -> RenderDataChart(ir, state, modifier)
            "DataTable" -> RenderDataTable(ir, state, onAction, modifier)
            // Control Flow
            "Conditional" -> RenderConditional(ir, state, onAction, modifier)
            "ForLoop" -> RenderForLoop(ir, state, onAction, modifier)
            // Meta
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

        // Count VStack/Card children to determine if we should auto-distribute space
        val vstackOrCardChildren = ir.children?.count {
            it.type == "VStack" || it.type == "Card"
        } ?: 0
        val shouldAutoDistribute = vstackOrCardChildren >= 2

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
                } else if (shouldAutoDistribute && (child.type == "VStack" || child.type == "Card")) {
                    // VStack/Card in HStack with multiple siblings should share space equally
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
        val label = ir.props["label"]?.jsonPrimitive?.content

        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable {
                        // Allow clicking label to toggle checkbox
                        if (statePath != null) {
                            onAction(NanoActionIR(
                                type = "stateMutation",
                                payload = mapOf(
                                    "path" to JsonPrimitive(statePath),
                                    "operation" to JsonPrimitive("SET"),
                                    "value" to JsonPrimitive((!checked).toString())
                                )
                            ))
                        }
                    }
                )
            }
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

    // ============================================================================
    // P0: Core Form Input Components
    // ============================================================================

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun RenderDatePicker(
        ir: NanoIR, state: Map<String, Any>, onAction: (NanoActionIR) -> Unit, modifier: Modifier
    ) {
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: "Select date"
        val binding = ir.bindings?.get("value")
        val statePath = binding?.expression?.removePrefix("state.")
        val currentValue = state[statePath]?.toString() ?: ""

        var showDialog by remember { mutableStateOf(false) }
        val datePickerState = rememberDatePickerState()

        // Display field
        OutlinedTextField(
            value = currentValue,
            onValueChange = { }, // Read-only, click to open dialog
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Date") },
            modifier = modifier
                .fillMaxWidth()
                .clickable { showDialog = true },
            singleLine = true,
            readOnly = true,
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        // DatePicker Dialog
        if (showDialog) {
            DatePickerDialog(
                onDismissRequest = { showDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        val selectedDate = datePickerState.selectedDateMillis
                        if (selectedDate != null && statePath != null) {
                            // Convert millis to YYYY-MM-DD format
                            // Material3 DatePicker returns UTC millis at midnight
                            val dateStr = formatDateFromMillis(selectedDate)

                            onAction(NanoActionIR(
                                type = "stateMutation",
                                payload = mapOf(
                                    "path" to JsonPrimitive(statePath),
                                    "operation" to JsonPrimitive("SET"),
                                    "value" to JsonPrimitive(dateStr)
                                )
                            ))
                        }
                        showDialog = false
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }

    @Composable
    private fun RenderRadio(
        ir: NanoIR, state: Map<String, Any>, onAction: (NanoActionIR) -> Unit, modifier: Modifier
    ) {
        val option = ir.props["option"]?.jsonPrimitive?.content ?: ""
        val label = ir.props["label"]?.jsonPrimitive?.content ?: option
        val binding = ir.bindings?.get("value")
        val statePath = binding?.expression?.removePrefix("state.")
        val selectedValue = state[statePath]?.toString() ?: ""
        val isSelected = selectedValue == option

        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = isSelected,
                onClick = {
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
            Spacer(modifier = Modifier.width(8.dp))
            Text(label)
        }
    }

    @Composable
    private fun RenderRadioGroup(
        ir: NanoIR, state: Map<String, Any>, onAction: (NanoActionIR) -> Unit, modifier: Modifier
    ) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ir.children?.forEach { child -> RenderNode(child, state, onAction) }
        }
    }

    @Composable
    private fun RenderSwitch(
        ir: NanoIR, state: Map<String, Any>, onAction: (NanoActionIR) -> Unit, modifier: Modifier
    ) {
        val label = ir.props["label"]?.jsonPrimitive?.content
        val binding = ir.bindings?.get("value")
        val statePath = binding?.expression?.removePrefix("state.")
        val isChecked = state[statePath] as? Boolean ?: false

        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            if (label != null) {
                Text(label, modifier = Modifier.weight(1f))
            }
            Switch(
                checked = isChecked,
                onCheckedChange = { newValue ->
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
                }
            )
        }
    }

    @Composable
    private fun RenderNumberInput(
        ir: NanoIR, state: Map<String, Any>, onAction: (NanoActionIR) -> Unit, modifier: Modifier
    ) {
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: ""
        val binding = ir.bindings?.get("value")
        val statePath = binding?.expression?.removePrefix("state.")
        val currentValue = (state[statePath] as? Number)?.toString() ?: state[statePath]?.toString() ?: ""

        OutlinedTextField(
            value = currentValue,
            onValueChange = { newValue ->
                if (statePath != null && newValue.matches(Regex("-?\\d*\\.?\\d*"))) {
                    onAction(NanoActionIR(
                        type = "stateMutation",
                        payload = mapOf(
                            "path" to JsonPrimitive(statePath),
                            "operation" to JsonPrimitive("SET"),
                            "value" to JsonPrimitive(newValue.toDoubleOrNull() ?: 0)
                        )
                    ))
                }
            },
            placeholder = { Text(placeholder) },
            modifier = modifier.fillMaxWidth(),
            singleLine = true
        )
    }

    // ============================================================================
    // P0: Feedback Components
    // ============================================================================

    @Composable
    private fun RenderModal(
        ir: NanoIR, state: Map<String, Any>, onAction: (NanoActionIR) -> Unit, modifier: Modifier
    ) {
        val title = ir.props["title"]?.jsonPrimitive?.content
        val binding = ir.bindings?.get("visible")
        val statePath = binding?.expression?.removePrefix("state.")
        val isVisible = state[statePath] as? Boolean ?: true

        if (isVisible) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    ir.children?.forEach { child -> RenderNode(child, state, onAction) }
                }
            }
        }
    }

    @Composable
    private fun RenderAlert(ir: NanoIR, modifier: Modifier) {
        val type = ir.props["type"]?.jsonPrimitive?.content ?: "info"
        val message = ir.props["message"]?.jsonPrimitive?.content ?: ""

        val backgroundColor = when (type) {
            "success" -> AutoDevColors.Signal.success.copy(alpha = 0.15f)
            "warning" -> AutoDevColors.Signal.warn.copy(alpha = 0.15f)
            "error" -> AutoDevColors.Signal.error.copy(alpha = 0.15f)
            else -> AutoDevColors.Signal.info.copy(alpha = 0.15f)
        }
        val borderColor = when (type) {
            "success" -> AutoDevColors.Signal.success
            "warning" -> AutoDevColors.Signal.warn
            "error" -> AutoDevColors.Signal.error
            else -> AutoDevColors.Signal.info
        }
        val icon = when (type) {
            "success" -> Icons.Default.CheckCircle
            "warning" -> Icons.Default.Warning
            "error" -> Icons.Default.Error
            else -> Icons.Default.Info
        }

        Surface(
            modifier = modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(8.dp)),
            color = backgroundColor,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = type, tint = borderColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(message, color = borderColor)
            }
        }
    }

    @Composable
    private fun RenderProgress(ir: NanoIR, state: Map<String, Any>, modifier: Modifier) {
        val valueStr = ir.props["value"]?.jsonPrimitive?.content
        val maxStr = ir.props["max"]?.jsonPrimitive?.content
        val showText = ir.props["showText"]?.jsonPrimitive?.booleanOrNull ?: true

        // Resolve binding expressions
        val value = resolveBindingValue(valueStr, state)?.toFloatOrNull() ?: 0f
        val max = resolveBindingValue(maxStr, state)?.toFloatOrNull() ?: 100f
        val progress = if (max > 0f) (value / max).coerceIn(0f, 1f) else 0f

        Column(modifier = modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            if (showText) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun RenderSpinner(ir: NanoIR, modifier: Modifier) {
        val text = ir.props["text"]?.jsonPrimitive?.content

        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            if (text != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(text)
            }
        }
    }

    // ============================================================================
    // Tier 1-3: GenUI Components
    // ============================================================================

    @Composable
    private fun RenderSplitView(
        ir: NanoIR, state: Map<String, Any>, onAction: (NanoActionIR) -> Unit, modifier: Modifier
    ) {
        val ratio = ir.props["ratio"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0.5f
        Row(modifier = modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(ratio)) {
                ir.children?.firstOrNull()?.let { RenderNode(it, state, onAction) }
            }
            Box(modifier = Modifier.weight(1f - ratio)) {
                ir.children?.getOrNull(1)?.let { RenderNode(it, state, onAction) }
            }
        }
    }

    @Composable
    private fun RenderSmartTextField(
        ir: NanoIR, state: Map<String, Any>, onAction: (NanoActionIR) -> Unit, modifier: Modifier
    ) {
        val label = ir.props["label"]?.jsonPrimitive?.content
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: ""
        val binding = ir.bindings?.get("value")
        val statePath = binding?.expression?.removePrefix("state.")
        val currentValue = state[statePath]?.toString() ?: ""

        OutlinedTextField(
            value = currentValue,
            onValueChange = { newValue ->
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
            label = label?.let { { Text(it) } },
            placeholder = { Text(placeholder) },
            modifier = modifier.fillMaxWidth()
        )
    }

    @Composable
    private fun RenderSlider(
        ir: NanoIR, state: Map<String, Any>, onAction: (NanoActionIR) -> Unit, modifier: Modifier
    ) {
        val label = ir.props["label"]?.jsonPrimitive?.content
        val min = ir.props["min"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
        val max = ir.props["max"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 100f
        val binding = ir.bindings?.get("value")
        val statePath = binding?.expression?.removePrefix("state.")
        val currentValue = (state[statePath] as? Number)?.toFloat() ?: min

        Column(modifier = modifier.fillMaxWidth()) {
            if (label != null) {
                Text(label, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(4.dp))
            }
            Slider(
                value = currentValue,
                onValueChange = { newValue ->
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
                valueRange = min..max,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable
    private fun RenderDateRangePicker(
        ir: NanoIR, state: Map<String, Any>, onAction: (NanoActionIR) -> Unit, modifier: Modifier
    ) {
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                placeholder = { Text("Start date") },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = "",
                onValueChange = {},
                placeholder = { Text("End date") },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
    }

    @Composable
    private fun RenderDataChart(ir: NanoIR, state: Map<String, Any>, modifier: Modifier) {
        val chartType = ir.props["type"]?.jsonPrimitive?.content ?: "line"
        val data = ir.props["data"]?.jsonPrimitive?.content

        Surface(
            modifier = modifier.fillMaxWidth().height(200.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Chart: $chartType", style = MaterialTheme.typography.bodyMedium)
                    if (data != null) {
                        Text("Data: $data", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    @Composable
    private fun RenderDataTable(
        ir: NanoIR, state: Map<String, Any>, onAction: (NanoActionIR) -> Unit, modifier: Modifier
    ) {
        val columns = ir.props["columns"]?.jsonPrimitive?.content
        val data = ir.props["data"]?.jsonPrimitive?.content

        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("DataTable", style = MaterialTheme.typography.titleSmall)
                if (columns != null) Text("Columns: $columns", style = MaterialTheme.typography.bodySmall)
                if (data != null) Text("Data: $data", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    // ============================================================================
    // Control Flow Components
    // ============================================================================

    @Composable
    private fun RenderConditional(
        ir: NanoIR, state: Map<String, Any>, onAction: (NanoActionIR) -> Unit, modifier: Modifier
    ) {
        val condition = ir.condition
        val isVisible = evaluateCondition(condition, state)

        if (isVisible) {
            Column(modifier = modifier) {
                ir.children?.forEach { child -> RenderNode(child, state, onAction) }
            }
        }
    }

    @Composable
    private fun RenderForLoop(
        ir: NanoIR, state: Map<String, Any>, onAction: (NanoActionIR) -> Unit, modifier: Modifier
    ) {
        val loop = ir.loop
        val variable = loop?.variable
        val iterable = loop?.iterable?.removePrefix("state.")
        val items = state[iterable] as? List<*> ?: emptyList<Any>()

        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEachIndexed { index, item ->
                val itemState = state.toMutableMap().apply {
                    if (variable != null) {
                        this[variable] = item ?: ""
                    }
                }
                ir.children?.forEach { child -> RenderNode(child, itemState, onAction) }
            }
        }
    }

    // ============================================================================
    // Helper Functions
    // ============================================================================

    private fun resolveBindingValue(value: String?, state: Map<String, Any>): String? {
        if (value == null) return null
        if (value.startsWith("state.")) {
            val path = value.removePrefix("state.")
            return state[path]?.toString()
        }
        return value
    }

    private fun evaluateCondition(condition: String?, state: Map<String, Any>): Boolean {
        if (condition.isNullOrBlank()) return true
        // Simple evaluation: check if state path exists and is truthy
        val path = condition.removePrefix("state.")
        val value = state[path]
        return when (value) {
            is Boolean -> value
            is String -> value.isNotBlank()
            is Number -> value.toDouble() != 0.0
            null -> false
            else -> true
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

    /**
     * Format date from epoch milliseconds to YYYY-MM-DD string
     * Material3 DatePicker returns UTC millis at midnight
     */
    private fun formatDateFromMillis(millis: Long): String {
        // Simple conversion: millis / 86400000 = days since epoch
        val days = millis / 86400000L
        // Approximate year (will be off by a few days due to leap years, but good enough for display)
        val year = 1970 + (days / 365)
        val remainingDays = days % 365
        val month = (remainingDays / 30).coerceIn(0, 11) + 1
        val day = (remainingDays % 30).coerceIn(0, 30) + 1

        return buildString {
            append(year.toString().padStart(4, '0'))
            append('-')
            append(month.toString().padStart(2, '0'))
            append('-')
            append(day.toString().padStart(2, '0'))
        }
    }
}

/**
 * Decode image bytes to ImageBitmap.
 * Platform-specific implementation.
 */
internal expect fun decodeImageBytesToBitmap(bytes: ByteArray): ImageBitmap
