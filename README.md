# AutoDev Xiuper

> **One Platform. All Phases. Every Device.**  
> 统一平台·全开发阶段·跨全设备

**AutoDev Xiuper** is the AI-native development platform built on Kotlin Multiplatform, covering all 7 phases of SDLC (Requirements → Development → Review → Testing → Data → Deployment → Operations) and supporting 8+ platforms (IDEA, VSCode, CLI, Web, Desktop, Android, iOS, Server).

![ScreenShot](https://xiuper.com/screenshot.png)

## 🚀 Get Started

### Download AutoDev Xiuper

- **IntelliJ IDEA Plugin**: [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/29223-autodev-experiment)
- **VSCode Extension**: [Visual Studio Marketplace](https://marketplace.visualstudio.com/items?itemName=Phodal.autodev)
- **CLI Tool**: `npm install -g @xiuper/cli`
- **Web Version**: https://web.xiuper.com/
- **Desktop & Android**: [Release Pages](https://github.com/phodal/auto-dev/releases)

### Previous Versions

- **AutoDev 2.0** (Stable): [Branch](https://github.com/phodal/auto-dev/tree/autodev2) | [Plugin](https://plugins.jetbrains.com/plugin/26988)

### Modules

| Module               | Platform            | Status              | Description                                           |
|----------------------|---------------------|---------------------|-------------------------------------------------------|
| **mpp-idea**         | IntelliJ IDEA       | ✅ Production        | Jewel UI, Agent toolwindow, code review, remote agent |
| **mpp-vscode**       | VSCode              | ✅ Production        | Xiuper Agent                                          |
| **mpp-ui** (Desktop) | macOS/Windows/Linux | ✅ Production        | Compose Multiplatform desktop app                     |
| **mpp-ui** (CLI)     | Terminal (Node.js)  | ✅ Production        | Terminal UI (React/Ink), local/server mode            |
| **mpp-ui** (Android) | Android             | ✅ Production        | Native Android app                                    |
| **mpp-web** (Web)    | Web                 | ✅ Production        | Web app                                               |
| **mpp-server**       | Server              | ✅ Production        | JVM (Ktor)                                            |
| **mpp-ios**          | iOS                 | 🚧 Production Ready | Native iOS app (SwiftUI + Compose)                    |

### 🌟 Key Features

**Xiuper Edition** represents a major milestone in AI-powered development:

- **One Platform**: Unified Kotlin Multiplatform architecture - write once, deploy everywhere
- **All Phases**: 7 specialized agents covering complete software development lifecycle
  - Requirements → Development → Review → Testing → Data → Deployment → Operations
- **Every Device**: Native support for 8+ platforms with zero compromise on performance
  - IDE: IntelliJ IDEA, VSCode
  - Desktop: macOS, Windows, Linux (Compose Multiplatform)
  - Mobile: Android, iOS (Native + Compose)
  - Terminal: CLI (Node.js with React/Ink)
  - Web: Modern web app
  - Server: Remote agent server (Ktor)
- **Multi-LLM Support**: OpenAI, Anthropic, Google, DeepSeek, Ollama, and more
- **DevIns Language**: Executable AI Agent scripting language for workflow automation
- **MCP Protocol**: Extensible tool ecosystem via Model Context Protocol
- **Code Intelligence**: TreeSitter-based parsing for Java, Kotlin, Python, JS, TS, Go, Rust, C#
- **Global Ready**: Full internationalization (Chinese/English)

## 🤖 Builtin Agents

AutoDev Xiuper includes 7 specialized AI agents mapped to the complete Software Development Lifecycle (SDLC):

| Agent         | SDLC Phase   | Description                                                                                        | Capabilities                      | Status    |
|---------------|--------------|----------------------------------------------------------------------------------------------------|-----------------------------------|-----------|
| **Knowledge** | Requirements | Requirements understanding and knowledge construction with AI-native document reading and analysis | DocQL / Context Engineering       | ✅ Stable  |
| **Coding**    | Development  | Autonomous coding agent with complete file system, shell, and tool access capabilities             | MCP / SubAgents / DevIns DSL      | ✅ Stable  |
| **Review**    | Code Review  | Professional code review analyzing code quality, security, performance, and best practices         | Linter / Summary / AutoFix        | ✅ Stable  |
| **Testing**   | Testing      | Automated testing agent that generates test cases, executes tests, and analyzes coverage           | E2E / Self-healing / Coverage     | 🚧 Coming |
| **ChatDB**    | Data         | Database conversation agent supporting Text-to-SQL and natural language data queries               | Schema Linking / Multi-DB / Query | ✅ Stable  |
| **WebEdit**   | Deployment   | Web editing agent for browsing pages, selecting DOM elements, and interacting with web content     | Inspect / Chat / Mapping          | 🔄 Beta   |
| **Ops**       | Operations   | Operations monitoring agent for log analysis, performance monitoring, and alert handling           | Logs / Metrics / Alerts           | 🚧 Coming |

Each agent is designed to handle specific phases of the development lifecycle, providing comprehensive AI assistance from requirements gathering to production operations.

## License

This code is distributed under the MPL 2.0 license. See `LICENSE` in this directory.
