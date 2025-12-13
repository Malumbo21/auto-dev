package cc.unitmesh.devins.ui.compose.theme

import androidx.compose.ui.graphics.Color

/**
 * AutoDev Design System - Color Palette
 *
 * 基于「霓虹暗夜」与「气韵流动」的视觉语言设计的色彩系统
 *
 * 设计原则：
 * 1. 虚空色阶 (Void) - 带微量蓝色的冷色调背景，营造深邃空间感
 * 2. 能量色阶 (Energy) - 区分用户意图（电光青）与 AI 响应（霓虹紫）
 * 3. 信号色阶 (Signal) - 高饱和度的状态指示色，确保清晰可辨
 */
object AutoDevColors {
    // ========================================================================
    // Void Scale - 虚空色阶（带微量蓝色的冷色调背景）
    // ========================================================================
    object Void {
        val bg = Color(0xFF0B0E14)           // 全局底层背景
        val surface1 = Color(0xFF151922)     // 侧边栏、面板
        val surface2 = Color(0xFF1F2430)     // 悬停、输入框、代码块
        val surface3 = Color(0xFF2A3040)     // 边框、分割线
        val overlay = Color(0x800B0E14)      // 模态遮罩 (50% opacity)
        val surfaceElevated = Color(0xFF181D26) // 浮层背景（Omnibar 等）
        val surfaceHover = Color(0xFF242A36)  // 悬停背景
        val border = Color(0xFF2A3040)        // 边框颜色

        // 亮色模式的虚空色阶
        val lightBg = Color(0xFFF8FAFC)      // 亮色背景 - 冷白
        val lightSurface1 = Color(0xFFFFFFFF) // 卡片表面
        val lightSurface2 = Color(0xFFF1F5F9) // 悬停状态
        val lightSurface3 = Color(0xFFE2E8F0) // 边框
    }

    // ========================================================================
    // Energy Scale - 能量色阶（人机边界）
    // ========================================================================
    object Energy {
        // 电光青 - 用户意图、用户操作
        val xiu = Color(0xFF00F3FF)
        val xiuHover = Color(0xFF33F5FF)
        val xiuDim = Color(0x4000F3FF)        // 25% opacity - 光晕
        val primary = xiu                      // 主色别名

        // 霓虹紫 - AI 生成内容
        val ai = Color(0xFFD946EF)
        val aiHover = Color(0xFFE066F5)
        val aiDim = Color(0x40D946EF)         // 25% opacity - 光晕

        // 亮色模式下的能量色（略微降低亮度以保证对比度）
        val xiuLight = Color(0xFF00BCD4)      // 深青色
        val aiLight = Color(0xFFAB47BC)       // 深紫色
    }

    // ========================================================================
    // Signal Scale - 信号色阶（状态指示）
    // ========================================================================
    object Signal {
        val success = Color(0xFF00E676)       // 高亮绿
        val error = Color(0xFFFF1744)         // 高亮红
        val warn = Color(0xFFFFEA00)          // 赛博黄
        val info = Color(0xFF2196F3)          // 信息蓝

        // 亮色模式下的信号色（略微加深以保证对比度）
        val successLight = Color(0xFF00C853)
        val errorLight = Color(0xFFD50000)
        val warnLight = Color(0xFFFFD600)
        val infoLight = Color(0xFF1976D2)

        // 信号色的淡色背景（用于状态提示背景）
        val successBg = Color(0x1A00E676)     // 10% opacity
        val errorBg = Color(0x1AFF1744)
        val warnBg = Color(0x1AFFEA00)
        val infoBg = Color(0x1A2196F3)
    }

    // ========================================================================
    // Text Colors - 文本颜色
    // ========================================================================
    object Text {
        // 暗色模式文本
        val primary = Color(0xFFF5F5F5)       // 主文本
        val secondary = Color(0xFFB0BEC5)     // 辅助文本
        val tertiary = Color(0xFF78909C)      // 第三级文本
        val quaternary = Color(0xFF546E7A)    // 第四级文本（最弱）
        val inverse = Color(0xFF0B0E14)       // 反色文本

        // 亮色模式文本
        val lightPrimary = Color(0xFF1E293B)  // 主文本
        val lightSecondary = Color(0xFF475569) // 辅助文本
        val lightTertiary = Color(0xFF94A3B8) // 第三级文本
    }

