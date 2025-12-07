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

// ============================================================================
// Void Scale - 虚空色阶（带微量蓝色的冷色调背景）
// ============================================================================

/**
 * 虚空色阶 - 暗色模式的基础背景色
 * 带有微量蓝色的冷色调，营造深邃空间感
 */
export const void_ = {
  bg: '#0B0E14',           // 全局底层背景
  surface1: '#151922',     // 侧边栏、面板
  surface2: '#1F2430',     // 悬停、输入框、代码块
  surface3: '#2A3040',     // 边框、分割线
  overlay: 'rgba(11, 14, 20, 0.8)',  // 模态遮罩
  
  // 亮色模式的虚空色阶
  lightBg: '#F8FAFC',      // 亮色背景 - 冷白
  lightSurface1: '#FFFFFF', // 卡片表面
  lightSurface2: '#F1F5F9', // 悬停状态
  lightSurface3: '#E2E8F0', // 边框
} as const;

// ============================================================================
// Energy Scale - 能量色阶（人机边界）
// ============================================================================

/**
 * 能量色阶 - 区分用户意图与 AI 响应
 */
export const energy = {
  // 电光青 - 用户意图、用户操作
  xiu: '#00F3FF',
  xiuHover: '#33F5FF',
  xiuDim: 'rgba(0, 243, 255, 0.25)',   // 25% opacity - 光晕
  
  // 霓虹紫 - AI 生成内容
  ai: '#D946EF',
  aiHover: '#E066F5',
  aiDim: 'rgba(217, 70, 239, 0.25)',   // 25% opacity - 光晕
  
  // 亮色模式下的能量色（略微降低亮度以保证对比度）
  xiuLight: '#00BCD4',     // 深青色
  aiLight: '#AB47BC',      // 深紫色
} as const;

// ============================================================================
// Signal Scale - 信号色阶（状态指示）
// ============================================================================

/**
 * 信号色阶 - 高饱和度的状态指示色
 */
export const signal = {
  success: '#00E676',      // 高亮绿
  error: '#FF1744',        // 高亮红
  warn: '#FFEA00',         // 赛博黄
  info: '#2196F3',         // 信息蓝
  
  // 亮色模式下的信号色（略微加深以保证对比度）
  successLight: '#00C853',
  errorLight: '#D50000',
  warnLight: '#FFD600',
  infoLight: '#1976D2',
  
  // 信号色的淡色背景（用于状态提示背景）
  successBg: 'rgba(0, 230, 118, 0.1)',
  errorBg: 'rgba(255, 23, 68, 0.1)',
  warnBg: 'rgba(255, 234, 0, 0.1)',
  infoBg: 'rgba(33, 150, 243, 0.1)',
} as const;

// ============================================================================
// Text Colors - 文本颜色
// ============================================================================

export const text = {
  // 暗色模式文本
  primary: '#F5F5F5',      // 主文本
  secondary: '#B0BEC5',    // 辅助文本
  tertiary: '#78909C',     // 第三级文本
  inverse: '#0B0E14',      // 反色文本
  
  // 亮色模式文本
  lightPrimary: '#1E293B',  // 主文本
  lightSecondary: '#475569', // 辅助文本
  lightTertiary: '#94A3B8', // 第三级文本
} as const;

// ============================================================================
// Theme Modes - 主题模式
// ============================================================================

/**
 * 暗色模式色彩令牌
 * 基于「霓虹暗夜」视觉语言
 */
export const darkTheme = {
  // Primary Colors - 电光青（用户意图）
  primary: energy.xiu,
  primaryHover: energy.xiuHover,
  primaryActive: energy.xiuDim,
  
  // Accent Colors - 霓虹紫（AI 生成）
  accent: energy.ai,
  accentHover: energy.aiHover,
  
  // Text Colors
  textPrimary: text.primary,
  textSecondary: text.secondary,
  textTertiary: text.tertiary,
  textInverse: text.inverse,
  
  // Surface Colors - 虚空色阶
  surfaceBg: void_.bg,
  surfaceCard: void_.surface1,
  surfaceHover: void_.surface2,
  surfaceActive: void_.surface3,
  
  // Border Colors
  border: void_.surface3,
  borderHover: void_.surface2,
  borderFocus: energy.xiu,
  
  // Semantic Colors - 信号色
  success: signal.success,
  successLight: signal.successBg,
  warning: signal.warn,
  warningLight: signal.warnBg,
  error: signal.error,
  errorLight: signal.errorBg,
  info: signal.info,
  infoLight: signal.infoBg,
} as const;

