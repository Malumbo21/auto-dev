/**
 * Codex Mode - Native Codex CLI via ACP protocol
 *
 * Connects to the native `codex` CLI through the Agent Client Protocol (ACP),
 * enabling multi-turn interactive sessions. Unlike the external CLI mode which
 * runs codex as a one-shot command, this mode maintains a persistent ACP
 * session for richer interaction (streaming thoughts, tool calls, plans).
 *
 * Supports other ACP agents as well (Gemini, Kimi, etc.).
 */

import type { Mode, ModeContext, ModeResult, ModeFactory } from './Mode.js';
import type { Message } from '../ui/App.js';
import { AcpClientConnection, type AcpClientCallbacks } from '../agents/acp/AcpClientConnection.js';
import { InputRouter } from '../processors/InputRouter.js';
import { SlashCommandProcessor } from '../processors/SlashCommandProcessor.js';
import * as path from 'path';
import * as fs from 'fs';

/**
 * Known ACP agent presets (mirrors AcpAgentPresets.kt).
 */
const ACP_AGENT_PRESETS: Record<string, { command: string; args: string[]; name: string }> = {
  codex: { command: 'codex', args: ['--acp'], name: 'Codex' },
  kimi: { command: 'kimi', args: ['acp'], name: 'Kimi' },
  gemini: { command: 'gemini', args: ['--experimental-acp'], name: 'Gemini' },
  copilot: { command: 'copilot', args: ['--acp'], name: 'Copilot' },
};

/**
 * Codex Mode - interactive ACP agent sessions in the TUI.
 */
export class CodexMode implements Mode {
  readonly name = 'codex';
  readonly displayName = 'Codex (ACP)';
  readonly description = 'Interactive Codex/ACP agent with streaming thoughts, tool calls, and plans';
  readonly icon = '>_';

  private client: AcpClientConnection | null = null;
  private router: InputRouter | null = null;
  private isExecuting = false;
  private projectPath = process.cwd();
  private agentName = 'codex';

  // Track rendering state for tool call dedup
  private startedToolCallIds = new Set<string>();
  private toolCallTitles = new Map<string, string>();

  async initialize(context: ModeContext): Promise<void> {
    context.logger.info('[CodexMode] Initializing Codex/ACP mode...');

    try {
      if (context.projectPath) {
        this.projectPath = path.resolve(context.projectPath);
      }

      if (!fs.existsSync(this.projectPath)) {
        throw new Error(`Project path does not exist: ${this.projectPath}`);
      }

      // Determine which ACP agent to use from env or default to codex
      const agentId = process.env.AUTODEV_ACP_AGENT || 'codex';
      const preset = ACP_AGENT_PRESETS[agentId];

      if (!preset) {
        throw new Error(
          `Unknown ACP agent: ${agentId}. Available: ${Object.keys(ACP_AGENT_PRESETS).join(', ')}`
        );
      }

      this.agentName = preset.name;

      // Create ACP client with full capabilities
      this.client = new AcpClientConnection(preset.command, preset.args, {}, {
        fs: true,
        terminal: true,
      });

      // Initialize input router for slash commands
      this.router = new InputRouter();
      const slashProcessor = new SlashCommandProcessor();
      this.router.register(slashProcessor, 100);

      // Connect to the agent
      context.logger.info(`[CodexMode] Connecting to ${preset.name} (${preset.command} ${preset.args.join(' ')})...`);
      await this.client.connect(this.projectPath);

      context.logger.info(`[CodexMode] ${preset.name} mode initialized successfully`);

      // Show welcome message
      const welcomeMessage: Message = {
        role: 'system',
        content: `>_ **${preset.name} Mode (ACP) Activated**\n\nProject: \`${this.projectPath}\`\n\nConnected to ${preset.name} via Agent Client Protocol. This session supports multi-turn conversations, streaming, and tool execution.\n\nType \`/agent\` to switch to AI Agent mode, \`/chat\` for chat mode, or \`/help\` for more commands.`,
        timestamp: Date.now(),
        showPrefix: true,
      };

      context.addMessage(welcomeMessage);
    } catch (error) {
      context.logger.error('[CodexMode] Failed to initialize:', error);
      throw error;
    }
  }