    // ========================================================================
    // Syntax Highlighting Colors - 代码高亮专用颜色
    // ========================================================================
    object Syntax {
        // 深色主题代码高亮
        object Dark {
            val agent = Energy.xiu            // Agent 提及 (@) - 电光青
            val command = Signal.success      // 命令 (/) - 高亮绿
            val variable = Energy.ai          // 变量 ($) - 霓虹紫
            val keyword = Color(0xFFFF9800)   // 关键字 - 橙色
            val string = Color(0xFF00E676)    // 字符串 - 高亮绿
            val number = Signal.info          // 数字 - 信息蓝
            val comment = Text.tertiary       // 注释 - 第三级文本
            val identifier = Text.secondary   // 标识符
        }

        // 亮色主题代码高亮
        object Light {
            val agent = Energy.xiuLight       // Agent 提及 (@)
            val command = Signal.successLight // 命令 (/)
            val variable = Energy.aiLight     // 变量 ($)
            val keyword = Color(0xFFE65100)   // 关键字 - 深橙色
            val string = Signal.successLight  // 字符串
            val number = Signal.infoLight     // 数字
            val comment = Text.lightTertiary  // 注释
            val identifier = Text.lightSecondary // 标识符
        }
    }

    // ========================================================================
    // Xiuper Brand - speed mark palette (X=>)
    // NOTE: Keep launch/splash visuals token-driven; do not hardcode colors in UI.
    // ========================================================================
    object Xiuper {
        // Darker, warmer void for the splash background (distinct from AutoDev Void.bg)
        val bg = Color(0xFF07060A)
        val bg2 = Color(0xFF0B1020)

        // Speed mark colors
        val markHot = Color(0xFFFF4D00)        // neon orange
        val markCool = Color(0xFF7C3AED)       // electric violet

        // Glow helpers
        val markHotDim = Color(0x40FF4D00)     // 25% opacity
        val markCoolDim = Color(0x407C3AED)    // 25% opacity

        // Text on splash (off-white)
        val text = Text.primary
        val textSecondary = Text.secondary
    }

    // ========================================================================
    // Diff Colors - Diff 显示专用颜色
    // ========================================================================
    data class DiffColors(
        val addedBg: Color,
        val addedBorder: Color,
        val deletedBg: Color,
        val deletedBorder: Color,
        val lineNumber: Color
    )

    object Diff {
        // 深色主题
        val Dark = DiffColors(
            addedBg = Signal.success.copy(alpha = 0.15f),
            addedBorder = Signal.success.copy(alpha = 0.3f),
            deletedBg = Signal.error.copy(alpha = 0.15f),
            deletedBorder = Signal.error.copy(alpha = 0.3f),
            lineNumber = Text.tertiary
        )

        // 亮色主题
        val Light = DiffColors(
            addedBg = Signal.successLight.copy(alpha = 0.1f),
            addedBorder = Signal.successLight.copy(alpha = 0.3f),
            deletedBg = Signal.errorLight.copy(alpha = 0.1f),
            deletedBorder = Signal.errorLight.copy(alpha = 0.3f),
            lineNumber = Text.lightTertiary
        )
    }

    // ========================================================================
    // Legacy Compatibility - 向后兼容的旧色阶别名
    // 所有旧色阶已映射到新的设计系统
    // ========================================================================

    /**
     * Indigo -> 电光青色阶 (主色)
     * 原本的靛蓝色已替换为电光青
     */
    object Indigo {
        val c50 = Color(0xFFE0FCFF)           // 最浅
        val c100 = Color(0xFFB3F5FF)
        val c200 = Color(0xFF80EEFF)
        val c300 = Energy.xiu                 // 暗黑模式主色 -> 电光青 #00F3FF
        val c400 = Energy.xiuHover            // 暗黑模式悬停 -> #33F5FF
        val c500 = Color(0xFF00D4E0)
        val c600 = Energy.xiuLight            // 亮色模式主色 -> #00BCD4
        val c700 = Color(0xFF0097A7)          // 亮色模式悬停
        val c800 = Color(0xFF00838F)
        val c900 = Color(0xFF006064)          // 最深
    }

