/**
 * AutoDev Design System
 * 
 * 基于「霓虹暗夜」与「气韵流动」的视觉语言设计的统一设计系统
 * 支持亮色/暗色模式
 */

export * from './colors.js';
export { default as colors } from './colors.js';
export * from './theme-helpers.js';
export { default as themeHelpers } from './theme-helpers.js';

// ============================================================================
// CSS Variables - 用于 Web/CSS 环境
// ============================================================================

/**
 * 生成 CSS 变量定义字符串（暗色模式）
 */
export const cssVariablesDark = `
:root, [data-theme="dark"] {
  /* 虚空色阶 */
  --void-bg: #0B0E14;
  --void-surface-1: #151922;
  --void-surface-2: #1F2430;
  --void-surface-3: #2A3040;
  --void-overlay: rgba(11, 14, 20, 0.8);
  
  /* 能量色阶 - 电光青（用户意图） */
  --energy-xiu: #00F3FF;
  --energy-xiu-hover: #33F5FF;
  --energy-xiu-dim: rgba(0, 243, 255, 0.25);
  
  /* 能量色阶 - 霓虹紫（AI 生成） */
  --energy-ai: #D946EF;
  --energy-ai-hover: #E066F5;
  --energy-ai-dim: rgba(217, 70, 239, 0.25);
  
  /* 信号色阶 */
  --signal-success: #00E676;
  --signal-error: #FF1744;
  --signal-warn: #FFEA00;
  --signal-info: #2196F3;
  --signal-success-bg: rgba(0, 230, 118, 0.1);
  --signal-error-bg: rgba(255, 23, 68, 0.1);
  --signal-warn-bg: rgba(255, 234, 0, 0.1);
  --signal-info-bg: rgba(33, 150, 243, 0.1);
  
  /* 文本颜色 */
  --text-primary: #F5F5F5;
  --text-secondary: #B0BEC5;
  --text-tertiary: #78909C;
  --text-inverse: #0B0E14;
  
  /* 动效曲线 */
  --ease-xiu: cubic-bezier(0.16, 1, 0.3, 1);
  --ease-stream: linear;
  --spring-tactile: linear(
    0, 0.009, 0.035 2.1%, 0.141 4.4%, 0.723 12.9%, 0.938 16.7%,
    1.017 18.4%, 1.077 20.4%, 1.121 22.8%, 1.149 25.7%, 1.159 29.6%,
    1.14 34.9%, 1.079 43%, 1.04 48.3%, 1.016 53.4%, 0.999 61.6%,
    0.995 71.2%, 1
  );
  
  /* 时长 */
  --duration-instant: 100ms;
  --duration-fast: 150ms;
  --duration-normal: 250ms;
  --duration-slow: 400ms;
  
  /* 间距 (4px grid) */
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-6: 24px;
  --space-8: 32px;
  
  /* 语义化别名 */
  --color-primary: var(--energy-xiu);
  --color-primary-hover: var(--energy-xiu-hover);
  --color-accent: var(--energy-ai);
  --color-accent-hover: var(--energy-ai-hover);
  --color-background: var(--void-bg);
  --color-surface: var(--void-surface-1);
  --color-border: var(--void-surface-3);
}
`;

/**
 * 生成 CSS 变量定义字符串（亮色模式）
 */
export const cssVariablesLight = `
[data-theme="light"] {
  /* 虚空色阶 - 亮色版本 */
  --void-bg: #F8FAFC;
  --void-surface-1: #FFFFFF;
  --void-surface-2: #F1F5F9;
  --void-surface-3: #E2E8F0;
  --void-overlay: rgba(0, 0, 0, 0.5);
  
  /* 能量色阶 - 亮色版本 */
  --energy-xiu: #00BCD4;
  --energy-xiu-hover: #0097A7;
  --energy-xiu-dim: rgba(0, 188, 212, 0.25);
  
  --energy-ai: #AB47BC;
  --energy-ai-hover: #9C27B0;
  --energy-ai-dim: rgba(171, 71, 188, 0.25);
  
  /* 信号色阶 - 亮色版本 */
  --signal-success: #00C853;
  --signal-error: #D50000;
  --signal-warn: #FFD600;
  --signal-info: #1976D2;
  --signal-success-bg: #E8F5E9;
  --signal-error-bg: #FFEBEE;
  --signal-warn-bg: #FFFDE7;
  --signal-info-bg: #E3F2FD;
  
  /* 文本颜色 - 亮色版本 */
  --text-primary: #1E293B;
  --text-secondary: #475569;
  --text-tertiary: #94A3B8;
  --text-inverse: #F5F5F5;
  
  /* 语义化别名 */
  --color-primary: var(--energy-xiu);
  --color-primary-hover: var(--energy-xiu-hover);
  --color-accent: var(--energy-ai);
  --color-accent-hover: var(--energy-ai-hover);
  --color-background: var(--void-bg);
  --color-surface: var(--void-surface-1);
  --color-border: var(--void-surface-3);
}
`;

/**
 * 霓虹发光效果 CSS
 */
export const cssGlowEffects = `
/* 焦点光环 - 电光青 */
.focus-ring-xiu {
  box-shadow: 
    0 0 0 2px var(--void-bg),
    0 0 0 4px var(--energy-xiu),
    0 0 15px 2px var(--energy-xiu-dim);
  transition: box-shadow 0.15s var(--ease-xiu);
}

/* AI 思考光晕 - 霓虹紫 */
.ai-pulse {
  animation: ai-breathe 2s ease-in-out infinite;
}

@keyframes ai-breathe {
  0%, 100% { box-shadow: 0 0 0 0 var(--energy-ai-dim); }
  50% { box-shadow: 0 0 20px 4px var(--energy-ai-dim); }
}

/* 用户输入光晕 - 电光青 */
.user-glow {
  box-shadow: 0 0 10px 2px var(--energy-xiu-dim);
}

/* AI 输出光晕 - 霓虹紫 */
.ai-glow {
  box-shadow: 0 0 10px 2px var(--energy-ai-dim);
}

/* 快速过渡 */
.transition-xiu {
  transition: all var(--duration-fast) var(--ease-xiu);
}

/* 流式输出过渡 */
.transition-stream {
  transition: all var(--duration-normal) var(--ease-stream);
}
`;

/**
 * 完整的 CSS 变量定义
 */
export const cssVariables = cssVariablesDark + '\n' + cssVariablesLight + '\n' + cssGlowEffects;

/**
 * 将 CSS 变量注入到文档中（仅在浏览器环境）
 */
export function injectCSSVariables(): void {
  if (typeof document === 'undefined') return;
  
  const styleId = 'autodev-design-system';
  let styleEl = document.getElementById(styleId) as HTMLStyleElement | null;
  
  if (!styleEl) {
    styleEl = document.createElement('style');
    styleEl.id = styleId;
    document.head.appendChild(styleEl);
  }
  
  styleEl.textContent = cssVariables;
}
