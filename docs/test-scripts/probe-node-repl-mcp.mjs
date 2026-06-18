#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { existsSync } from 'node:fs';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { tmpdir } from 'node:os';
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

function imageOf(response) {
  return response.result?.content?.find((item) => item.type === 'image') ?? null;
}

try {
  const init = await request('initialize', {
    protocolVersion: '2024-11-05',
    clientInfo: { name: 'probe-node-repl-mcp', version: '0.1.0' },
    capabilities: {},
  });
  assert(init.result?.serverInfo?.name === 'rmcp', 'initialize did not return Codex-compatible serverInfo');
  notify('notifications/initialized');

  const tools = await request('tools/list');
  const toolNames = tools.result.tools.map((tool) => tool.name);
  assert(JSON.stringify(toolNames) === JSON.stringify(['js', 'js_add_node_module_dir', 'js_reset']), `unexpected tool list: ${toolNames.join(', ')}`);

  const simple = await request('tools/call', {
    name: 'js',
    arguments: { code: 'const answer = 41; nodeRepl.write(String(answer + 1))' },
  });
  assert(textOf(simple) === '42', `unexpected simple eval result: ${textOf(simple)}`);

  const persisted = await request('tools/call', {
    name: 'js',
    arguments: { code: 'nodeRepl.write(String(answer + 2))' },
  });
  assert(textOf(persisted) === '43', `persistent binding failed: ${textOf(persisted)}`);

  const implicit = await request('tools/call', {
    name: 'js',
    arguments: { code: 'answer + 3' },
  });
  assert(textOf(implicit) === '', `implicit expression output should be empty: ${textOf(implicit)}`);

  const topLevelAwait = await request('tools/call', {
    name: 'js',
    arguments: { code: 'await import("node:os").then((os) => nodeRepl.write(os.platform()))' },
  });
  assert(textOf(topLevelAwait).length > 0, 'top-level await dynamic import returned empty output');

  const write = await request('tools/call', {
    name: 'js',
    arguments: { code: 'nodeRepl.write("cwd=" + nodeRepl.cwd)' },
  });
  assert(textOf(write).startsWith('cwd='), `nodeRepl.write failed: ${textOf(write)}`);

  const apiShape = await request('tools/call', {
    name: 'js',
    arguments: { code: 'nodeRepl.write(JSON.stringify({ keys: Object.keys(nodeRepl).sort(), envKeys: Object.keys(nodeRepl.env), fetch: typeof nodeRepl.fetch, nativePipe: typeof nodeRepl.nativePipe }))' },
  });
  const apiShapeValue = JSON.parse(textOf(apiShape));
  assert(JSON.stringify(apiShapeValue.keys) === JSON.stringify(['cwd', 'emitImage', 'env', 'homeDir', 'requestMeta', 'setResponseMeta', 'tmpDir', 'write']), `unexpected nodeRepl keys: ${textOf(apiShape)}`);
  assert(apiShapeValue.envKeys.length === 0, `nodeRepl.env should be empty by default: ${textOf(apiShape)}`);
  assert(apiShapeValue.fetch === 'undefined', `nodeRepl.fetch should be hidden in ordinary code: ${textOf(apiShape)}`);
  assert(apiShapeValue.nativePipe === 'undefined', `nodeRepl.nativePipe should be hidden in ordinary code: ${textOf(apiShape)}`);

  const fetchUnavailable = await request('tools/call', {
    name: 'js',
    arguments: { code: 'await nodeRepl.fetch("data:text/plain,fetch-ok")' },
  });
  assert(fetchUnavailable.result?.isError === true, 'nodeRepl.fetch should fail outside trusted code');
  assert(textOf(fetchUnavailable) === 'nodeRepl.fetch is not a function', `unexpected nodeRepl.fetch error: ${textOf(fetchUnavailable)}`);

  const blockedProcess = await request('tools/call', {
    name: 'js',
    arguments: { code: 'try { await import("node:process"); nodeRepl.write("allowed"); } catch { nodeRepl.write("blocked"); }' },
  });
  assert(textOf(blockedProcess) === 'blocked', `node:process import should be blocked: ${textOf(blockedProcess)}`);

  const image = await request('tools/call', {
    name: 'js',
    arguments: { code: 'await nodeRepl.emitImage(new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))' },
  });
  assert(imageOf(image)?.mimeType === 'image/png', 'nodeRepl.emitImage did not return inferred PNG content');

  const tempRoot = await mkdtemp(join(tmpdir(), 'autodev-node-repl-'));
  const tempNodeModules = join(tempRoot, 'node_modules');
  await mkdir(tempNodeModules);
  const addModuleDir = await request('tools/call', {
    name: 'js_add_node_module_dir',
    arguments: { path: tempNodeModules },
  });
  assert(textOf(addModuleDir) === 'true', `js_add_node_module_dir first add failed: ${textOf(addModuleDir)}`);
  const addModuleDirAgain = await request('tools/call', {
    name: 'js_add_node_module_dir',
    arguments: { path: tempNodeModules },
  });
  assert(textOf(addModuleDirAgain) === 'false', `js_add_node_module_dir second add should be false: ${textOf(addModuleDirAgain)}`);

  const tempModule = join(tempRoot, 'uses-node-repl.mjs');
  await writeFile(tempModule, 'export function run() { globalThis.nodeRepl.write("module-ok"); }\n', 'utf8');
  const localModule = await request('tools/call', {
    name: 'js',
    arguments: { code: `await import(${JSON.stringify(tempModule)}).then((mod) => mod.run())` },
  });
  assert(textOf(localModule) === 'module-ok', `local module global nodeRepl failed: ${textOf(localModule)}`);
  await rm(tempRoot, { recursive: true, force: true });

  const bundledModules = await request('tools/call', {
    name: 'js',
    arguments: { code: 'await import("pngjs").then((mod) => nodeRepl.write(typeof mod.PNG))' },
  });
  assert(textOf(bundledModules) === 'function', `bundled node_modules import failed: ${textOf(bundledModules)}`);

  await request('tools/call', {
    name: 'js_reset',
    arguments: {},
  });

  const reset = await request('tools/call', {
    name: 'js',
    arguments: { code: 'nodeRepl.write(typeof answer)' },
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
