# Auggie ACP Agent Integration

This guide explains how to set up and use Auggie as an ACP (Agent Client Protocol) agent in AutoDev.

## Overview

Auggie is Augment Code's AI agent that supports the Agent Client Protocol (ACP). By integrating Auggie with AutoDev, you can leverage Auggie's capabilities for code generation, analysis, and refactoring tasks directly within AutoDev.

## Prerequisites

1. **Auggie CLI installed**: Download and install Auggie from [Augment Code](https://docs.augmentcode.com/cli/acp/agent)
2. **Auggie API Key**: Obtain your API key from your Augment Code account
3. **AutoDev 3.0+**: Ensure you have AutoDev version 3.0 or later

## Installation

### 1. Install Auggie CLI

Follow the official [Auggie installation guide](https://docs.augmentcode.com/cli/acp/agent):

```bash
# macOS / Linux
brew install augment-code/tap/auggie

# Or download from releases
# https://github.com/augment-code/auggie/releases
```

### 2. Verify Installation

```bash
auggie --version
auggie --acp --help
```

## Configuration

### 1. Edit config.yaml

Add Auggie to your AutoDev configuration file at `~/.autodev/config.yaml`:

```yaml
acpAgents:
  auggie:
    name: "Auggie"
    command: "auggie"
    args: "--acp"
    env: "AUGGIE_API_KEY=your_api_key_here"

activeAcpAgent: auggie
```

### 2. Set Your API Key

Replace `your_api_key_here` with your actual Auggie API key:

```yaml
env: "AUGGIE_API_KEY=sk-aug-xxxxxxxxxxxxx"
```

Alternatively, set the environment variable:

```bash
export AUGGIE_API_KEY=sk-aug-xxxxxxxxxxxxx
```

## Usage

### In AutoDev Compose GUI

1. Open AutoDev Compose
2. In the Agentic tab, click the **Engine** dropdown
3. Select **Auggie** from the list
4. Enter your task in the input field
5. Click **Send** to execute the task

### In AutoDev CLI

```bash
autodev code -p /path/to/project -t "Your task here" --engine auggie
```

## Troubleshooting

### Connection Failed

**Error**: `Failed to connect to ACP agent`

**Solutions**:
- Verify Auggie is installed: `auggie --version`
- Check API key is set correctly
- Ensure Auggie supports ACP mode: `auggie --acp --help`

### Command Not Found

**Error**: `auggie: command not found`

**Solutions**:
- Verify installation: `which auggie`
- Add Auggie to PATH if needed
- Reinstall Auggie

### API Key Issues

**Error**: `Authentication failed` or `Invalid API key`

**Solutions**:
- Verify API key in config.yaml
- Check API key hasn't expired
- Regenerate API key in Augment Code dashboard

## Configuration Examples

### Multiple ACP Agents

You can configure multiple ACP agents and switch between them:

```yaml
acpAgents:
  auggie:
    name: "Auggie"
    command: "auggie"
    args: "--acp"
    env: "AUGGIE_API_KEY=sk-aug-xxx"
  
  kimi:
    name: "Kimi CLI"
    command: "kimi"
    args: "--acp"
    env: "KIMI_API_KEY=xxx"
  
  claude:
    name: "Claude Code"
    command: "claude"
    args: "--acp"
    env: ""

activeAcpAgent: auggie
```

### Advanced Configuration

```yaml
acpAgents:
  auggie:
    name: "Auggie (Production)"
    command: "auggie"
    args: "--acp --verbose"
    env: |
      AUGGIE_API_KEY=sk-aug-xxx
      AUGGIE_MODEL=claude-3-opus
```

## References

- [Auggie Documentation](https://docs.augmentcode.com/cli/acp/agent)
- [Agent Client Protocol](https://github.com/agentclientprotocol/agent-client-protocol)
- [AutoDev GitHub](https://github.com/phodal/auto-dev)

