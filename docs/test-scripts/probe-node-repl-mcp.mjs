#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)));
const serverPath = resolve(repoRoot, 'mpp-ui/dist/jsMain/typescript/node-repl/server.js');
const wrapperPath = resolve(repoRoot, 'mpp-ui/bin/autodev-node-repl');

if (!existsSync(serverPath)) {
  console.error(`node_repl server is not built: ${serverPath}`);
  console.error('Run: cd mpp-ui && npm run build:ts');
  process.exit(1);
}

const command = process.env.AUTODEV_NODE_REPL_COMMAND || wrapperPath;
const args = process.env.AUTODEV_NODE_REPL_COMMAND ? [] : [];

const child = spawn(command, args, {
  cwd: repoRoot,
  stdio: ['pipe', 'pipe', 'pipe'],
});

const pending = new Map();
let nextId = 1;
let stderr = '';

child.stderr.on('data', (chunk) => {
  stderr += chunk.toString('utf8');
});

const rl = createInterface({ input: child.stdout });
rl.on('line', (line) => {
  const message = JSON.parse(line);
  const waiter = pending.get(message.id);
  if (!waiter) {
    return;
  }
  pending.delete(message.id);
  waiter.resolve(message);
});

function request(method, params = {}) {
  const id = nextId++;
  const payload = { jsonrpc: '2.0', id, method, params };
  child.stdin.write(JSON.stringify(payload) + '\n');
  return new Promise((resolvePromise, reject) => {
    const timeout = setTimeout(() => {
      pending.delete(id);
      reject(new Error(`Timed out waiting for ${method}; stderr=${stderr}`));
    }, 5000);
    pending.set(id, {
      resolve: (value) => {
        clearTimeout(timeout);
        resolvePromise(value);
      },
    });
  });
}

function notify(method, params = {}) {
  child.stdin.write(JSON.stringify({ jsonrpc: '2.0', method, params }) + '\n');
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function textOf(response) {
  return response.result?.content?.filter((item) => item.type === 'text').map((item) => item.text).join('') ?? '';
}

try {
  const init = await request('initialize', {
    protocolVersion: '2024-11-05',
    clientInfo: { name: 'probe-node-repl-mcp', version: '0.1.0' },
    capabilities: {},
  });
  assert(init.result?.serverInfo?.name === 'autodev-node-repl', 'initialize did not return serverInfo');
  notify('notifications/initialized');

  const tools = await request('tools/list');
  const toolNames = new Set(tools.result.tools.map((tool) => tool.name));
  assert(toolNames.has('js'), 'js tool missing');
  assert(toolNames.has('js_reset'), 'js_reset tool missing');
  assert(toolNames.has('js_add_node_module_dir'), 'js_add_node_module_dir tool missing');

  const simple = await request('tools/call', {
    name: 'js',
    arguments: { code: 'const answer = 41; answer + 1' },
  });
  assert(textOf(simple) === '42', `unexpected simple eval result: ${textOf(simple)}`);

  const persisted = await request('tools/call', {
    name: 'js',
    arguments: { code: 'answer + 2' },
  });
  assert(textOf(persisted) === '43', `persistent binding failed: ${textOf(persisted)}`);

  const topLevelAwait = await request('tools/call', {
    name: 'js',
    arguments: { code: 'await import("node:os").then((os) => os.platform())' },
  });
  assert(textOf(topLevelAwait).length > 0, 'top-level await dynamic import returned empty output');

  const write = await request('tools/call', {
    name: 'js',
    arguments: { code: 'nodeRepl.write("cwd=" + nodeRepl.cwd)' },
  });
  assert(textOf(write).startsWith('cwd='), `nodeRepl.write failed: ${textOf(write)}`);

  const bundledModules = await request('tools/call', {
    name: 'js',
    arguments: { code: 'await import("pngjs").then((mod) => typeof mod.PNG)' },
  });
  assert(textOf(bundledModules) === 'function', `bundled node_modules import failed: ${textOf(bundledModules)}`);

  await request('tools/call', {
    name: 'js_reset',
    arguments: {},
  });

  const reset = await request('tools/call', {
    name: 'js',
    arguments: { code: 'typeof answer' },
  });
  assert(textOf(reset) === 'undefined', `js_reset did not clear context: ${textOf(reset)}`);

  child.stdin.end();
  child.kill();
  console.log('node_repl MCP probe passed');
} catch (error) {
  child.kill();
  console.error(error);
  process.exit(1);
}
