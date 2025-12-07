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
// ============================================================================

/**
 * @deprecated Use energy.xiu instead
 */
export const indigo = {
  50: '#E0F7FA',
  100: '#B2EBF2',
  200: '#80DEEA',
  300: energy.xiu,         // 暗黑模式主色 -> 电光青
  400: energy.xiuHover,    // 暗黑模式悬停
  500: '#00ACC1',
  600: energy.xiuLight,    // 亮色模式主色
  700: '#00838F',          // 亮色模式悬停
  800: '#006064',
  900: '#004D40',
} as const;

/**
 * @deprecated Use energy.ai instead
 */
export const cyan = {
  50: '#FCE4EC',
  100: '#F8BBD9',
  200: '#F48FB1',
  300: '#F06292',
  400: energy.ai,          // 暗黑模式辅色 -> 霓虹紫
  500: energy.aiLight,     // 亮色模式辅色
  600: '#8E24AA',
  700: '#7B1FA2',
  800: '#6A1B9A',
  900: '#4A148C',
} as const;

/**
 * @deprecated Use void_ instead
 */
export const neutral = {
  50: void_.lightBg,       // 亮色模式背景
  100: text.primary,       // 暗黑模式主文本
  200: void_.lightSurface3, // 亮色模式边框
  300: text.secondary,     // 暗黑模式辅文本
  400: '#78909C',
  500: text.tertiary,
  600: '#546E7A',
  700: void_.surface3,     // 暗黑模式边框
  800: void_.surface1,     // 暗黑模式卡片
  900: void_.bg,           // 暗黑模式背景
} as const;

/**
 * @deprecated Use signal.success instead
 */
export const green = {
  50: signal.successBg,
  100: signal.successBg,
  200: 'rgba(0, 230, 118, 0.4)',
  300: signal.success,     // 暗黑模式成功色
  400: signal.success,
  500: signal.success,
  600: signal.successLight, // 亮色模式成功色
  700: '#00A844',
  800: '#008C39',
  900: '#00662A',
} as const;

/**
 * @deprecated Use signal.warn instead
 */
export const amber = {
  50: signal.warnBg,
  100: signal.warnBg,
  200: 'rgba(255, 234, 0, 0.4)',
  300: signal.warn,        // 暗黑模式警告色
  400: signal.warn,
  500: signal.warnLight,   // 亮色模式警告色
  600: '#FFC400',
  700: '#FFAB00',
  800: '#FF8F00',
  900: '#FF6F00',
} as const;

/**
 * @deprecated Use signal.error instead
 */
export const red = {
  50: signal.errorBg,
  100: signal.errorBg,
  200: 'rgba(255, 23, 68, 0.4)',
  300: signal.error,       // 暗黑模式错误色
  400: signal.error,
  500: signal.error,
  600: signal.errorLight,  // 亮色模式错误色
  700: '#B71C1C',
  800: '#8E0000',
  900: '#5D0000',
} as const;

/**
 * @deprecated Use signal.info instead
 */
export const blue = {
  50: signal.infoBg,
  100: signal.infoBg,
  200: 'rgba(33, 150, 243, 0.4)',
  300: signal.info,        // 暗黑模式信息色
  400: signal.info,
  500: signal.info,        // 亮色模式信息色
  600: signal.infoLight,
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
  
  // Legacy color scales (deprecated)
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
