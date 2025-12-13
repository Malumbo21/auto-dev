import React from 'react';

type Link = {
  label: string;
  href: string;
  variant?: 'primary' | 'secondary' | 'ghost';
};

const LINKS: Link[] = [
  { label: '立即体验（Web）', href: 'https://web.xiuper.com/', variant: 'primary' },
  { label: '打开 Web UI（本仓库 /#/app）', href: '#/app', variant: 'secondary' },
  { label: 'GitHub（源码）', href: 'https://github.com/phodal/xuiper.com', variant: 'ghost' },
];

const FEATURE_LIST: Array<{ title: string; desc: string }> = [
  { title: '全平台 AI Agent', desc: '一套核心逻辑，多端复用：Desktop、Android、iOS、Web、CLI、IDE 插件。' },
  { title: '多模型支持', desc: '支持 OpenAI / Anthropic / Google / DeepSeek / Ollama / OpenRouter 等。' },
  { title: '可扩展工具生态', desc: '内置 MCP（Model Context Protocol），让工具集成和能力扩展更标准。' },
  { title: '代码理解与变更', desc: 'TreeSitter 多语言解析 + Agent 工具链，面向真实工程工作流。' },
  { title: '跨平台 UI', desc: 'Kotlin Multiplatform + Compose Multiplatform，统一设计与交互。' },
  { title: '双语支持', desc: '中文/英文界面，适配不同团队与使用场景。' },
];

const PLATFORM_LIST: Array<{ name: string; note: string }> = [
  { name: 'IntelliJ IDEA', note: 'Jewel UI / 工具窗口 / Code Review / Remote Agent' },
  { name: 'VSCode', note: 'Xuiper Agent（扩展）' },
  { name: 'Desktop', note: 'macOS / Windows / Linux（Compose Desktop）' },
  { name: 'Android', note: '原生 Android（Compose）' },
  { name: 'iOS', note: 'SwiftUI + Compose（Production Ready）' },
  { name: 'Web', note: '浏览器 Web App（React + mpp-core）' },
  { name: 'CLI', note: 'Node.js TUI（React/Ink）' },
  { name: 'Server', note: 'Ktor（可选）' },
];

function ButtonLink({ label, href, variant = 'secondary' }: Link) {
  const className = ['xu-btn', `xu-btn--${variant}`].join(' ');
  const isExternal = /^https?:\/\//.test(href);
  return (
    <a
      className={className}
      href={href}
      target={isExternal ? '_blank' : undefined}
      rel={isExternal ? 'noreferrer' : undefined}
    >
      {label}
    </a>
  );
}

