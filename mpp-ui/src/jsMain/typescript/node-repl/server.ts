#!/usr/bin/env node

import * as readline from 'node:readline';
import { spawn } from 'node:child_process';
import { pathToFileURL } from 'node:url';
import { NodeReplRuntime } from './NodeReplRuntime.js';

interface JsonRpcRequest {
  jsonrpc?: string;
  id?: string | number | null;
  method?: string;
  params?: any;
}

interface JsonRpcError {
  code: number;
  message: string;
  data?: unknown;
}

const SERVER_NAME = 'autodev-node-repl';
const SERVER_VERSION = '0.1.0';
const DEFAULT_PROTOCOL_VERSION = '2024-11-05';

const TOOL_DESCRIPTIONS = {
  js: 'Run JavaScript in a persistent Node-backed runtime with common Node globals, nodeRepl.write(text), nodeRepl.env, nodeRepl.fetch(...), nodeRepl.nativePipe.createConnection(...), nodeRepl.setResponseMeta(meta), await nodeRepl.emitImage(...), and dynamic import support when Node runs with --experimental-vm-modules.',
  js_reset: 'Reset the persistent JavaScript runtime context.',
  js_add_node_module_dir: 'Add a directory to the runtime module resolution search path for later dynamic imports.',
  node_runtime_info: 'Return Node.js runtime and module search path diagnostics.',
};

export async function startNodeReplMcpServer(): Promise<void> {
  const runtime = new NodeReplRuntime(process.cwd());
  const input = readline.createInterface({
    input: process.stdin,
    crlfDelay: Infinity,
    terminal: false,
  });

  process.stderr.write(`[${SERVER_NAME}] MCP stdio server started (cwd=${process.cwd()})\n`);

  input.on('line', (line) => {
    void handleLine(runtime, line);
  });

  await new Promise<void>((resolve) => {
    input.on('close', resolve);
  });
}

export function ensureVmModulesFlag(): boolean {
  if (process.execArgv.includes('--experimental-vm-modules')) {
    return true;
  }
  if (process.env.AUTODEV_NODE_REPL_REEXEC === '1') {
    return true;
  }

  const child = spawn(
    process.execPath,
    ['--experimental-vm-modules', ...process.argv.slice(1)],
    {
      env: {
        ...process.env,
        AUTODEV_NODE_REPL_REEXEC: '1',
      },
      stdio: 'inherit',
    },
  );

  child.on('exit', (code, signal) => {
    if (signal) {
      process.kill(process.pid, signal);
      return;
    }
    process.exit(code ?? 1);
  });

  child.on('error', (error) => {
    process.stderr.write(`[${SERVER_NAME}] failed to restart with --experimental-vm-modules: ${error.message}\n`);
    process.exit(1);
  });

  return false;
}

async function handleLine(runtime: NodeReplRuntime, line: string): Promise<void> {
  if (!line.trim()) {
    return;
  }

  let request: JsonRpcRequest;
  try {
    request = JSON.parse(line);
  } catch (error) {
    sendError(null, {
      code: -32700,
      message: `Parse error: ${error instanceof Error ? error.message : String(error)}`,
    });
    return;
  }

  if (!request.method) {
    sendError(request.id ?? null, { code: -32600, message: 'Invalid request: missing method' });
    return;
  }

  try {
    switch (request.method) {
      case 'initialize':
        sendResult(request.id, {
          protocolVersion: request.params?.protocolVersion ?? DEFAULT_PROTOCOL_VERSION,
          capabilities: {
            tools: {},
          },
          serverInfo: {
            name: SERVER_NAME,
            version: SERVER_VERSION,
          },
        });
        return;

      case 'notifications/initialized':
      case 'notifications/cancelled':
        return;

      case 'ping':
        sendResult(request.id, {});
        return;

      case 'tools/list':
        sendResult(request.id, { tools: listTools() });
        return;

      case 'tools/call':
        await handleToolCall(runtime, request);
        return;

      default:
        if (request.id !== undefined) {
          sendError(request.id, { code: -32601, message: `Method not found: ${request.method}` });
        }
    }
  } catch (error) {
    sendError(request.id ?? null, {
      code: -32000,
      message: error instanceof Error ? error.message : String(error),
    });
  }
}

