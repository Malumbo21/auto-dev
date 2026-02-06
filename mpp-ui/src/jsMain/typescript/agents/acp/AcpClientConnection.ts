/**
 * ACP Client Connection for the JS/Node.js CLI.
 *
 * This allows our CLI to connect to external ACP agents (e.g., Claude CLI, Gemini CLI, Codex)
 * and interact with them through the standardized ACP protocol.
 *
 * Features:
 * - Process lifecycle management with graceful shutdown
 * - fs capabilities (readTextFile/writeTextFile) for agent file access
 * - Terminal capability for agent shell execution
 * - Session reuse for multi-turn prompts
 * - Tool call deduplication (ACP streams many IN_PROGRESS updates)
 *
 * Usage:
 *   const client = new AcpClientConnection('codex', ['--acp']);
 *   await client.connect('/path/to/project');
 *   const result = await client.prompt('Fix the bug in main.ts');
 *   // Multi-turn: send another prompt in the same session
 *   const result2 = await client.prompt('Now add tests');
 *   await client.disconnect();
 */

import * as acp from '@agentclientprotocol/sdk';
import { spawn, ChildProcess } from 'node:child_process';
import { Readable, Writable } from 'node:stream';
import * as fs from 'node:fs';
import * as path from 'node:path';

/**
 * Callback interface for receiving ACP session updates.
 */
export interface AcpClientCallbacks {
  onTextChunk?: (text: string) => void;
  onThoughtChunk?: (text: string) => void;
  onToolCall?: (title: string, status: string, input?: string, output?: string) => void;
  onPlanUpdate?: (entries: Array<{ content: string; status: string }>) => void;
  onModeUpdate?: (modeId: string) => void;
  onError?: (message: string) => void;
  /** Called when permission is requested. Return true to approve, false to deny. */
  onPermissionRequest?: (toolTitle: string, options: string[]) => Promise<boolean>;
}

/**
 * Configuration for ACP client capabilities.
 */
export interface AcpClientCapabilities {
  /** Enable fs.readTextFile and fs.writeTextFile for the agent */
  fs?: boolean;
  /** Enable terminal operations for the agent */
  terminal?: boolean;
}

/**
 * ACP Client that connects to an external ACP agent process.
 *
 * Supports process reuse, multi-turn prompts, and tool call dedup.
 */
export class AcpClientConnection {
  private command: string;
  private args: string[];
  private env: Record<string, string>;
  private clientCapabilities: AcpClientCapabilities;
  private connection: acp.ClientSideConnection | null = null;
  private agentProcess: ChildProcess | null = null;
  private callbacks: AcpClientCallbacks = {};
  private currentSessionId: string | null = null;
  private currentCwd: string | null = null;
  private promptCount = 0;

  constructor(
    command: string,
    args: string[] = [],
    env: Record<string, string> = {},
    capabilities: AcpClientCapabilities = { fs: true, terminal: true },
  ) {
    this.command = command;
    this.args = args;
    this.env = env;
    this.clientCapabilities = capabilities;
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
    // If already connected to the same cwd, reuse the session
    if (this.isConnected && this.currentCwd === cwd) {
      console.error(`[ACP Client] Reusing existing connection for ${cwd}`);
      return;
    }

    // Disconnect existing connection if switching cwd
    if (this.isConnected) {
      await this.disconnect();
    }

    this.currentCwd = cwd;

    // Spawn the agent process
    console.error(`[ACP Client] Spawning agent: ${this.command} ${this.args.join(' ')}`);

    this.agentProcess = spawn(this.command, this.args, {
      stdio: ['pipe', 'pipe', 'inherit'],
      cwd,
      env: { ...process.env, ...this.env },
    });

    // Handle process exit
    this.agentProcess.on('exit', (code, signal) => {
      console.error(`[ACP Client] Agent process exited (code=${code}, signal=${signal})`);
      this.connection = null;
      this.currentSessionId = null;
    });

    if (!this.agentProcess.stdin || !this.agentProcess.stdout) {
      throw new Error('Failed to get stdio handles from agent process');
    }

    // Create ACP stream from agent's stdio
    const output = Writable.toWeb(this.agentProcess.stdin) as WritableStream<Uint8Array>;
    const input = Readable.toWeb(this.agentProcess.stdout) as ReadableStream<Uint8Array>;
    const stream = acp.ndJsonStream(output, input);

    // Create the client-side connection with capabilities
    const enableFs = this.clientCapabilities.fs ?? true;
    const enableTerminal = this.clientCapabilities.terminal ?? true;

    this.connection = new acp.ClientSideConnection(
      (agent) => new AutoDevAcpClient(agent, this.callbacks, cwd, enableFs),
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
          readTextFile: enableFs,
          writeTextFile: enableFs,
        },
        terminal: enableTerminal,
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
    this.promptCount = 0;
    console.error(`[ACP Client] Session created: ${this.currentSessionId}`);
  }

