# ACP Agents Overview

AutoDev supports the Agent Client Protocol (ACP), allowing you to integrate external AI agents for coding tasks.

## What is ACP?

The Agent Client Protocol (ACP) is an open standard for connecting AI agents to development tools. It uses JSON-RPC 2.0 over stdio for communication, enabling seamless integration of external agents.

**Resources**:
- [ACP GitHub Repository](https://github.com/agentclientprotocol/agent-client-protocol)
- [ACP Specification](https://github.com/agentclientprotocol/agent-client-protocol/blob/main/SPECIFICATION.md)

## Supported Agents

AutoDev supports the following ACP-compliant agents:

### Auggie
- **Provider**: Augment Code
- **Documentation**: [Auggie ACP Setup](./auggie-acp-setup.md)
- **Features**: Code generation, analysis, refactoring
- **Installation**: `brew install augment-code/tap/auggie`

### Kimi CLI
- **Provider**: Moonshot AI
- **Features**: Chinese AI agent with strong coding capabilities
- **Installation**: Follow [Kimi documentation](https://kimi.moonshot.cn)

### Claude Code
- **Provider**: Anthropic
- **Features**: Code generation, analysis, testing
- **Installation**: Follow [Claude Code documentation](https://docs.anthropic.com/claude/docs/claude-code)

### Gemini CLI
- **Provider**: Google
- **Features**: Code generation, analysis
- **Installation**: Follow [Gemini documentation](https://ai.google.dev)

## Configuration

All ACP agents are configured in `~/.autodev/config.yaml`:

```yaml
acpAgents:
  auggie:
    name: "Auggie"
    command: "auggie"
    args: "--acp"
    env: "AUGGIE_API_KEY=xxx"
  
  kimi:
    name: "Kimi CLI"
    command: "kimi"
    args: "--acp"
    env: "KIMI_API_KEY=xxx"

activeAcpAgent: auggie
```

## Usage

### In Compose GUI

1. Open AutoDev Compose
2. Go to the **Agentic** tab
3. Click the **Engine** dropdown
4. Select your preferred ACP agent
5. Enter your task and click **Send**

### In CLI

```bash
autodev code -p /path/to/project -t "Your task" --engine auggie
```

## Architecture

```
AutoDev (Client)
    ↓ (JSON-RPC over stdio)
ACP Agent CLI (--acp mode)
    ↓ (ACP Protocol)
External AI Agent
```

## Features

- **Multiple Agents**: Configure and switch between different ACP agents
- **Seamless Integration**: Agents appear in the engine selector dropdown
- **Full Protocol Support**: Supports all standard ACP operations
- **Environment Variables**: Pass API keys and configuration via env vars
- **Custom Arguments**: Add agent-specific command-line arguments

## Troubleshooting

### Agent Not Appearing in Dropdown

- Verify agent is configured in `config.yaml`
- Check `isConfigured()` returns true (command must be set)
- Restart AutoDev

### Connection Issues

- Verify agent CLI is installed: `which auggie`
- Test agent directly: `auggie --acp --help`
- Check API key is valid
- Review logs in `~/.autodev/acp-logs/`

### Performance

- ACP agents run as separate processes
- Each agent maintains its own session
- Agents are reused when possible for efficiency

## Adding New Agents

To add a new ACP agent:

1. Install the agent CLI
2. Verify it supports `--acp` mode
3. Add configuration to `config.yaml`
4. Restart AutoDev
5. Select the agent from the dropdown

## References

- [ACP Specification](https://github.com/agentclientprotocol/agent-client-protocol)
- [Auggie Setup Guide](./auggie-acp-setup.md)
- [AutoDev GitHub](https://github.com/phodal/auto-dev)