  async handleInput(input: string, context: ModeContext): Promise<ModeResult> {
    if (!this.client || !this.router) {
      return {
        success: false,
        error: 'Codex mode not initialized',
      };
    }

    const trimmedInput = input.trim();
    if (!trimmedInput) {
      return {
        success: false,
        error: 'Please provide a prompt',
      };
    }

    try {
      // Handle slash commands first
      const routerContext = {
        clearMessages: context.clearMessages,
        logger: context.logger,
        addMessage: (role: string, content: string) => {
          const message: Message = {
            role: role as any,
            content,
            timestamp: Date.now(),
            showPrefix: true,
          };
          context.addMessage(message);
        },
        setLoading: (_loading: boolean) => {},
        readFile: async (filePath: string) => {
          return fs.readFileSync(filePath, 'utf-8');
        },
      };

      const routeResult = await this.router.route(trimmedInput, routerContext);

      if (routeResult.type === 'handled') {
        if (routeResult.output) {
          context.addMessage({
            role: 'system',
            content: routeResult.output,
            timestamp: Date.now(),
            showPrefix: true,
          });
        }
        return { success: true };
      }

      if (routeResult.type === 'error') {
        return { success: false, error: routeResult.message };
      }

      // Execute prompt via ACP
      if (this.isExecuting) {
        return {
          success: false,
          error: 'Agent is already executing. Please wait or cancel.',
        };
      }

      this.isExecuting = true;

      // Add user message
      context.addMessage({
        role: 'user',
        content: trimmedInput,
        timestamp: Date.now(),
        showPrefix: true,
      });

      // Reset dedup state for new prompt
      this.startedToolCallIds.clear();
      this.toolCallTitles.clear();

      // Set up streaming callbacks
      let responseBuffer = '';
      let hasStartedResponse = false;
      let inThought = false;

      const callbacks: AcpClientCallbacks = {
        onTextChunk: (text: string) => {
          if (!hasStartedResponse) {
            hasStartedResponse = true;
          }
          responseBuffer += text;
          // Update pending message with accumulated response
          context.setPendingMessage({
            role: 'assistant',
            content: responseBuffer,
            timestamp: Date.now(),
            showPrefix: true,
          });
        },

        onThoughtChunk: (text: string) => {
          if (!inThought) {
            inThought = true;
          }
          // Show thoughts as system messages (they tend to be informational)
          context.logger.info(`[${this.agentName}] Thinking: ${text.substring(0, 100)}...`);
        },

        onToolCall: (title: string, status: string, _input?: string, output?: string) => {
          const isTerminal = status === 'completed' || status === 'failed';

          if (isTerminal) {
            const statusIcon = status === 'completed' ? '[OK]' : '[FAIL]';
            const outputSummary = output ? `\n${output.substring(0, 200)}${output.length > 200 ? '...' : ''}` : '';
            context.addMessage({
              role: 'system',
              content: `${statusIcon} **${title}**${outputSummary}`,
              timestamp: Date.now(),
              showPrefix: true,
            });
          } else {
            // Show running tool
            context.addMessage({
              role: 'system',
              content: `[...] **${title}**`,
              timestamp: Date.now(),
              showPrefix: false,
            });
          }
        },

        onPlanUpdate: (entries: Array<{ content: string; status: string }>) => {
          const planText = entries
            .map((e, i) => {
              const marker =
                e.status === 'completed' ? '[x]' : e.status === 'in_progress' ? '[*]' : '[ ]';
              return `${i + 1}. ${marker} ${e.content}`;
            })
            .join('\n');

          context.addMessage({
            role: 'system',
            content: `**Plan Update:**\n${planText}`,
            timestamp: Date.now(),
            showPrefix: true,
          });
        },

        onError: (message: string) => {
          context.addMessage({
            role: 'system',
            content: `Error: ${message}`,
            timestamp: Date.now(),
            showPrefix: true,
          });
        },
      };

      this.client.setCallbacks(callbacks);

      // Send the prompt
      const result = await this.client.prompt(trimmedInput);

      // Finalize the pending message
      if (responseBuffer) {
        context.setPendingMessage(null);
        context.addMessage({
          role: 'assistant',
          content: responseBuffer,
          timestamp: Date.now(),
          showPrefix: true,
        });
      }

      // Add completion message
      const isSuccess = result.stopReason !== 'refusal' && result.stopReason !== 'cancelled';
      context.addMessage({
        role: 'system',
        content: isSuccess
          ? `[OK] **${this.agentName} finished** (${result.stopReason})`
          : `[FAIL] **${this.agentName} stopped** (${result.stopReason})`,
        timestamp: Date.now(),
        showPrefix: true,
      });

      this.isExecuting = false;
      return { success: isSuccess };
    } catch (error) {
      this.isExecuting = false;
      const errorMsg = error instanceof Error ? error.message : String(error);
      context.logger.error('[CodexMode] Execution failed:', error);

      context.setPendingMessage(null);
      context.addMessage({
        role: 'system',
        content: `[FAIL] **Execution failed**: ${errorMsg}`,
        timestamp: Date.now(),
        showPrefix: true,
      });

      return { success: false, error: errorMsg };
    }
  }

  async cleanup(): Promise<void> {
    this.isExecuting = false;
    if (this.client) {
      await this.client.disconnect();
      this.client = null;
    }
    this.router = null;
  }

  getStatus(): string {
    if (this.isExecuting) {
      return `${this.agentName} executing...`;
    }
    const promptCount = this.client?.currentPromptCount ?? 0;
    return `${this.agentName} ready (${path.basename(this.projectPath)}, ${promptCount} prompts)`;
  }
}

/**
 * Codex Mode Factory
 */
export class CodexModeFactory implements ModeFactory {
  readonly type = 'codex';

  createMode(): Mode {
    return new CodexMode();
  }
}
