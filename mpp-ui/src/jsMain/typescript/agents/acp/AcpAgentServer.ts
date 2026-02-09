/**
 * ACP Agent Server for the JS/Node.js CLI.
 *
 * This exposes our CodingAgent as an ACP-compliant agent that can be connected to
 * by any ACP client (e.g., JetBrains IDEA, VSCode, Zed, or other editors).
 *
 * Communication happens over STDIO using JSON-RPC (newline-delimited JSON).
 *
 * Usage:
 *   xiuper acp
 *   # Now other editors can connect to this agent via its stdin/stdout
 */

import * as acp from '@agentclientprotocol/sdk';
import { Readable, Writable } from 'node:stream';
import { ConfigManager } from '../../config/ConfigManager.js';
import mppCore from '@xiuper/mpp-core';
import * as path from 'path';
import * as fs from 'fs';

const { cc: KotlinCC } = mppCore;

/**
 * Session state for each ACP session.
 */
interface AgentSessionState {
  sessionId: string;
  cwd: string;
  agent: any; // JsCodingAgent instance
  abortController: AbortController;
}

/**
 * ACP Agent implementation that wraps our CodingAgent.
 */
class AutoDevAcpAgent implements acp.Agent {
  private connection: acp.AgentSideConnection;
  private sessions = new Map<string, AgentSessionState>();

  constructor(connection: acp.AgentSideConnection) {
    this.connection = connection;
  }

  async initialize(params: acp.InitializeRequest): Promise<acp.InitializeResponse> {
    console.error('[ACP Agent] Client connected:', params.clientInfo?.name);
    return {
      protocolVersion: acp.PROTOCOL_VERSION,
      agentCapabilities: {
        loadSession: false,
        promptCapabilities: {},
      },
      agentInfo: {
        name: 'autodev-xiuper',
        version: '3.0.0',
        title: 'AutoDev Xiuper (ACP Agent)',
      },
    };
  }

  async newSession(params: acp.NewSessionRequest): Promise<acp.NewSessionResponse> {
    const sessionId = `autodev-${Date.now()}`;
    const cwd = params.cwd;

    console.error(`[ACP Agent] New session: ${sessionId} (cwd=${cwd})`);
    console.error(`[ACP Agent] MCP servers from client: ${params.mcpServers.length}`);

    // Load config
    const config = await ConfigManager.load();
    const activeConfig = config.getActiveConfig();

    if (!activeConfig) {
      throw new Error('No active LLM configuration found. Please configure your LLM provider first.');
    }

    // Create LLM service
    const llmService = new KotlinCC.unitmesh.llm.JsKoogLLMService(
      new KotlinCC.unitmesh.llm.JsModelConfig(
        activeConfig.provider,
        activeConfig.model,
        activeConfig.apiKey || '',
        activeConfig.temperature || 0.7,
        activeConfig.maxTokens || 8192,
        activeConfig.baseUrl || ''
      )
    );

    // Convert ACP MCP servers to our internal format
    const enabledMcpServers: Record<string, any> = {};
    for (const mcpServer of params.mcpServers) {
      // Only support stdio transport for now
      if ('command' in mcpServer) {
        const name = (mcpServer as any).name || mcpServer.command;
        enabledMcpServers[name] = {
          command: mcpServer.command,
          args: mcpServer.args || [],
          env: (mcpServer as any).env || {},
        };
        console.error(`[ACP Agent] Registered MCP server: ${name} (${mcpServer.command})`);
      } else {
        console.error(`[ACP Agent] Skipping non-stdio MCP server (not supported yet)`);
      }
    }

    // Create a renderer that sends updates via ACP
    const renderer = new AcpSessionRenderer(this.connection, sessionId);

    // Create CodingAgent
    const agent = new KotlinCC.unitmesh.agent.JsCodingAgent(
      cwd,
      llmService,
      10, // maxIterations
      renderer,
      Object.keys(enabledMcpServers).length > 0 ? enabledMcpServers : null,
      null // toolConfig
    );

    this.sessions.set(sessionId, {
      sessionId,
      cwd,
      agent,
      abortController: new AbortController(),
    });

    return { sessionId };
  }

