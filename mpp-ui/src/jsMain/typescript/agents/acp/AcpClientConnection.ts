/**
 * ACP Client Connection for the JS/Node.js CLI.
 *
 * This allows our CLI to connect to external ACP agents (e.g., Claude CLI, Gemini CLI)
 * and interact with them through the standardized ACP protocol.
 *
 * Usage:
 *   const client = new AcpClientConnection('claude', ['--acp']);
 *   await client.connect('/path/to/project');
 *   const result = await client.prompt('Fix the bug in main.ts');
 */

import * as acp from '@agentclientprotocol/sdk';
import { spawn, ChildProcess } from 'node:child_process';
import { Readable, Writable } from 'node:stream';

/**
 * Callback interface for receiving ACP session updates.
 */
export interface AcpClientCallbacks {
  onTextChunk?: (text: string) => void;
  onThoughtChunk?: (text: string) => void;
  onToolCall?: (title: string, status: string, input?: string, output?: string) => void;
  onPlanUpdate?: (entries: Array<{ content: string; status: string }>) => void;
  onError?: (message: string) => void;
}

/**
 * ACP Client that connects to an external ACP agent process.
 */
export class AcpClientConnection {
  private command: string;
  private args: string[];
  private env: Record<string, string>;
  private connection: acp.ClientSideConnection | null = null;
  private agentProcess: ChildProcess | null = null;
  private callbacks: AcpClientCallbacks = {};
  private currentSessionId: string | null = null;

  constructor(
    command: string,
    args: string[] = [],
    env: Record<string, string> = {}
  ) {
    this.command = command;
    this.args = args;
    this.env = env;
  }

  /**
   * Set callbacks for receiving updates from the agent.
   */
  setCallbacks(callbacks: AcpClientCallbacks): void {
    this.callbacks = callbacks;
  }

  /**
   * Connect to the ACP agent: spawn the process, initialize protocol, create session.
   */
  async connect(cwd: string): Promise<void> {
    // Spawn the agent process
    console.error(`[ACP Client] Spawning agent: ${this.command} ${this.args.join(' ')}`);

    this.agentProcess = spawn(this.command, this.args, {
      stdio: ['pipe', 'pipe', 'inherit'],
      cwd,
      env: { ...process.env, ...this.env },
    });

    if (!this.agentProcess.stdin || !this.agentProcess.stdout) {
      throw new Error('Failed to get stdio handles from agent process');
    }

    // Create ACP stream from agent's stdio
    const input = Writable.toWeb(this.agentProcess.stdin) as WritableStream;
    const output = Readable.toWeb(this.agentProcess.stdout) as ReadableStream;
    const stream = acp.ndJsonStream(input, output);

    // Create the client-side connection
    this.connection = new acp.ClientSideConnection(
      (agent) => new AutoDevAcpClient(agent, this.callbacks),
      stream
    );

    // Initialize the connection
    const initResult = await this.connection.initialize({
      protocolVersion: acp.PROTOCOL_VERSION,
      clientInfo: {
        name: 'autodev-xiuper',
        version: '3.0.0',
        title: 'AutoDev Xiuper CLI (ACP Client)',
      },
      clientCapabilities: {
        fs: {
          readTextFile: false,
          writeTextFile: false,
        },
        terminal: false,
      },
    });

    console.error(
      `[ACP Client] Connected to agent: ${initResult.agentInfo?.name} v${initResult.agentInfo?.version}`
    );

    // Create a session
    const sessionResult = await this.connection.newSession({
      cwd,
      mcpServers: [],
    });

    this.currentSessionId = sessionResult.sessionId;
    console.error(`[ACP Client] Session created: ${this.currentSessionId}`);
  }

  /**
   * Send a prompt to the agent and wait for completion.
   */
  async prompt(text: string): Promise<{ stopReason: string }> {
    if (!this.connection || !this.currentSessionId) {
      throw new Error('ACP client not connected');
    }

    const result = await this.connection.prompt({
      sessionId: this.currentSessionId,
      prompt: [
        {
          type: 'text',
          text,
        },
      ],
    });

    return { stopReason: result.stopReason };
  }

  /**
   * Cancel the current prompt.
   */
  async cancel(): Promise<void> {
    if (this.connection && this.currentSessionId) {
      await this.connection.cancel({
        sessionId: this.currentSessionId,
      });
    }
  }

  /**
   * Disconnect from the agent.
   */
  async disconnect(): Promise<void> {
    if (this.agentProcess) {
      this.agentProcess.kill();
      this.agentProcess = null;
    }
    this.connection = null;
    this.currentSessionId = null;
    console.error('[ACP Client] Disconnected.');
  }

  /**
   * Check if the client is connected.
   */
  get isConnected(): boolean {
    return this.connection !== null && this.currentSessionId !== null;
  }
}

/**
 * Internal ACP Client implementation that receives updates from the agent.
 */
class AutoDevAcpClient implements acp.Client {
  private callbacks: AcpClientCallbacks;

  constructor(_agent: acp.Agent, callbacks: AcpClientCallbacks) {
    this.callbacks = callbacks;
  }

  async requestPermission(
    params: acp.RequestPermissionRequest
  ): Promise<acp.RequestPermissionResponse> {
    // Auto-approve the first option for now
    const firstOption = params.options?.[0];
    return {
      outcome: firstOption
        ? { outcome: 'selected', optionId: firstOption.optionId }
        : { outcome: 'cancelled' },
    };
  }

  async sessionUpdate(params: acp.SessionNotification): Promise<void> {
    const update = params.update;
    if (!update) return;

    switch ((update as any).sessionUpdate) {
      case 'agent_message_chunk': {
        const content = (update as any).content;
        if (content?.type === 'text' && content.text) {
          this.callbacks.onTextChunk?.(content.text);
        }
        break;
      }
      case 'agent_thought_chunk': {
        const content = (update as any).content;
        if (content?.type === 'text' && content.text) {
          this.callbacks.onThoughtChunk?.(content.text);
        }
        break;
      }
      case 'tool_call':
      case 'tool_call_update': {
        this.callbacks.onToolCall?.(
          (update as any).title || 'tool',
          (update as any).status || 'unknown',
          (update as any).rawInput?.toString(),
          (update as any).rawOutput?.toString()
        );
        break;
      }
      case 'plan': {
        const entries = (update as any).entries || [];
        this.callbacks.onPlanUpdate?.(entries);
        break;
      }
      default:
        // Ignore unknown update types
        break;
    }
  }
}