/**
 * 亮色模式色彩令牌
 * 保持能量色阶的核心特征，适配亮色背景
 */
export const lightTheme = {
  // Primary Colors - 电光青（亮色版本）
  primary: energy.xiuLight,
  primaryHover: '#0097A7',
  primaryActive: '#00838F',
  
  // Accent Colors - 霓虹紫（亮色版本）
  accent: energy.aiLight,
  accentHover: '#9C27B0',
  
  // Text Colors
  textPrimary: text.lightPrimary,
  textSecondary: text.lightSecondary,
  textTertiary: text.lightTertiary,
  textInverse: text.primary,
  
  // Surface Colors - 亮色虚空
  surfaceBg: void_.lightBg,
  surfaceCard: void_.lightSurface1,
  surfaceHover: void_.lightSurface2,
  surfaceActive: void_.lightSurface3,
  
  // Border Colors
  border: void_.lightSurface3,
  borderHover: '#CBD5E1',
  borderFocus: energy.xiuLight,
  
  // Semantic Colors - 信号色（亮色版本）
  success: signal.successLight,
  successLight: '#E8F5E9',
  warning: signal.warnLight,
  warningLight: '#FFFDE7',
  error: signal.errorLight,
  errorLight: '#FFEBEE',
  info: signal.infoLight,
  infoLight: '#E3F2FD',
} as const;

// ============================================================================
// Legacy Compatibility - 向后兼容的旧色阶
// 所有旧色阶已映射到新的设计系统
// ============================================================================

/**
 * Indigo -> 电光青色阶 (主色)
 * 原本的靛蓝色已替换为电光青
 */
export const indigo = {
  50: '#E0FCFF',           // 最浅
  100: '#B3F5FF',
  200: '#80EEFF',
  300: energy.xiu,         // 暗黑模式主色 -> 电光青 #00F3FF
  400: energy.xiuHover,    // 暗黑模式悬停 -> #33F5FF
  500: '#00D4E0',
  600: energy.xiuLight,    // 亮色模式主色 -> #00BCD4
  700: '#0097A7',          // 亮色模式悬停
  800: '#00838F',
  900: '#006064',          // 最深
} as const;

/**
 * Cyan -> 霓虹紫色阶 (AI 辅色)
 * 原本的青色已替换为霓虹紫，用于区分 AI 生成内容
 */
export const cyan = {
  50: '#FCE4F6',           // 最浅
  100: '#F8BBE8',
  200: '#F48FDB',
  300: '#EE63CE',
  400: energy.ai,          // 暗黑模式辅色 -> 霓虹紫 #D946EF
  500: energy.aiLight,     // 亮色模式辅色 -> #AB47BC
  600: '#9C27B0',
  700: '#7B1FA2',
  800: '#6A1B9A',
  900: '#4A148C',          // 最深
} as const;

/**
 * Neutral -> 虚空灰色阶
 * 映射到带冷色调的虚空色阶
 */
export const neutral = {
  50: void_.lightBg,       // #F8FAFC 亮色模式背景
  100: void_.lightSurface2, // #F1F5F9
  200: void_.lightSurface3, // #E2E8F0 亮色模式边框
  300: text.secondary,     // #B0BEC5 暗黑模式辅文本
  400: text.tertiary,      // #78909C
  500: '#607D8B',
  600: '#546E7A',
  700: void_.surface3,     // #2A3040 暗黑模式边框
  800: void_.surface1,     // #151922 暗黑模式卡片
  900: void_.bg,           // #0B0E14 暗黑模式背景
} as const;

/**
 * Green -> 高亮绿色阶 (成功状态)
 */
export const green = {
  50: '#E8F5E9',           // 淡背景
  100: '#C8E6C9',
  200: '#A5D6A7',
  300: '#81C784',
  400: signal.success,     // 暗黑模式成功色 -> #00E676
  500: signal.success,     // #00E676
  600: signal.successLight, // 亮色模式成功色 -> #00C853
  700: '#388E3C',
  800: '#2E7D32',
  900: '#1B5E20',
} as const;

