package cc.unitmesh.devins.theme

/**
 * AutoDev Design System - Color System Interface
 *
 * 定义跨平台的颜色系统抽象，各平台可以根据自己的 UI 框架实现
 * 
 * 设计原则：
 * 1. 虚空色阶 (Void) - 背景和表面
 * 2. 能量色阶 (Energy) - 主色和辅色
 * 3. 信号色阶 (Signal) - 状态指示
 * 4. 向后兼容 - 保留旧的色阶别名
 */
interface ColorSystem {
    // Core color scales
    val void: VoidScale
    val energy: EnergyScale
    val signal: SignalScale
    val text: TextScale
    val syntax: SyntaxScale
    
    // Legacy compatibility - 向后兼容的色阶
    val indigo: ColorScale
    val cyan: ColorScale
    val neutral: ColorScale
    val green: ColorScale
    val amber: ColorScale
    val red: ColorScale
    val blue: ColorScale
}

/**
 * 虚空色阶 - 背景和表面
 */
interface VoidScale {
    val bg: Any  // 全局底层背景
    val surface1: Any  // 侧边栏、面板
    val surface2: Any  // 悬停、输入框
    val surface3: Any  // 边框、分割线
    val overlay: Any  // 模态遮罩
    val surfaceElevated: Any  // 浮层背景
    val surfaceHover: Any  // 悬停背景
    val border: Any  // 边框颜色
    
    // Light mode
    val lightBg: Any
    val lightSurface1: Any
    val lightSurface2: Any
    val lightSurface3: Any
}

/**
 * 能量色阶 - 主色和辅色
 */
interface EnergyScale {
    val xiu: Any  // 电光青 - 用户操作
    val xiuHover: Any
    val xiuDim: Any
    val primary: Any  // 主色别名
    
    val ai: Any  // 霓虹紫 - AI 内容
    val aiHover: Any
    val aiDim: Any
    
    // Light mode
    val xiuLight: Any
    val aiLight: Any
}

/**
 * 信号色阶 - 状态指示
 */
interface SignalScale {
    val success: Any  // 成功
    val error: Any  // 错误
    val warn: Any  // 警告
    val info: Any  // 信息
    
    // Light mode
    val successLight: Any
    val errorLight: Any
    val warnLight: Any
    val infoLight: Any
    
    // Background colors
    val successBg: Any
    val errorBg: Any
    val warnBg: Any
    val infoBg: Any
}

/**
 * 文本色阶
 */
interface TextScale {
    val primary: Any
    val secondary: Any
    val tertiary: Any
    val quaternary: Any
    val inverse: Any
    
    // Light mode
    val lightPrimary: Any
    val lightSecondary: Any
    val lightTertiary: Any
}

/**
 * 语法高亮色阶
 */
interface SyntaxScale {
    val agent: Any
    val command: Any
    val variable: Any
    val keyword: Any
    val string: Any
    val number: Any
    val comment: Any
    val identifier: Any
}

/**
 * 通用色阶 (c50-c900)
 * 用于向后兼容旧的颜色引用
 */
interface ColorScale {
    val c50: Any
    val c100: Any
    val c200: Any
    val c300: Any
    val c400: Any
    val c500: Any
    val c600: Any
    val c700: Any
    val c800: Any
    val c900: Any
}

