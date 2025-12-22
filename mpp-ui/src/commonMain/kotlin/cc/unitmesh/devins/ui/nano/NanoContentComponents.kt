package cc.unitmesh.devins.ui.nano

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.xuiper.eval.evaluator.NanoExpressionEvaluator
import cc.unitmesh.xuiper.ir.NanoIR
import kotlinx.serialization.json.jsonPrimitive

/**
 * Content components for NanoUI Compose renderer.
 * Includes: Text, Image, Badge, Icon, Divider
 */
object NanoContentComponents {

    @Composable
    fun RenderText(ir: NanoIR, state: Map<String, Any>, modifier: Modifier) {
        val rawContent = NanoExpressionEvaluator.resolveStringProp(ir, "content", state)

        // Interpolate {state.xxx} or {state.xxx + 1} expressions in content
        val content = NanoExpressionEvaluator.interpolateText(rawContent, state)

        val style = ir.props["style"]?.jsonPrimitive?.content

        // Check if markdown parsing is enabled (default: true)
        val enableMarkdown = ir.props["markdown"]?.jsonPrimitive?.content != "false"

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

        if (enableMarkdown) {
            val annotatedContent = parseMarkdownInline(content, textStyle)
            Text(text = annotatedContent, style = textStyle, modifier = modifier)
        } else {
            Text(text = content, style = textStyle, modifier = modifier)
        }
    }