/**
 * Amber -> 赛博黄色阶 (警告状态)
 */
export const amber = {
  50: '#FFFDE7',           // 淡背景
  100: '#FFF9C4',
  200: '#FFF59D',
  300: signal.warn,        // 暗黑模式警告色 -> #FFEA00
  400: signal.warn,        // #FFEA00
  500: signal.warnLight,   // 亮色模式警告色 -> #FFD600
  600: '#FFC400',
  700: '#FFAB00',
  800: '#FF8F00',
  900: '#FF6F00',
} as const;

/**
 * Red -> 高亮红色阶 (错误状态)
 */
export const red = {
  50: '#FFEBEE',           // 淡背景
  100: '#FFCDD2',
  200: '#EF9A9A',
  300: '#E57373',
  400: signal.error,       // 暗黑模式错误色 -> #FF1744
  500: signal.error,       // #FF1744
  600: signal.errorLight,  // 亮色模式错误色 -> #D50000
  700: '#C62828',
  800: '#B71C1C',
  900: '#8E0000',
} as const;

/**
 * Blue -> 信息蓝色阶 (信息状态)
 */
export const blue = {
  50: '#E3F2FD',           // 淡背景
  100: '#BBDEFB',
  200: '#90CAF9',
  300: signal.info,        // 暗黑模式信息色 -> #2196F3
  400: signal.info,        // #2196F3
  500: signal.info,        // #2196F3
  600: signal.infoLight,   // 亮色模式信息色 -> #1976D2
  700: '#1565C0',
  800: '#0D47A1',
  900: '#0A3D91',
} as const;

// ============================================================================
// Theme Type Definitions
// ============================================================================

export type ThemeMode = 'light' | 'dark';
export type ColorTheme = {
  readonly primary: string;
  readonly primaryHover: string;
  readonly primaryActive: string;
  readonly accent: string;
  readonly accentHover: string;
  readonly textPrimary: string;
  readonly textSecondary: string;
  readonly textTertiary: string;
  readonly textInverse: string;
  readonly surfaceBg: string;
  readonly surfaceCard: string;
  readonly surfaceHover: string;
  readonly surfaceActive: string;
  readonly border: string;
  readonly borderHover: string;
  readonly borderFocus: string;
  readonly success: string;
  readonly successLight: string;
  readonly warning: string;
  readonly warningLight: string;
  readonly error: string;
  readonly errorLight: string;
  readonly info: string;
  readonly infoLight: string;
};

// ============================================================================
// Theme Context and Utilities
// ============================================================================

/**
 * 获取当前主题
 * 默认为暗黑模式（开发者首选）
 */
export function getTheme(mode: ThemeMode = 'dark'): ColorTheme {
  return mode === 'light' ? lightTheme : darkTheme;
}

/**
 * CLI 颜色映射（用于 Ink Text 组件）
 * 将设计令牌映射到 Ink 的颜色名称
 */
export const inkColorMap = {
  primary: 'cyan' as const,     // 电光青 -> cyan
  accent: 'magenta' as const,   // 霓虹紫 -> magenta
  success: 'green' as const,
  warning: 'yellow' as const,
  error: 'red' as const,
  info: 'blue' as const,
  muted: 'gray' as const,
} as const;

/**
 * Chalk 颜色映射（用于终端输出）
 */
export const chalkColorMap = {
  primary: 'cyan',              // 电光青
  accent: 'magenta',            // 霓虹紫
  success: 'green',
  warning: 'yellow',
  error: 'red',
  info: 'blue',
  muted: 'gray',
} as const;

// ============================================================================
// Exports
// ============================================================================

export const colors = {
  // New color scales
  void: void_,
  energy,
  signal,
  text,
  
  // Legacy color scales (mapped to new system)
  indigo,
  cyan,
  neutral,
  green,
  amber,
  red,
  blue,
  
  // Themes
  light: lightTheme,
  dark: darkTheme,
  
  // Utilities
  getTheme,
  inkColorMap,
  chalkColorMap,
} as const;

export default colors;