    /**
     * Cyan -> 霓虹紫色阶 (AI 辅色)
     * 原本的青色已替换为霓虹紫，用于区分 AI 生成内容
     */
    object Cyan {
        val c50 = Color(0xFFFCE4F6)           // 最浅
        val c100 = Color(0xFFF8BBE8)
        val c200 = Color(0xFFF48FDB)
        val c300 = Color(0xFFEE63CE)
        val c400 = Energy.ai                  // 暗黑模式辅色 -> 霓虹紫 #D946EF
        val c500 = Energy.aiLight             // 亮色模式辅色 -> #AB47BC
        val c600 = Color(0xFF9C27B0)
        val c700 = Color(0xFF7B1FA2)
        val c800 = Color(0xFF6A1B9A)
        val c900 = Color(0xFF4A148C)          // 最深
    }

    /**
     * Neutral -> 虚空灰色阶
     * 映射到带冷色调的虚空色阶
     */
    object Neutral {
        val c50 = Void.lightBg                // #F8FAFC 亮色模式背景
        val c100 = Void.lightSurface2         // #F1F5F9
        val c200 = Void.lightSurface3         // #E2E8F0 亮色模式边框
        val c300 = Text.secondary             // #B0BEC5 暗黑模式辅文本
        val c400 = Text.tertiary              // #78909C
        val c500 = Color(0xFF607D8B)
        val c600 = Color(0xFF546E7A)
        val c700 = Void.surface3              // #2A3040 暗黑模式边框
        val c800 = Void.surface1              // #151922 暗黑模式卡片
        val c900 = Void.bg                    // #0B0E14 暗黑模式背景
    }

    /**
     * Green -> 高亮绿色阶 (成功状态)
     */
    object Green {
        val c50 = Color(0xFFE8F5E9)           // 淡背景
        val c100 = Color(0xFFC8E6C9)
        val c200 = Color(0xFFA5D6A7)
        val c300 = Color(0xFF81C784)
        val c400 = Signal.success             // 暗黑模式成功色 -> #00E676
        val c500 = Signal.success             // #00E676
        val c600 = Signal.successLight        // 亮色模式成功色 -> #00C853
        val c700 = Color(0xFF388E3C)
        val c800 = Color(0xFF2E7D32)
        val c900 = Color(0xFF1B5E20)
    }

    /**
     * Amber -> 赛博黄色阶 (警告状态)
     */
    object Amber {
        val c50 = Color(0xFFFFFDE7)           // 淡背景
        val c100 = Color(0xFFFFF9C4)
        val c200 = Color(0xFFFFF59D)
        val c300 = Signal.warn                // 暗黑模式警告色 -> #FFEA00
        val c400 = Signal.warn                // #FFEA00
        val c500 = Signal.warnLight           // 亮色模式警告色 -> #FFD600
        val c600 = Color(0xFFFFC400)
        val c700 = Color(0xFFFFAB00)
        val c800 = Color(0xFFFF8F00)
        val c900 = Color(0xFFFF6F00)
    }

    /**
     * Red -> 高亮红色阶 (错误状态)
     */
    object Red {
        val c50 = Color(0xFFFFEBEE)           // 淡背景
        val c100 = Color(0xFFFFCDD2)
        val c200 = Color(0xFFEF9A9A)
        val c300 = Color(0xFFE57373)
        val c400 = Signal.error               // 暗黑模式错误色 -> #FF1744
        val c500 = Signal.error               // #FF1744
        val c600 = Signal.errorLight          // 亮色模式错误色 -> #D50000
        val c700 = Color(0xFFC62828)
        val c800 = Color(0xFFB71C1C)
        val c900 = Color(0xFF8E0000)
    }

    /**
     * Blue -> 信息蓝色阶 (信息状态)
     */
    object Blue {
        val c50 = Color(0xFFE3F2FD)           // 淡背景
        val c100 = Color(0xFFBBDEFB)
        val c200 = Color(0xFF90CAF9)
        val c300 = Signal.info                // 暗黑模式信息色 -> #2196F3
        val c400 = Signal.info                // #2196F3
        val c500 = Signal.info                // #2196F3
        val c600 = Signal.infoLight           // 亮色模式信息色 -> #1976D2
        val c700 = Color(0xFF1565C0)
        val c800 = Color(0xFF0D47A1)
        val c900 = Color(0xFF0A3D91)
    }
}