    @Composable
    fun RenderBadge(ir: NanoIR, state: Map<String, Any>, modifier: Modifier) {
        val rawText = NanoExpressionEvaluator.resolveStringProp(ir, "text", state)
        val text = NanoExpressionEvaluator.interpolateText(rawText, state)
        val colorName = ir.props["color"]?.jsonPrimitive?.content

        val (bgColor, contentColor) = when (colorName) {
            // Treat named colors as semantic intents so they can be themed.
            "green" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            "red" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
            "blue" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
            "yellow", "orange" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        }

        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(4.dp),
            color = bgColor,
            contentColor = contentColor
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = LocalContentColor.current,
                fontSize = 12.sp
            )
        }
    }

    @Composable
    fun RenderIcon(ir: NanoIR, modifier: Modifier) {
        val iconName = ir.props["name"]?.jsonPrimitive?.content ?: ""
        val sizeName = ir.props["size"]?.jsonPrimitive?.content
        val colorName = ir.props["color"]?.jsonPrimitive?.content

        val normalizedName = iconName.trim().lowercase().replace('_', '-')

        // Map icon names to Material Icons
        val iconVector: ImageVector = when (normalizedName) {
            // Travel / Places
            "flight", "airplane", "plane" -> Icons.Default.Flight
            "hotel", "bed" -> Icons.Default.Hotel
            "restaurant", "food", "dining" -> Icons.Default.Restaurant

            // Location / Navigation (FontAwesome/Lucide-style names)
            "location-dot", "location-on", "map-pin", "pin", "pin-drop", "flag" -> Icons.Default.LocationOn
            "location-arrow", "near-me" -> Icons.Default.NearMe
            "my-location", "crosshair", "crosshairs" -> Icons.Default.MyLocation
            "place", "attractions", "location" -> Icons.Default.Place
            "map" -> Icons.Default.Map
            "compass" -> Icons.Default.Explore
            "globe", "globe-americas", "globe-asia", "globe-europe" -> Icons.Default.Public
            "route" -> Icons.Default.AltRoute

            // Time / Calendar
            "calendar", "calendar-days", "event" -> Icons.Default.Event
            "clock", "time", "schedule" -> Icons.Default.Schedule

            // Weather
            "weather", "sun", "sunny" -> Icons.Default.WbSunny
            "moon" -> Icons.Default.NightsStay
            "cloud", "cloudy" -> Icons.Default.Cloud
            "cloud-sun", "partly-cloudy", "partly-cloudy-day" -> Icons.Default.Cloud
            "cloud-rain", "cloud-showers", "rainy" -> Icons.Default.Umbrella
            "wind" -> Icons.Default.Air
            "snowflake", "snow" -> Icons.Default.AcUnit
            "umbrella", "rain" -> Icons.Default.Umbrella
            "bolt", "lightning" -> Icons.Default.Bolt
            "droplet", "drop", "water" -> Icons.Default.WaterDrop

            // Common actions
            "check", "done" -> Icons.Default.Check
            "check-circle", "circle-check" -> Icons.Default.CheckCircle
            "xmark", "times", "close" -> Icons.Default.Close
            "trash", "delete" -> Icons.Default.Delete
            "pen", "pencil", "edit" -> Icons.Default.Edit
            "save", "floppy-disk" -> Icons.Default.Save
            "share" -> Icons.Default.Share
            "send", "paper-plane" -> Icons.Default.Send
            "refresh", "sync", "rotate" -> Icons.Default.Refresh
            "download" -> Icons.Default.Download
            "upload" -> Icons.Default.Upload
            "copy", "content-copy" -> Icons.Default.ContentCopy
            "paste", "content-paste" -> Icons.Default.ContentPaste
            "cut", "content-cut" -> Icons.Default.ContentCut
            "clipboard" -> Icons.Default.ContentPaste
            "link" -> Icons.Default.Link
            "external-link", "open-in-new" -> Icons.Default.OpenInNew
            "attachment", "attach", "paperclip" -> Icons.Default.AttachFile
            "ticket" -> Icons.Default.ConfirmationNumber
            "id-card", "id", "badge" -> Icons.Default.Badge

            // UI controls
            "search", "magnifying-glass" -> Icons.Default.Search
            "menu", "bars" -> Icons.Default.Menu
            "settings", "gear", "cog" -> Icons.Default.Settings
            "sliders", "tune" -> Icons.Default.Tune
            "filter" -> Icons.Default.FilterList
            "sort" -> Icons.Default.Sort
            "ellipsis", "more" -> Icons.Default.MoreHoriz
            "ellipsis-vertical", "more-vertical" -> Icons.Default.MoreVert

            // Arrows / Chevrons
            "arrow-right" -> Icons.AutoMirrored.Filled.ArrowForward
            "arrow-left" -> Icons.AutoMirrored.Filled.ArrowBack
            "arrow-up" -> Icons.Default.KeyboardArrowUp
            "arrow-down" -> Icons.Default.KeyboardArrowDown
            "chevron-right", "angle-right" -> Icons.Default.ChevronRight
            "chevron-left", "angle-left" -> Icons.Default.ChevronLeft
            "chevron-up", "angle-up" -> Icons.Default.KeyboardArrowUp
            "chevron-down", "angle-down" -> Icons.Default.KeyboardArrowDown
            "caret-up" -> Icons.Default.ArrowDropUp
            "caret-down" -> Icons.Default.ArrowDropDown
            "back", "arrow-back" -> Icons.AutoMirrored.Filled.ArrowBack
            "forward", "arrow-forward" -> Icons.AutoMirrored.Filled.ArrowForward

            // Status
            "info", "circle-info" -> Icons.Default.Info
            "warning", "triangle-exclamation" -> Icons.Default.Warning
            "error", "circle-xmark", "ban" -> Icons.Default.Error
            "help", "circle-question", "question" -> Icons.Default.Help

            // Misc
            "home", "house" -> Icons.Default.Home
            "person", "user" -> Icons.Default.Person
            "email", "mail", "envelope" -> Icons.Default.Email
            "phone" -> Icons.Default.Phone
            "camera" -> Icons.Default.CameraAlt
            "image", "photo" -> Icons.Default.Image
            "star", "favorite" -> Icons.Default.Star
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
            // Treat named colors as semantic intents so they can be themed.
            "green" -> MaterialTheme.colorScheme.tertiary
            "red" -> MaterialTheme.colorScheme.error
            "blue" -> MaterialTheme.colorScheme.secondary
            "yellow", "orange" -> MaterialTheme.colorScheme.primary
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

    /**
     * Renders inline code text with monospace font and background styling.
     * Supports content interpolation and optional color props.
     */
    @Composable
    fun RenderCode(ir: NanoIR, state: Map<String, Any>, modifier: Modifier) {
        val rawContent = NanoExpressionEvaluator.resolveStringProp(ir, "content", state)
        val content = NanoExpressionEvaluator.interpolateText(rawContent, state)
        val colorName = ir.props["color"]?.jsonPrimitive?.content
        val bgColorName = ir.props["bgColor"]?.jsonPrimitive?.content

        val textColor = when (colorName) {
            "green" -> MaterialTheme.colorScheme.tertiary
            "red" -> MaterialTheme.colorScheme.error
            "blue" -> MaterialTheme.colorScheme.secondary
            "yellow", "orange" -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        }

        val backgroundColor = when (bgColorName) {
            "green" -> MaterialTheme.colorScheme.tertiaryContainer
            "red" -> MaterialTheme.colorScheme.errorContainer
            "blue" -> MaterialTheme.colorScheme.secondaryContainer
            "yellow", "orange" -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }

        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            color = textColor,
            modifier = modifier
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }

    /**
     * Renders a clickable hyperlink with optional icon and styling.
     * Supports content interpolation.
     */
    @Composable
    fun RenderLink(ir: NanoIR, state: Map<String, Any>, modifier: Modifier) {
        val rawContent = NanoExpressionEvaluator.resolveStringProp(ir, "content", state)
        val content = NanoExpressionEvaluator.interpolateText(rawContent, state)
        val rawUrl = ir.props["url"]?.jsonPrimitive?.content ?: ""
        val resolvedUrl = NanoExpressionEvaluator.interpolateText(rawUrl, state)
        val colorName = ir.props["color"]?.jsonPrimitive?.content
        val showIcon = ir.props["showIcon"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

        val linkColor = when (colorName) {
            "primary" -> MaterialTheme.colorScheme.primary
            "secondary" -> MaterialTheme.colorScheme.secondary
            "green" -> MaterialTheme.colorScheme.tertiary
            "red" -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        }

        Row(
            modifier = modifier.clickable {
                println("Opening link: $resolvedUrl")
            },
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline
                )
            )
            if (showIcon) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "External link",
                    modifier = Modifier.size(14.dp),
                    tint = linkColor
                )
            }
        }
    }

    /**
     * Renders a blockquote with optional attribution.
     * Supports content interpolation and styling variants.
     */
    @Composable
    fun RenderBlockquote(ir: NanoIR, state: Map<String, Any>, modifier: Modifier) {
        val rawContent = NanoExpressionEvaluator.resolveStringProp(ir, "content", state)
        val content = NanoExpressionEvaluator.interpolateText(rawContent, state)
        val rawAttribution = ir.props["attribution"]?.jsonPrimitive?.content
        val attribution = rawAttribution?.let { NanoExpressionEvaluator.interpolateText(it, state) }
        val variant = ir.props["variant"]?.jsonPrimitive?.content

        val borderColor = when (variant) {
            "warning" -> MaterialTheme.colorScheme.error
            "success" -> MaterialTheme.colorScheme.tertiary
            "info" -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        }

        val backgroundColor = when (variant) {
            "warning" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            "success" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            "info" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }

        Column(
            modifier = modifier
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                )
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .background(
                        color = borderColor,
                        shape = RoundedCornerShape(2.dp)
                    )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )

            if (!attribution.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "— $attribution",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

