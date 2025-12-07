/**
 * Theme Helpers - 主题辅助工具
 * 
 * 提供便捷的方法来访问当前主题的颜色和工具函数
 * 基于「霓虹暗夜」视觉语言的语义化颜色系统
 */

import { inkColorMap, chalkColorMap } from './colors.js';
import chalk from 'chalk';

/**
 * Chalk 颜色辅助类
 * 使用设计系统中定义的语义化颜色
 */
export const semanticChalk = {
  // Primary and Accent - 能量色
  primary: chalk.cyan,      // 电光青 - 用户意图
  accent: chalk.magenta,    // 霓虹紫 - AI 生成
  
  // Semantic colors - 信号色
  success: chalk.green,
  warning: chalk.yellow,
  error: chalk.red,
  info: chalk.blue,
  
  // Text variants
  muted: chalk.gray,
  dim: chalk.dim,
  bold: chalk.bold,
  
  // Combined styles
  successBold: chalk.green.bold,
  errorBold: chalk.red.bold,
  warningBold: chalk.yellow.bold,
  infoBold: chalk.blue.bold,
  primaryBold: chalk.cyan.bold,
  accentBold: chalk.magenta.bold,
  
  // 人机边界颜色
  user: chalk.cyan,         // 用户相关 - 电光青
  ai: chalk.magenta,        // AI 相关 - 霓虹紫
} as const;

/**
 * Ink 颜色辅助 - 用于 <Text> 组件
 * 返回 Ink 支持的颜色字符串
 */
export const semanticInk = {
  primary: inkColorMap.primary,
  accent: inkColorMap.accent,
  success: inkColorMap.success,
  warning: inkColorMap.warning,
  error: inkColorMap.error,
  info: inkColorMap.info,
  muted: inkColorMap.muted,
  
  // 人机边界颜色
  user: 'cyan' as const,    // 用户相关 - 电光青
  ai: 'magenta' as const,   // AI 相关 - 霓虹紫
} as const;

/**
 * 语义化颜色类型
 */
export type SemanticColor = 'primary' | 'accent' | 'success' | 'warning' | 'error' | 'info' | 'muted' | 'user' | 'ai';

/**
 * 获取 Ink 颜色（用于 <Text color={...}> ）
 */
export function getInkColor(semantic: SemanticColor): string {
  return semanticInk[semantic] ?? semanticInk.muted;
}

/**
 * 获取 Chalk 颜色函数
 */
export function getChalkColor(semantic: SemanticColor): typeof chalk {
  return semanticChalk[semantic] ?? semanticChalk.muted;
}

/**
 * 状态指示器
 */
export const statusIndicators = {
  success: '✓',
  error: '✗',
  warning: '⚠',
  info: 'ℹ',
  loading: '⏳',
  processing: '⚡',  // 使用闪电符号更符合「气韵流动」美学
  user: '›',         // 用户输入提示
  ai: '◆',           // AI 输出提示
} as const;

/**
 * 带颜色的状态指示器
 */
export function coloredStatus(status: keyof typeof statusIndicators, message: string): string {
  const indicator = statusIndicators[status];
  
  switch (status) {
    case 'success':
      return chalk.green(`${indicator} ${message}`);
    case 'error':
      return chalk.red(`${indicator} ${message}`);
    case 'warning':
      return chalk.yellow(`${indicator} ${message}`);
    case 'info':
      return chalk.blue(`${indicator} ${message}`);
    case 'loading':
    case 'processing':
      return chalk.cyan(`${indicator} ${message}`);
    case 'user':
      return chalk.cyan(`${indicator} ${message}`);
    case 'ai':
      return chalk.magenta(`${indicator} ${message}`);
    default:
      return `${indicator} ${message}`;
  }
}

/**
 * 分隔线样式
 */
export const dividers = {
  solid: (length: number = 60) => chalk.gray('─'.repeat(length)),
  double: (length: number = 60) => chalk.gray('═'.repeat(length)),
  bold: (length: number = 60) => chalk.bold('─'.repeat(length)),
  glow: (length: number = 60) => chalk.cyan('━'.repeat(length)),  // 电光青发光分隔线
} as const;

/**
 * 高亮文本
 */
export function highlight(text: string, color: SemanticColor = 'primary'): string {
  const chalkFn = getChalkColor(color);
  return chalkFn.bold(text);
}

/**
 * 代码块样式
 */
export function codeBlock(code: string, language?: string): string {
  const header = language ? chalk.gray(`[${language}]`) : '';
  const lines = code.split('\n').map(line => chalk.gray('│ ') + line);
  
  return [
    header,
    chalk.gray('┌' + '─'.repeat(58) + '┐'),
    ...lines,
    chalk.gray('└' + '─'.repeat(58) + '┘')
  ].filter(Boolean).join('\n');
}

/**
 * 人机边界样式 - 用于区分用户输入和 AI 输出
 */
export const boundary = {
  user: (text: string) => chalk.cyan(text),
  ai: (text: string) => chalk.magenta(text),
  userBold: (text: string) => chalk.cyan.bold(text),
  aiBold: (text: string) => chalk.magenta.bold(text),
} as const;

export default {
  semanticChalk,
  semanticInk,
  getInkColor,
  getChalkColor,
  statusIndicators,
  coloredStatus,
  dividers,
  highlight,
  codeBlock,
  boundary,
};