  async prompt(params: acp.PromptRequest): Promise<acp.PromptResponse> {
    const session = this.sessions.get(params.sessionId);
    if (!session) {
      throw acp.RequestError.invalidParams(`Unknown session: ${params.sessionId}`);
    }

    // Extract text from content blocks
    const textParts: string[] = [];
    for (const block of params.prompt) {
      if (block.type === 'text') {
        textParts.push((block as any).text);
      }
    }
    const promptText = textParts.join('\n');

    console.error(`[ACP Agent] Prompt in session ${params.sessionId}: ${promptText.substring(0, 100)}...`);

    try {
      // Create task and execute
      const task = new KotlinCC.unitmesh.agent.JsAgentTask(promptText, session.cwd);
      const result = await session.agent.executeTask(task);

      return {
        stopReason: result.success ? 'end_turn' : 'end_turn',
      };
    } catch (error) {
      console.error('[ACP Agent] Prompt error:', error);

      // Send error as agent message
      await this.connection.sessionUpdate({
        sessionId: params.sessionId,
        update: {
          sessionUpdate: 'agent_message_chunk',
          content: {
            type: 'text',
            text: `Error: ${error instanceof Error ? error.message : String(error)}`,
          },
        },
      });

      return { stopReason: 'end_turn' };
    }
  }

  async cancel(params: acp.CancelNotification): Promise<void> {
    const session = this.sessions.get(params.sessionId);
    if (session) {
      session.abortController.abort();
      console.error(`[ACP Agent] Session cancelled: ${params.sessionId}`);
    }
  }

  // Optional methods - not needed for basic operation
  async authenticate(params: any): Promise<any> {
    throw acp.RequestError.methodNotFound('authenticate');
  }
}

/**
 * Renderer that sends all output as ACP session updates.
 * This bridges our internal renderer to the ACP protocol.
 */
class AcpSessionRenderer {
  readonly __doNotUseOrImplementIt: any = {};

  private connection: acp.AgentSideConnection;
  private sessionId: string;
  private toolCallCounter = 0;

  constructor(connection: acp.AgentSideConnection, sessionId: string) {
    this.connection = connection;
    this.sessionId = sessionId;
  }

  renderIterationHeader(current: number, max: number): void {
    // Send as thought
    this.sendThought(`Iteration ${current}/${max}`);
  }

  renderLLMResponseStart(): void {
    // No-op, chunks will follow
  }

  renderLLMResponseChunk(chunk: string): void {
    if (chunk) {
      this.sendTextChunk(chunk);
    }
  }

  renderLLMResponseEnd(): void {
    // No-op
  }

  renderThinkingChunk(chunk: string, isStart: boolean, isEnd: boolean): void {
    if (chunk) {
      this.sendThought(chunk);
    }
  }

  renderToolCall(toolName: string, paramsStr: string): void {
    this.toolCallCounter++;
    this.sendToolCall(toolName, 'in_progress', paramsStr);
  }

  renderToolCallWithParams(toolName: string, params: Record<string, any>): void {
    this.toolCallCounter++;
    const paramsStr = Object.entries(params)
      .map(([k, v]) => `${k}=${v}`)
      .join(', ');
    this.sendToolCall(toolName, 'in_progress', paramsStr);
  }

  renderToolResult(
    toolName: string,
    success: boolean,
    output: string | null,
    fullOutput?: string | null,
    metadata?: Record<string, string>
  ): void {
    this.sendToolCall(
      toolName,
      success ? 'completed' : 'errored',
      undefined,
      output || fullOutput || undefined
    );
  }

  renderTaskComplete(executionTimeMs?: number, toolsUsedCount?: number): void {
    this.sendTextChunk(`\nTask completed in ${executionTimeMs || 0}ms using ${toolsUsedCount || 0} tools.`);
  }