  /**
   * Send a prompt to the agent and wait for completion.
   * Supports multi-turn: call this multiple times within the same session.
   */
  async prompt(text: string): Promise<{ stopReason: string }> {
    if (!this.connection || !this.currentSessionId) {
      throw new Error('ACP client not connected');
    }

    this.promptCount++;

    try {
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
    } catch (e) {
      const error = e instanceof Error ? e : new Error(String(e));
      console.error(`[ACP Client] Prompt failed: ${error.message}`);
      this.callbacks.onError?.(`ACP prompt failed: ${error.message}`);
      throw error;
    }
  }

  /**
   * Cancel the current prompt.
   */
  async cancel(): Promise<void> {
    if (this.connection && this.currentSessionId) {
      try {
        await this.connection.cancel({
          sessionId: this.currentSessionId,
        });
      } catch (e) {
        console.error(`[ACP Client] Cancel failed: ${e}`);
      }
    }
  }

  /**
   * Disconnect from the agent and clean up the process.
   */
  async disconnect(): Promise<void> {
    if (this.agentProcess) {
      // Graceful shutdown: SIGTERM first
      this.agentProcess.kill('SIGTERM');

      // Wait briefly for graceful exit, then force kill
      await new Promise<void>((resolve) => {
        const timeout = setTimeout(() => {
          if (this.agentProcess) {
            this.agentProcess.kill('SIGKILL');
          }
          resolve();
        }, 3000);

        if (this.agentProcess) {
          this.agentProcess.on('exit', () => {
            clearTimeout(timeout);
            resolve();
          });
        } else {
          clearTimeout(timeout);
          resolve();
        }
      });

      this.agentProcess = null;
    }
    this.connection = null;
    this.currentSessionId = null;
    this.currentCwd = null;
    this.promptCount = 0;
    console.error('[ACP Client] Disconnected.');
  }

  /**
   * Check if the client is connected and the process is alive.
   */
  get isConnected(): boolean {
    return (
      this.connection !== null &&
      this.currentSessionId !== null &&
      this.agentProcess !== null &&
      !this.agentProcess.killed
    );
  }

  /**
   * Get the number of prompts sent in the current session.
   */
  get currentPromptCount(): number {
    return this.promptCount;
  }
}

/**
 * Internal ACP Client implementation that receives updates from the agent.
 *
 * Implements tool call dedup: ACP sends many IN_PROGRESS updates per tool call
 * (title grows char-by-char). We only forward the first and terminal events.
 */
class AutoDevAcpClient implements acp.Client {
  private callbacks: AcpClientCallbacks;
  private cwd: string;
  private enableFs: boolean;
  /** Tool call title tracking for deduplication */
  private toolCallTitles = new Map<string, string>();
  private startedToolCallIds = new Set<string>();

  constructor(
    _agent: acp.Agent,
    callbacks: AcpClientCallbacks,
    cwd: string,
    enableFs: boolean,
  ) {
    this.callbacks = callbacks;
    this.cwd = cwd;
    this.enableFs = enableFs;
  }

