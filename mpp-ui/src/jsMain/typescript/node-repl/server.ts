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

const SERVER_NAME = 'rmcp';
const SERVER_VERSION = '1.5.0';
const DEFAULT_PROTOCOL_VERSION = '2024-11-05';

const TOOL_DESCRIPTIONS = {
  js: 'Run JavaScript in a persistent Node-backed kernel with top-level await. This is the JavaScript execution tool for the `node_repl` MCP server; use it whenever instructions say to use `node_repl`, the Node REPL MCP, or run Node REPL code. If `timeout_ms` is omitted, execution times out after 30000 ms (30 seconds); pass a larger `timeout_ms` for slow browser automation or other long-running operations. Use `nodeRepl.cwd`, `nodeRepl.homeDir`, and `nodeRepl.tmpDir` to inspect host paths. Use `nodeRepl.requestMeta` to inspect the current MCP request `_meta` object during a tool call. Use `nodeRepl.setResponseMeta(meta)` to attach top-level MCP result `_meta`; repeated calls shallow-merge object keys for the current tool call. Use `nodeRepl.write(text)` when you want exact text output in the tool result; it writes the string exactly as given and does not append a newline. Prefer it over `console.log(...)` for final output, JSON, or other text you plan to consume programmatically. `console.log(...)` is still useful for ad hoc debugging or object inspection because it formats values and appends line breaks automatically. Use `await nodeRepl.emitImage(imageLike)` to return images; each call adds one image to the outer tool result, so call it multiple times to emit multiple images. Supported image inputs are a data URL, inferred PNG/JPEG/WebP bytes, or `{ bytes, mimeType }`. Saved references to `nodeRepl.write(...)` and `nodeRepl.emitImage(...)` stay reusable across calls, but async callbacks that fire after a call finishes still fail because no exec is active. Top-level bindings persist across calls until `js_reset`. If a call throws, prior bindings remain available and bindings that finished initializing before the throw often remain reusable. For reusable names that may be assigned again later, prefer top-level `var name = ...`; `var` can be redeclared across calls. If you hit `SyntaxError: Identifier \'x\' has already been declared`, reuse the existing binding if possible, reassign it only if it was declared with `let` or `var`, or pick a new name instead of resetting immediately; a previous `const x` cannot be changed into `var x`. Use a short `{ ... }` block only for temporary scratch names, and do not wrap an entire call in block scope if you want those names reusable later. Use dynamic imports like `await import("playwright")`, `await import("pkg")`, or `await import("./file.js")`; top-level static `import` is not supported. Import packages by package name after installing them into a directory added with `js_add_node_module_dir`, `NODE_REPL_NODE_MODULE_DIRS`, or the working directory. Do not import package entrypoints by filesystem path such as `./node_modules/playwright/index.mjs`. Imported local files must be ESM `.js` or `.mjs` files and run in the context chosen at their dynamic-import boundary, so they can also use `nodeRepl.*`, the captured `console`, and `import.meta` helpers. Bare package imports always resolve from the REPL-wide search roots (`NODE_REPL_NODE_MODULE_DIRS`, then directories later added with `js_add_node_module_dir`, then cwd), not relative to the imported file\'s location. Imported local files may statically import other local `.js` / `.mjs` files, available packages, and allowed Node builtins. `import.meta.resolve()` returns importable strings such as `file://...`, bare package names, and `node:...` specifiers. Local file modules reload between execs. `node:` builtins are generally available via dynamic import, but `process` / `node:process` remains blocked for now because the current Rust-server-to-Node-child transport runs over stdio and raw process streams can corrupt it. Prefer `nodeRepl.write(text)` for text output and `nodeRepl.emitImage(...)` for images.',
  js_add_node_module_dir: 'Add an absolute `node_modules` directory to the REPL-wide Node module search roots for future package imports. The directory stays available for this MCP server lifetime, including after `js_reset`. Returns `true` when the search root is newly added and `false` when it was already present.',
  js_reset: 'Reset the persistent JavaScript kernel and clear all bindings created by prior `js` calls. Use this when you need a clean state, or when reusing existing bindings, top-level `var` declarations, or fresh names cannot recover from conflicting declarations.',
};