  renderFinalResult(success: boolean, message: string, iterations: number): void {
    // Final result is implicit in the prompt response
  }

  renderError(message: string): void {
    this.sendTextChunk(`\nError: ${message}`);
  }

  renderRepeatWarning(toolName: string, count: number): void {
    this.sendThought(`Warning: Tool '${toolName}' called ${count} times consecutively`);
  }

  renderRecoveryAdvice(recoveryAdvice: string): void {
    this.sendTextChunk(`\nRecovery advice: ${recoveryAdvice}`);
  }

  renderUserConfirmationRequest(toolName: string, params: Record<string, any>): void {
    // Auto-approve in ACP mode
  }

  renderPlanSummary(summary: any): void {
    // Could emit plan update here if needed
  }

  renderAgentSketchBlock(
    agentName: string,
    language: string,
    code: string,
    metadata: Record<string, string>
  ): void {
    this.sendTextChunk(`\n\`\`\`${language}\n${code}\n\`\`\`\n`);
  }

  addLiveTerminal(sessionId: string, command: string, workingDirectory?: string | null, ptyHandle?: any): void {
    // No-op in ACP mode
  }

  // -- Private helpers --

  private sendTextChunk(text: string): void {
    this.connection.sessionUpdate({
      sessionId: this.sessionId,
      update: {
        sessionUpdate: 'agent_message_chunk',
        content: { type: 'text', text },
      },
    }).catch(err => console.error('[ACP Renderer] Failed to send text chunk:', err));
  }

  private sendThought(text: string): void {
    this.connection.sessionUpdate({
      sessionId: this.sessionId,
      update: {
        sessionUpdate: 'agent_thought_chunk',
        content: { type: 'text', text },
      },
    }).catch(err => console.error('[ACP Renderer] Failed to send thought:', err));
  }

  private sendToolCall(title: string, status: string, input?: string, output?: string): void {
    this.connection.sessionUpdate({
      sessionId: this.sessionId,
      update: {
        sessionUpdate: 'tool_call',
        toolCallId: `tc-${this.toolCallCounter}`,
        title,
        status,
        kind: 'other',
        ...(input ? { rawInput: input } : {}),
        ...(output ? { rawOutput: output } : {}),
      } as any,
    }).catch(err => console.error('[ACP Renderer] Failed to send tool call:', err));
  }
}

/**
 * Start the ACP agent server on stdin/stdout.
 * This is the entry point for `xiuper acp` command.
 */
export async function startAcpAgentServer(): Promise<void> {
  // CRITICAL: Redirect all console output to stderr BEFORE any Kotlin code runs
  // This prevents Kotlin logging from polluting stdout (which is used for JSON-RPC)
  redirectConsoleToStderr();

  console.error('[ACP Agent] Starting AutoDev Xiuper ACP Agent Server...');

  // For ACP ndJsonStream: first param is output (where we write), second is input (where we read)
  // We write JSON responses to stdout, read JSON requests from stdin
  const output = Writable.toWeb(process.stdout) as WritableStream<Uint8Array>;
  const input = Readable.toWeb(process.stdin) as ReadableStream<Uint8Array>;

  const stream = acp.ndJsonStream(output, input);

  const connection = new acp.AgentSideConnection(
    (conn) => new AutoDevAcpAgent(conn),
    stream
  );

  console.error('[ACP Agent] Server started. Waiting for client connections on stdio...');

  // Wait for the connection to close
  await connection.closed;
  console.error('[ACP Agent] Connection closed.');
}

/**
 * Redirect all console methods to stderr.
 * This is critical for ACP mode where stdout is reserved for JSON-RPC messages.
 */
function redirectConsoleToStderr(): void {
  // Save original console.error
  const originalError = console.error;

  // Redirect all console methods to stderr
  console.log = originalError;
  console.info = originalError;
  console.warn = originalError;
  console.debug = originalError;

  // console.error already goes to stderr
}