async function handleToolCall(runtime: NodeReplRuntime, request: JsonRpcRequest): Promise<void> {
  const toolName = request.params?.name;
  const args = request.params?.arguments ?? {};

  if (!toolName || typeof toolName !== 'string') {
    sendError(request.id, { code: -32602, message: 'tools/call requires params.name' });
    return;
  }

  try {
    switch (toolName) {
      case 'js': {
        const result = await runtime.execute({
          code: String(args.code ?? ''),
          timeoutMs: typeof args.timeout_ms === 'number' ? args.timeout_ms : undefined,
          requestMeta: request.params?._meta ?? {},
        });
        sendResult(request.id, {
          content: result.content,
          _meta: result.responseMeta,
        });
        return;
      }

      case 'js_reset':
        runtime.reset();
        sendResult(request.id, {
          content: [{ type: 'text', text: 'js kernel reset' }],
        });
        return;

      case 'js_add_node_module_dir': {
        const added = runtime.addNodeModuleDir(String(args.path ?? ''));
        sendResult(request.id, {
          content: [{ type: 'text', text: String(added) }],
        });
        return;
      }

      case 'node_runtime_info':
        sendResult(request.id, {
          content: [{
            type: 'text',
            text: JSON.stringify({
              node: process.version,
              execPath: process.execPath,
              execArgv: process.execArgv,
              cwd: process.cwd(),
              platform: process.platform,
              arch: process.arch,
              moduleDirs: runtime.getModuleDirs(),
            }, null, 2),
          }],
        });
        return;

      default:
        sendResult(request.id, {
          isError: true,
          content: [{ type: 'text', text: `Unknown tool: ${toolName}` }],
        });
    }
  } catch (error) {
    sendResult(request.id, {
      isError: true,
      content: [{
        type: 'text',
        text: error instanceof Error ? `${error.name}: ${error.message}` : String(error),
      }],
    });
  }
}

function listTools(): Array<Record<string, unknown>> {
  return [
    {
      name: 'js',
      description: TOOL_DESCRIPTIONS.js,
      inputSchema: {
        type: 'object',
        properties: {
          code: {
            type: 'string',
            description: 'JavaScript source to execute.',
          },
          timeout_ms: {
            type: 'number',
            description: 'Optional execution timeout in milliseconds. Defaults to 30000.',
          },
          title: {
            type: 'string',
            description: 'Optional short description for clients to display.',
          },
        },
        required: ['code'],
      },
    },
    {
      name: 'js_reset',
      description: TOOL_DESCRIPTIONS.js_reset,
      inputSchema: {
        type: 'object',
        properties: {},
      },
    },
    {
      name: 'js_add_node_module_dir',
      description: TOOL_DESCRIPTIONS.js_add_node_module_dir,
      inputSchema: {
        type: 'object',
        properties: {
          path: {
            type: 'string',
            description: 'Absolute path to a node_modules directory.',
          },
        },
        required: ['path'],
      },
    },
    {
      name: 'node_runtime_info',
      description: TOOL_DESCRIPTIONS.node_runtime_info,
      inputSchema: {
        type: 'object',
        properties: {},
      },
    },
  ];
}

function sendResult(id: JsonRpcRequest['id'], result: unknown): void {
  if (id === undefined) {
    return;
  }
  process.stdout.write(JSON.stringify({
    jsonrpc: '2.0',
    id,
    result,
  }) + '\n');
}

function sendError(id: JsonRpcRequest['id'], error: JsonRpcError): void {
  process.stdout.write(JSON.stringify({
    jsonrpc: '2.0',
    id,
    error,
  }) + '\n');
}

const entrypoint = process.argv[1] ? pathToFileURL(process.argv[1]).href : '';
if (import.meta.url === entrypoint && ensureVmModulesFlag()) {
  startNodeReplMcpServer().catch((error) => {
    process.stderr.write(`[${SERVER_NAME}] fatal error: ${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
    process.exit(1);
  });
}
