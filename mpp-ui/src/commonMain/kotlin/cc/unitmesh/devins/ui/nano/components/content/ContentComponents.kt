package cc.unitmesh.devins.ui.nano.components.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.devins.ui.nano.ComposeNodeContext
import cc.unitmesh.devins.ui.nano.NanoColorMapper
import cc.unitmesh.devins.ui.nano.NanoPropsResolver
import cc.unitmesh.devins.ui.nano.parseMarkdownInline
import cc.unitmesh.xuiper.ir.booleanProp
import cc.unitmesh.xuiper.ir.stringProp
import cc.unitmesh.xuiper.props.NanoSizeMapper
import cc.unitmesh.xuiper.render.stateful.NanoNodeRenderer

/**
 * Content components for NanoUI Compose renderer.
 * Includes: Text, Badge, Icon, Divider, Code, Link, Blockquote
 *
 * All components use the unified NanoNodeContext interface.
 */
object ContentComponents {

    val textRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderText(ctx) }
    }

    val badgeRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderBadge(ctx) }
    }

    val iconRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderIcon(ctx) }
    }

    val dividerRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderDivider(ctx) }
    }

    val codeRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderCode(ctx) }
    }

    val linkRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderLink(ctx) }
    }

    val blockquoteRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderBlockquote(ctx) }
    }

    @Composable
    fun RenderText(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val content = NanoPropsResolver.resolveString(ir, "content", ctx.state)
        val style = ir.stringProp("style")
        val enableMarkdown = ir.stringProp("markdown") != "false"

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
            val annotatedContent = parseMarkdownInline(
                text = content,
                baseStyle = textStyle,
                linkColor = MaterialTheme.colorScheme.primary,
                codeBackground = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(text = annotatedContent, style = textStyle, modifier = ctx.payload)
        } else {
            Text(text = content, style = textStyle, modifier = ctx.payload)
        }
    }

    @Composable
    fun RenderBadge(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val text = NanoPropsResolver.resolveString(ir, "text", ctx.state)
        val colorName = ir.stringProp("color")

        val (bgColor, contentColor) = NanoColorMapper.mapContainerColors(colorName)

        Surface(
            modifier = ctx.payload,
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
    fun RenderIcon(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val iconName = ir.stringProp("name") ?: ""
        val sizeName = ir.stringProp("size")
        val colorName = ir.stringProp("color")

        val normalizedName = iconName.trim().lowercase().replace('_', '-')
        val iconVector = mapIconName(normalizedName)
        val iconSize = NanoSizeMapper.parseIconSize(sizeName).dp
        val iconColor = NanoColorMapper.mapTextColor(colorName)

        Icon(
            imageVector = iconVector,
            contentDescription = iconName,
            modifier = ctx.payload.size(iconSize),
            tint = iconColor
        )
    }

    @Composable
    fun RenderDivider(ctx: ComposeNodeContext) {
        HorizontalDivider(ctx.payload.padding(vertical = 8.dp))
    }

    @Composable
    fun RenderCode(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val content = NanoPropsResolver.resolveString(ir, "content", ctx.state)
        val colorName = ir.stringProp("color")
        val bgColorName = ir.stringProp("bgColor")

        val textColor = NanoColorMapper.mapTextColor(colorName)
        val backgroundColor = NanoColorMapper.mapBackgroundColor(bgColorName)

        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            color = textColor,
            modifier = ctx.payload
                .background(color = backgroundColor, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }

    @Composable
    fun RenderLink(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val content = NanoPropsResolver.resolveString(ir, "content", ctx.state)
        val rawUrl = ir.stringProp("url") ?: ""
        val resolvedUrl = NanoPropsResolver.resolveString(ir, "url", ctx.state)
        val colorName = ir.stringProp("color")
        val showIcon = ir.booleanProp("showIcon") ?: false

        val linkColor = NanoColorMapper.mapTextColor(colorName) ?: MaterialTheme.colorScheme.primary

        Row(
            modifier = ctx.payload.clickable { println("Opening link: $resolvedUrl") },
            verticalAlignment = Alignment.CenterVertically,
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

    @Composable
    fun RenderBlockquote(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val content = NanoPropsResolver.resolveString(ir, "content", ctx.state)
        val rawAttribution = ir.stringProp("attribution")
        val attribution = rawAttribution?.let { NanoPropsResolver.resolveString(ir, "attribution", ctx.state) }
        val variant = ir.stringProp("variant")

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
            modifier = ctx.payload
                .background(color = backgroundColor, shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .background(color = borderColor, shape = RoundedCornerShape(2.dp))
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
                    text = "-- $attribution",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }

    private fun mapIconName(normalizedName: String): ImageVector {
        return when (normalizedName) {
            "flight", "airplane", "plane" -> Icons.Default.Flight
            "hotel", "bed" -> Icons.Default.Hotel
            "restaurant", "food", "dining" -> Icons.Default.Restaurant
            "location-dot", "location-on", "map-pin", "pin", "pin-drop", "flag" -> Icons.Default.LocationOn
            "location-arrow", "near-me" -> Icons.Default.NearMe
            "my-location", "crosshair", "crosshairs" -> Icons.Default.MyLocation
            "place", "attractions", "location" -> Icons.Default.Place
            "map" -> Icons.Default.Map
            "compass" -> Icons.Default.Explore
            "globe", "globe-americas", "globe-asia", "globe-europe" -> Icons.Default.Public
            "route" -> Icons.Default.AltRoute
            "calendar", "calendar-days", "event" -> Icons.Default.Event
            "clock", "time", "schedule" -> Icons.Default.Schedule
            "weather", "sun", "sunny" -> Icons.Default.WbSunny
            "moon" -> Icons.Default.NightsStay
            "cloud", "cloudy" -> Icons.Default.Cloud
            "umbrella", "rain" -> Icons.Default.Umbrella
            "wind" -> Icons.Default.Air
            "snowflake", "snow" -> Icons.Default.AcUnit
            "bolt", "lightning" -> Icons.Default.Bolt
            "droplet", "drop", "water" -> Icons.Default.WaterDrop
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
            "link" -> Icons.Default.Link
            "external-link", "open-in-new" -> Icons.Default.OpenInNew
            "attachment", "attach", "paperclip" -> Icons.Default.AttachFile
            "ticket" -> Icons.Default.ConfirmationNumber
            "id-card", "id", "badge" -> Icons.Default.Badge
            "search", "magnifying-glass" -> Icons.Default.Search
            "menu", "bars" -> Icons.Default.Menu
            "settings", "gear", "cog" -> Icons.Default.Settings
            "sliders", "tune" -> Icons.Default.Tune
            "filter" -> Icons.Default.FilterList
            "sort" -> Icons.Default.Sort
            "ellipsis", "more" -> Icons.Default.MoreHoriz
            "ellipsis-vertical", "more-vertical" -> Icons.Default.MoreVert
            "arrow-right" -> Icons.AutoMirrored.Filled.ArrowForward
            "arrow-left" -> Icons.AutoMirrored.Filled.ArrowBack
            "arrow-up" -> Icons.Default.KeyboardArrowUp
            "arrow-down" -> Icons.Default.KeyboardArrowDown
            "chevron-right", "angle-right" -> Icons.Default.ChevronRight
            "chevron-left", "angle-left" -> Icons.Default.ChevronLeft
            "info", "circle-info" -> Icons.Default.Info
            "warning", "triangle-exclamation" -> Icons.Default.Warning
            "error", "circle-xmark", "ban" -> Icons.Default.Error
            "help", "circle-question", "question" -> Icons.Default.Help
            "home", "house" -> Icons.Default.Home
            "person", "user" -> Icons.Default.Person
            "email", "mail", "envelope" -> Icons.Default.Email
            "phone" -> Icons.Default.Phone
            "camera" -> Icons.Default.CameraAlt
            "image", "photo" -> Icons.Default.Image
            "star", "favorite" -> Icons.Default.Star
            else -> Icons.Default.Info
        }
    }
}