const BASE_INSTRUCTIONS = 'Use `js` to run JavaScript in the persistent Node-backed kernel. When a skill or prompt says to use `node_repl`, call this server\'s `js` execution tool. Calls default to a 30000 ms (30 seconds) timeout when `timeout_ms` is omitted. The runtime exposes `nodeRepl.cwd`, `nodeRepl.homeDir`, `nodeRepl.tmpDir`, `nodeRepl.requestMeta`, `nodeRepl.setResponseMeta(...)`, and `await nodeRepl.emitImage(...)`. Top-level bindings persist across `js` calls until `js_reset`; do not redeclare existing `const` or `let` names. Reuse existing bindings, use top-level `var` for reusable state that may be assigned again, or choose a fresh descriptive name. Use `js_add_node_module_dir` before `js` when a skill provides an extra package directory, and use dynamic imports like `await import("playwright")` rather than filesystem paths under `./node_modules`.';

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
            experimental: {
              'codex/sandbox-state-meta': {},
            },
            tools: {
              listChanged: true,
            },
          },
          instructions: buildInstructions(),
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
          isError: false,
          _meta: result.responseMeta,
        });
        return;
      }

      case 'js_reset':
        runtime.reset();
        sendResult(request.id, {
          content: [{ type: 'text', text: 'js kernel reset' }],
          isError: false,
        });
        return;

      case 'js_add_node_module_dir': {
        const added = runtime.addNodeModuleDir(String(args.path ?? ''));
        sendResult(request.id, {
          content: [{ type: 'text', text: String(added) }],
          isError: false,
        });
        return;
      }

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
        text: formatErrorMessage(error),
      }],
    });
  }
}

function formatErrorMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'message' in error) {
    return String((error as { message: unknown }).message);
  }
  return String(error);
}

function listTools(): Array<Record<string, unknown>> {
  return [
    {
      name: 'js',
      description: TOOL_DESCRIPTIONS.js,
      inputSchema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          code: {
            type: 'string',
            description: 'JavaScript source to execute in the persistent Node-backed kernel. The code runs with top-level await and can use the `nodeRepl` helpers. Examples: `nodeRepl.write(nodeRepl.cwd)`, `const { chromium } = await import("playwright")`, or `await nodeRepl.emitImage(pngBuffer)`.',
          },
          timeout_ms: {
            type: 'integer',
            minimum: 1,
            description: 'Optional execution timeout in milliseconds. Defaults to 30000 (30 seconds) when omitted.',
          },
          title: {
            type: 'string',
            minLength: 1,
            maxLength: 80,
            description: 'Short user-facing description of what this code block is doing. Use a few words, for example `Inspect package metadata` or `Render chart preview`.',
          },
        },
        required: ['code'],
      },
    },
    {
      name: 'js_add_node_module_dir',
      description: TOOL_DESCRIPTIONS.js_add_node_module_dir,
      inputSchema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          path: {
            type: 'string',
            minLength: 1,
            description: 'Absolute path to a node_modules directory to add to Node package resolution.',
          },
        },
        required: ['path'],
      },
    },
    {
      name: 'js_reset',
      description: TOOL_DESCRIPTIONS.js_reset,
      inputSchema: {
        type: 'object',
        additionalProperties: false,
        properties: {},
      },
    },
  ];
}

function buildInstructions(): string {
  const useCases = [
    process.env.NODE_REPL_INSTRUCTIONS_USE_CASE_BROWSER,
    process.env.NODE_REPL_INSTRUCTIONS_USE_CASE_CHROME,
  ].filter((value): value is string => typeof value === 'string' && value.length > 0);

  if (useCases.length === 0) {
    return BASE_INSTRUCTIONS;
  }

  return `${BASE_INSTRUCTIONS}\n\nUse Cases:\n${useCases.map((value) => `- ${value}`).join('\n')}`;
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