export const LandingPage: React.FC = () => {
  return (
    <div className="xu-page">
      <header className="xu-header">
        <div className="xu-container xu-header__inner">
          <a className="xu-brand" href="#/">
            <span className="xu-brand__mark" aria-hidden="true">X</span>
            <span className="xu-brand__text">Xuiper</span>
          </a>
          <nav className="xu-nav">
            <a className="xu-nav__link" href="#features">特性</a>
            <a className="xu-nav__link" href="#platforms">平台</a>
            <a className="xu-nav__link" href="#start">开始使用</a>
            <a className="xu-nav__link" href="https://github.com/phodal/xuiper.com" target="_blank" rel="noreferrer">
              GitHub
            </a>
          </nav>
        </div>
      </header>

      <main>
        <section className="xu-hero">
          <div className="xu-container xu-hero__inner">
            <div className="xu-hero__content">
              <p className="xu-badge">AutoDev 3.0 · Xiuper</p>
              <h1 className="xu-hero__title">
                面向 AI4SDLC 的
                <br />
                全平台开发助理与 Coding Agent
              </h1>
              <p className="xu-hero__subtitle">
                基于 Kotlin Multiplatform 与 Compose Multiplatform，覆盖 IDE、桌面、移动端、Web、CLI。
                让 AI Agent 真正进入你的工程化工作流。
              </p>
              <div className="xu-hero__cta">
                {LINKS.map((l) => (
                  <ButtonLink key={l.label} {...l} />
                ))}
              </div>
              <div className="xu-hero__meta">
                <div className="xu-kv">
                  <div className="xu-kv__k">CLI 安装</div>
                  <div className="xu-kv__v">
                    <code>npm install -g @autodev/cli</code>
                  </div>
                </div>
                <div className="xu-kv">
                  <div className="xu-kv__k">License</div>
                  <div className="xu-kv__v">MPL 2.0</div>
                </div>
              </div>
            </div>

            <div className="xu-hero__visual" aria-hidden="true">
              <div className="xu-orbit">
                <div className="xu-orbit__ring" />
                <div className="xu-orbit__ring xu-orbit__ring--2" />
                <div className="xu-orbit__core">
                  <div className="xu-orbit__x">X</div>
                  <div className="xu-orbit__hint">X =&gt; Super open</div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section id="features" className="xu-section">
          <div className="xu-container">
            <h2 className="xu-section__title">关键特性</h2>
            <p className="xu-section__desc">
              Landing 文案基于本仓库 `mpp-ui`/`mpp-web` 的 README 及实现：多端一致、可扩展、面向真实工程。
            </p>
            <div className="xu-grid">
              {FEATURE_LIST.map((f) => (
                <div key={f.title} className="xu-card">
                  <div className="xu-card__title">{f.title}</div>
                  <div className="xu-card__desc">{f.desc}</div>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section id="platforms" className="xu-section xu-section--alt">
          <div className="xu-container">
            <h2 className="xu-section__title">平台覆盖</h2>
            <p className="xu-section__desc">从编辑器到终端，从桌面到移动端，一套核心能力多端复用。</p>
            <div className="xu-grid xu-grid--platforms">
              {PLATFORM_LIST.map((p) => (
                <div key={p.name} className="xu-card xu-card--platform">
                  <div className="xu-card__title">{p.name}</div>
                  <div className="xu-card__desc">{p.note}</div>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section id="start" className="xu-section">
          <div className="xu-container">
            <h2 className="xu-section__title">开始使用</h2>
            <div className="xu-steps">
              <div className="xu-step">
                <div className="xu-step__n">01</div>
                <div className="xu-step__body">
                  <div className="xu-step__t">Web 体验</div>
                  <div className="xu-step__d">
                    打开 <a href="https://web.xiuper.com/" target="_blank" rel="noreferrer">web.xiuper.com</a>，或进入本仓库内置 Web UI（<a href="#/app">/#/app</a>）。
                  </div>
                </div>
              </div>
              <div className="xu-step">
                <div className="xu-step__n">02</div>
                <div className="xu-step__body">
                  <div className="xu-step__t">CLI 安装</div>
                  <div className="xu-step__d">
                    <code>npm install -g @autodev/cli</code>
                  </div>
                </div>
              </div>
              <div className="xu-step">
                <div className="xu-step__n">03</div>
                <div className="xu-step__body">
                  <div className="xu-step__t">配置多模型</div>
                  <div className="xu-step__d">
                    参考 `mpp-ui/config.yaml.example`，配置 OpenAI/DeepSeek/Ollama 等 provider，支持切换 active 配置。
                  </div>
                </div>
              </div>
            </div>

            <div className="xu-callout">
              <div className="xu-callout__t">提示</div>
              <div className="xu-callout__d">
                这是一个面向 `www.xuiper.com` 的 landing page 结构草案。后续你可以在 `xuiper.com` 仓库里继续补充截图、下载入口、以及更完整的产品文档链接。
              </div>
            </div>
          </div>
        </section>
      </main>

      <footer className="xu-footer">
        <div className="xu-container xu-footer__inner">
          <div className="xu-footer__left">
            <div className="xu-footer__brand">Xuiper</div>
            <div className="xu-footer__meta">AutoDev 3.0 · MPL 2.0</div>
          </div>
          <div className="xu-footer__right">
            <a className="xu-footer__link" href="https://github.com/phodal/xuiper.com" target="_blank" rel="noreferrer">
              GitHub
            </a>
            <a className="xu-footer__link" href="https://web.xiuper.com/" target="_blank" rel="noreferrer">
              Web
            </a>
            <a className="xu-footer__link" href="#/app">Web UI</a>
          </div>
        </div>
      </footer>
    </div>
  );
};


