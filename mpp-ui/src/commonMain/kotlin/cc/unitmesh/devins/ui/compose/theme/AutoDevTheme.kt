package cc.unitmesh.devins.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * AutoDev 暗色主题配色方案
 * 基于「霓虹暗夜」视觉语言 - 虚空色阶 + 能量色阶
 */
private val DarkColorScheme =
    darkColorScheme(
        // 主色 - 电光青（用户意图）
        primary = AutoDevColors.Energy.xiu,
        onPrimary = AutoDevColors.Void.bg,
        primaryContainer = AutoDevColors.Void.surface2,
        onPrimaryContainer = AutoDevColors.Energy.xiu,
        
        // 辅色 - 霓虹紫（AI 生成）
        secondary = AutoDevColors.Energy.ai,
        onSecondary = AutoDevColors.Void.bg,
        secondaryContainer = AutoDevColors.Void.surface2,
        onSecondaryContainer = AutoDevColors.Energy.ai,
        
        // 第三色 - 成功绿
        tertiary = AutoDevColors.Signal.success,
        onTertiary = AutoDevColors.Void.bg,
        tertiaryContainer = AutoDevColors.Signal.successBg,
        onTertiaryContainer = AutoDevColors.Signal.success,
        
        // 背景和表面 - 虚空色阶
        background = AutoDevColors.Void.bg,
        onBackground = AutoDevColors.Text.primary,
        surface = AutoDevColors.Void.surface1,
        onSurface = AutoDevColors.Text.primary,
        surfaceVariant = AutoDevColors.Void.surface2,
        onSurfaceVariant = AutoDevColors.Text.secondary,
        
        // 错误 - 高亮红
        error = AutoDevColors.Signal.error,
        onError = AutoDevColors.Void.bg,
        errorContainer = AutoDevColors.Signal.errorBg,
        onErrorContainer = AutoDevColors.Signal.error,
        
        // 轮廓 - 虚空边框色
        outline = AutoDevColors.Void.surface3,
        outlineVariant = AutoDevColors.Void.surface2,
        
        // 反向表面
        inverseSurface = AutoDevColors.Text.primary,
        inverseOnSurface = AutoDevColors.Void.bg,
        inversePrimary = AutoDevColors.Energy.xiuLight,
        
        // 剪贴薄
        scrim = AutoDevColors.Void.overlay,
    )

/**
 * AutoDev 亮色主题配色方案
 * 保持能量色阶的核心特征，适配亮色背景
 */
private val LightColorScheme =
    lightColorScheme(
        // 主色 - 电光青（亮色版本）
        primary = AutoDevColors.Energy.xiuLight,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE0F7FA),
        onPrimaryContainer = Color(0xFF006064),
        
        // 辅色 - 霓虹紫（亮色版本）
        secondary = AutoDevColors.Energy.aiLight,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF3E5F5),
        onSecondaryContainer = Color(0xFF4A148C),
        
        // 第三色 - 成功绿
        tertiary = AutoDevColors.Signal.successLight,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE8F5E9),
        onTertiaryContainer = Color(0xFF1B5E20),
        
        // 背景和表面 - 亮色虚空
        background = AutoDevColors.Void.lightBg,
        onBackground = AutoDevColors.Text.lightPrimary,
        surface = AutoDevColors.Void.lightSurface1,
        onSurface = AutoDevColors.Text.lightPrimary,
        surfaceVariant = AutoDevColors.Void.lightSurface2,
        onSurfaceVariant = AutoDevColors.Text.lightSecondary,
        
        // 错误 - 高亮红（亮色版本）
        error = AutoDevColors.Signal.errorLight,
        onError = Color.White,
        errorContainer = Color(0xFFFFEBEE),
        onErrorContainer = Color(0xFFB71C1C),
        
        // 轮廓 - 亮色边框
        outline = AutoDevColors.Void.lightSurface3,
        outlineVariant = Color(0xFFE2E8F0),
        
        // 反向表面
        inverseSurface = AutoDevColors.Void.bg,
        inverseOnSurface = AutoDevColors.Text.primary,
        inversePrimary = AutoDevColors.Energy.xiu,
        
        // 剪贴薄
        scrim = Color(0x80000000),
    )

/**
 * AutoDev 主题
 * 支持白天模式、夜间模式和跟随系统
 */
@Composable
fun AutoDevTheme(
    themeMode: ThemeManager.ThemeMode = ThemeManager.currentTheme,
    content: @Composable () -> Unit
) {
    val systemInDarkTheme = isSystemInDarkTheme()

    // 根据主题模式决定是否使用深色主题
    val darkTheme =
        when (themeMode) {
            ThemeManager.ThemeMode.LIGHT -> false
            ThemeManager.ThemeMode.DARK -> true
            ThemeManager.ThemeMode.SYSTEM -> systemInDarkTheme
        }

    val colorScheme =
        if (darkTheme) {
            DarkColorScheme
        } else {
            LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

/**
 * 向后兼容的旧版 API
 */
@Composable
fun AutoDevTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val themeMode =
        if (darkTheme) {
            ThemeManager.ThemeMode.DARK
        } else {
            ThemeManager.ThemeMode.LIGHT
        }

    AutoDevTheme(themeMode = themeMode, content = content)
}
