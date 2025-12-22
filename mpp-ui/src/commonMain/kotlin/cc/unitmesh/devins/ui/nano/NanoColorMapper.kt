package cc.unitmesh.devins.ui.nano

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Utility for mapping color names to MaterialTheme colors in Compose.
 *
 * Provides consistent color mapping across all NanoUI Compose components.
 */
object NanoColorMapper {

    @Composable
    fun mapTextColor(colorName: String?): Color = when (colorName?.lowercase()) {
        "primary" -> MaterialTheme.colorScheme.primary
        "secondary" -> MaterialTheme.colorScheme.secondary
        "green" -> MaterialTheme.colorScheme.tertiary
        "red" -> MaterialTheme.colorScheme.error
        "blue" -> MaterialTheme.colorScheme.secondary
        "yellow", "orange" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    @Composable
    fun mapBackgroundColor(colorName: String?): Color = when (colorName?.lowercase()) {
        "green" -> MaterialTheme.colorScheme.tertiaryContainer
        "red" -> MaterialTheme.colorScheme.errorContainer
        "blue" -> MaterialTheme.colorScheme.secondaryContainer
        "yellow", "orange" -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    @Composable
    fun mapContainerColors(colorName: String?): Pair<Color, Color> = when (colorName?.lowercase()) {
        "green" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        "red" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        "blue" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        "yellow", "orange" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
}

