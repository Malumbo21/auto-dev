# ACP Agents Overview

AutoDev supports the Agent Client Protocol (ACP), allowing you to integrate external AI agents for coding tasks.

## What is ACP?

The Agent Client Protocol (ACP) is an open standard for connecting AI agents to development tools. It uses JSON-RPC 2.0 over stdio for communication, enabling seamless integration of external agents.

**Resources**:
- [ACP GitHub Repository](https://github.com/agentclientprotocol/agent-client-protocol)
- [ACP Specification](https://github.com/agentclientprotocol/agent-client-protocol/blob/main/SPECIFICATION.md)

## Supported Agents

AutoDev supports both standard ACP-compliant agents and special-cased integrations.

### ACP-Compliant Agents

These agents use the standard Agent Client Protocol (ACP) with JSON-RPC 2.0 over stdio:

#### Auggie
- **Provider**: Augment Code
- **Protocol**: Standard ACP
- **Documentation**: [Auggie ACP Setup](./auggie-acp-setup.md)
- **Features**: Code generation, analysis, refactoring
- **Installation**: `brew install augment-code/tap/auggie`

#### Kimi CLI
- **Provider**: Moonshot AI
- **Protocol**: Standard ACP
- **Features**: Chinese AI agent with strong coding capabilities
- **Installation**: Follow [Kimi documentation](https://kimi.moonshot.cn)

#### Gemini CLI
- **Provider**: Google
- **Protocol**: Standard ACP
- **Features**: Code generation, analysis
- **Installation**: Follow [Gemini documentation](https://ai.google.dev)

### Special-Cased Agents

These agents require custom integration and do not follow the standard ACP protocol:

#### Claude Code
- **Provider**: Anthropic
- **Protocol**: Custom stream-json protocol (non-standard)
- **Features**: Code generation, analysis, testing
- **Installation**: Follow [Claude Code documentation](https://docs.anthropic.com/claude/docs/claude-code)
- **Note**: Claude Code uses a custom stream-json protocol instead of standard ACP JSON-RPC. It requires special handling in AutoDev and is not compatible with standard ACP tooling.

## Configuration

### ACP-Compliant Agents

Standard ACP agents are configured in `~/.autodev/config.yaml`:

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

### Special-Cased Agents

Claude Code requires custom configuration due to its non-standard stream-json protocol. Configuration details are handled separately from standard ACP agents.

## Usage

### In Compose GUI

1. Open AutoDev Compose
2. Go to the **Agentic** tab
3. Click the **Engine** dropdown
4. Select your preferred ACP agent
5. Enter your task and click **Send**

### In Compose GUI

ACP agents like Auggie are only available in the Compose GUI. The Node CLI supports `autodev`, `claude`, and `codex` engines only.

To use Auggie:
1. Open AutoDev Compose GUI
2. Click the **Engine** dropdown
3. Select **Auggie**
4. Enter your task and click **Send**

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

