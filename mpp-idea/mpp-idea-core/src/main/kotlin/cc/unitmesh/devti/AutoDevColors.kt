package cc.unitmesh.devti

import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import java.awt.Color

/**
 * AutoDev Design System - IntelliJ IDEA Plugin Color Palette
 *
 * 基于「霓虹暗夜」与「气韵流动」的视觉语言设计的色彩系统
 * 与 mpp-ui/AutoDevColors.kt (Compose) 保持同步
 *
 * 设计原则：
 * 1. 虚空色阶 (Void) - 带微量蓝色的冷色调背景，营造深邃空间感
 * 2. 能量色阶 (Energy) - 区分用户意图（电光青）与 AI 响应（霓虹紫）
 * 3. 信号色阶 (Signal) - 高饱和度的状态指示色，确保清晰可辨
 */
object AutoDevColors {
    // ========================================================================
    // Signal Colors - 信号色阶（状态指示）
    // JBColor(lightColor, darkColor)
    // ========================================================================
    val COMPLETED_STATUS = JBColor(0x00C853, 0x00E676)   // Signal.success
    val FAILED_STATUS = JBColor(0xD50000, 0xFF1744)      // Signal.error
    val IN_PROGRESS_STATUS = JBColor(0x1976D2, 0x2196F3) // Signal.info
    val TODO_STATUS = JBColor(0x94A3B8, 0x78909C)        // Text.tertiary

    val USER_ROLE_BG = JBColor(Gray._240, Gray._10)

    // Text colors
    val COMPLETED_TEXT = JBColor(0x94A3B8, 0x78909C)     // Text.tertiary
    val FAILED_TEXT = JBColor(0xD50000, 0xFF1744)        // Signal.error
    val IN_PROGRESS_TEXT = JBColor(0x1976D2, 0x2196F3)   // Signal.info

    val SEPARATOR_BORDER = JBColor(0xE2E8F0, 0x2A3040)   // Void.lightSurface3 / Void.surface3
    val LINK_COLOR = JBColor(0x00BCD4, 0x00F3FF)         // Energy.xiuLight / Energy.xiu

    // ========================================================================
    // Diff and UI specific colors
    // ========================================================================
    val DIFF_NEW_LINE_COLOR_SHADOW = JBColor(0x3000E676, 0x3000E676) // Signal.success with alpha
    val DIFF_NEW_LINE_COLOR: Int = 0x3000E676
    val DELETION_INLAY_COLOR: JBColor = JBColor(0x30FF1744, 0x30FF1744) // Signal.error with alpha
    val REJECT_BUTTON_COLOR: JBColor = JBColor(Color(255, 23, 68, 153), Color(255, 23, 68, 153)) // Signal.error
    val ACCEPT_BUTTON_COLOR: JBColor = JBColor(0x00C853, 0x00E676) // Signal.success

    // Additional colors extracted from SingleFileDiffSketch
    val FILE_HOVER_COLOR = JBColor(0x00BCD4, 0x00F3FF)   // Energy.xiuLight / Energy.xiu
    val ADD_LINE_COLOR = JBColor(0x00C853, 0x00E676)     // Signal.success
    val REMOVE_LINE_COLOR = JBColor(0xD50000, 0xFF1744)  // Signal.error

    // ========================================================================
    // Execution result colors
    // ========================================================================
    val EXECUTION_SUCCESS_BACKGROUND = JBColor(Color(0, 230, 118, 26), Color(0, 230, 118, 26)) // Signal.successBg
    val EXECUTION_ERROR_BACKGROUND = JBColor(Color(255, 23, 68, 26), Color(255, 23, 68, 26))   // Signal.errorBg
    val EXECUTION_SUCCESS_BORDER = JBColor(0x00C853, 0x00E676)   // Signal.success
    val EXECUTION_RUNNING_BORDER = JBColor(0x1976D2, 0x2196F3)   // Signal.info
    val EXECUTION_WARNING_BORDER = JBColor(0xFFD600, 0xFFEA00)   // Signal.warn
    val EXECUTION_ERROR_BORDER = JBColor(0xD50000, 0xFF1744)     // Signal.error

    // ========================================================================
    // Loading panel colors
    // ========================================================================
    object LoadingPanel {
        // Background colors - 基于 Void 色阶
        val BACKGROUND = JBColor(Color(248, 250, 252), Color(11, 14, 20))   // Void.lightBg / Void.bg
        val FOREGROUND = JBColor(Color(30, 41, 59), Color(245, 245, 245))   // Text.lightPrimary / Text.primary
        val BORDER = JBColor(Color(226, 232, 240), Color(42, 48, 64))       // Void.lightSurface3 / Void.surface3

        // Progress bar colors - 基于 Energy 色阶
        val PROGRESS_COLOR = JBColor(Color(0, 188, 212), Color(0, 243, 255))     // Energy.xiuLight / Energy.xiu
        val PROGRESS_BACKGROUND = JBColor(Color(241, 245, 249), Color(31, 36, 48)) // Void.lightSurface2 / Void.surface2

        // Gradient colors - 基于 Energy 色阶
        val GRADIENT_COLOR1 = JBColor(Color(0, 188, 212, 50), Color(0, 243, 255, 50))   // Energy.xiu dim
        val GRADIENT_COLOR2 = JBColor(Color(171, 71, 188, 50), Color(217, 70, 239, 50)) // Energy.ai dim
    }
}