  async requestPermission(
    params: acp.RequestPermissionRequest
  ): Promise<acp.RequestPermissionResponse> {
    const toolTitle = (params as any).toolCall?.title || 'tool';
    const optionNames = (params.options || []).map((o: any) => o.name || o.kind || 'unknown');

    // If a callback is set, let the host decide
    if (this.callbacks.onPermissionRequest) {
      const approved = await this.callbacks.onPermissionRequest(toolTitle, optionNames);
      if (approved) {
        const allowOption = params.options?.find(
          (o: any) => o.kind === 'allow_once' || o.kind === 'allow_always'
        );
        if (allowOption) {
          return {
            outcome: { outcome: 'selected', optionId: allowOption.optionId },
          };
        }
      }
      return { outcome: { outcome: 'cancelled' } };
    }

    // Default: auto-approve the first allow option
    const firstAllowOption = params.options?.find(
      (o: any) => o.kind === 'allow_once' || o.kind === 'allow_always'
    );
    const firstOption = firstAllowOption || params.options?.[0];
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
        const toolCallId = (update as any).toolCallId || '';
        const title = (update as any).title || '';
        const status = (update as any).status || 'unknown';

        // Track best title (grows char-by-char in ACP streaming)
        if (toolCallId && title) {
          this.toolCallTitles.set(toolCallId, title);
        }

        const isTerminal = status === 'completed' || status === 'failed';
        const isRunning = status === 'in_progress' || status === 'pending';

        if (isRunning && toolCallId && !this.startedToolCallIds.has(toolCallId)) {
          // First event for this tool call - render it
          this.startedToolCallIds.add(toolCallId);
          this.callbacks.onToolCall?.(
            this.toolCallTitles.get(toolCallId) || title || 'tool',
            status,
            (update as any).rawInput?.toString(),
            (update as any).rawOutput?.toString()
          );
        } else if (isTerminal) {
          // Terminal event - render result with best-known title
          const bestTitle = (toolCallId ? this.toolCallTitles.get(toolCallId) : null) || title || 'tool';
          this.callbacks.onToolCall?.(
            bestTitle,
            status,
            (update as any).rawInput?.toString(),
            (update as any).rawOutput?.toString()
          );
          // Clean up tracking
          if (toolCallId) {
            this.toolCallTitles.delete(toolCallId);
            this.startedToolCallIds.delete(toolCallId);
          }
        }
        // Skip intermediate IN_PROGRESS updates (title streaming) to avoid spam
        break;
      }
      case 'plan': {
        const entries = (update as any).entries || [];
        this.callbacks.onPlanUpdate?.(entries);
        break;
      }
      case 'current_mode': {
        const modeId = (update as any).currentModeId;
        if (modeId) {
          this.callbacks.onModeUpdate?.(String(modeId));
        }
        break;
      }
      default:
        // Ignore unknown update types
        break;
    }
  }

  // ── File System Operations ──────────────────────────────────────

  async readTextFile(params: {
    path: string;
    line?: number;
    limit?: number;
  }): Promise<{ content: string; _meta?: Record<string, unknown> | null }> {
    if (!this.enableFs) {
      throw new Error('fs.readTextFile is disabled');
    }

    const resolved = this.resolvePath(params.path);
    console.error(`[ACP Client] fs.readTextFile: ${resolved}`);

    const content = fs.readFileSync(resolved, 'utf-8');
    const lines = content.split('\n');

    const startLine = Math.max(0, (params.line ?? 1) - 1);
    const lineLimit = params.limit ?? lines.length - startLine;

    const sliced = lines.slice(startLine, startLine + lineLimit).join('\n');
    return { content: sliced };
  }

  async writeTextFile(params: {
    path: string;
    content: string;
  }): Promise<{ _meta?: Record<string, unknown> | null }> {
    if (!this.enableFs) {
      throw new Error('fs.writeTextFile is disabled');
    }

    const resolved = this.resolvePath(params.path);
    console.error(`[ACP Client] fs.writeTextFile: ${resolved} (${params.content.length} chars)`);

    // Ensure parent directory exists
    const dir = path.dirname(resolved);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }

    fs.writeFileSync(resolved, params.content, 'utf-8');
    return {};
  }

  // ── Helpers ─────────────────────────────────────────────────────

  private resolvePath(filePath: string): string {
    if (path.isAbsolute(filePath)) {
      return filePath;
    }
    return path.resolve(this.cwd, filePath);
  }
}
