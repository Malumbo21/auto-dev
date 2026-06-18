#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { existsSync, readFileSync } from 'node:fs';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { createServer } from 'node:net';
import { join, resolve } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)));
const codexRoot = '/Applications/Codex.app/Contents/Resources/cua_node';
const codexConfig = resolve(process.env.HOME ?? '', '.codex/config.toml');
const codexConfigEnv = readCodexNodeReplConfigEnv();
const codexCommand = readCodexNodeReplCommand() ?? join(codexRoot, 'bin/node_repl');
const autoDevCommand = resolve(repoRoot, 'mpp-ui/bin/autodev-node-repl');
const bundledPackageProbes = [
  '@napi-rs/canvas',
  '@oai/sky',
  'bmp-js',
  'idb-keyval',
  'jpeg-js',
  'node-fetch',
  'objc-js',
  'pdfjs-dist',
  'pixelmatch',
  'playwright',
  'pngjs',
  'semver',
  'sharp',
  'tesseract.js',
];

function codexEnv() {
  return {
    ...process.env,
    NODE_REPL_NATIVE_PIPE_CONNECT_TIMEOUT_MS: '1000',
    NODE_REPL_NODE_MODULE_DIRS: join(codexRoot, 'lib/node_modules'),
    NODE_REPL_NODE_PATH: join(codexRoot, 'bin/node'),
    ...codexConfigEnv,
  };
}

function autoDevEnv() {
  return {
    ...process.env,
    NODE_REPL_NATIVE_PIPE_CONNECT_TIMEOUT_MS: '1000',
    NODE_REPL_NODE_MODULE_DIRS: resolve(repoRoot, 'mpp-ui/vendor/node_modules'),
    NODE_REPL_TRUSTED_CODE_PATHS: codexConfigEnv.NODE_REPL_TRUSTED_CODE_PATHS ?? repoRoot,
    NODE_REPL_TRUSTED_BROWSER_CLIENT_SHA256S: codexConfigEnv.NODE_REPL_TRUSTED_BROWSER_CLIENT_SHA256S,
    NODE_REPL_INSTRUCTIONS_USE_CASE_BROWSER: codexConfigEnv.NODE_REPL_INSTRUCTIONS_USE_CASE_BROWSER,
    NODE_REPL_INSTRUCTIONS_USE_CASE_CHROME: codexConfigEnv.NODE_REPL_INSTRUCTIONS_USE_CASE_CHROME,
  };
}

function readCodexNodeReplCommand() {
  if (!existsSync(codexConfig)) {
    return null;
  }
  const text = readFileSync(codexConfig, 'utf8');
  const section = text.match(/\[mcp_servers\.node_repl\]([\s\S]*?)(?:\n\[|$)/);
  const command = section?.[1]?.match(/^\s*command\s*=\s*"([^"]+)"/m)?.[1];
  return command ?? null;
}

function readCodexNodeReplConfigEnv() {
  if (!existsSync(codexConfig)) {
    return {};
  }
  const text = readFileSync(codexConfig, 'utf8');
  const section = text.match(/\[mcp_servers\.node_repl\.env\]([\s\S]*?)(?:\n\[|$)/);
  if (!section) {
    return {};
  }
  const env = {};
  for (const line of section[1].split('\n')) {
    const match = line.match(/^\s*([A-Za-z0-9_]+)\s*=\s*"([^"]*)"/);
    if (match) {
      env[match[1]] = match[2];
    }
  }
  return env;
}

function startServer(label, command, env) {
  if (!existsSync(command)) {
    throw new Error(`${label} command does not exist: ${command}`);
  }

  const child = spawn(command, [], {
    cwd: repoRoot,
    env,
    stdio: ['pipe', 'pipe', 'pipe'],
  });

  const pending = new Map();
  const rl = createInterface({ input: child.stdout });
  let stderr = '';
  let nextId = 1;

  child.stderr.on('data', (chunk) => {
    stderr += chunk.toString('utf8');
  });

  rl.on('line', (line) => {
    let message;
    try {
      message = JSON.parse(line);
    } catch (error) {
      throw new Error(`${label} emitted invalid JSON: ${line}\n${error}`);
    }
    const waiter = pending.get(message.id);
    if (!waiter) {
      return;
    }
    pending.delete(message.id);
    waiter.resolve(message);
  });

  function request(method, params = {}) {
    const id = nextId++;
    child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id, method, params }) + '\n');
    return new Promise((resolvePromise, reject) => {
      const timeout = setTimeout(() => {
        pending.delete(id);
        reject(new Error(`${label} timed out waiting for ${method}; stderr=${stderr}`));
      }, 8000);
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

  function stop() {
    child.stdin.end();
    child.kill();
  }

  return { label, request, notify, stop };
}

function textOf(response) {
  return response.result?.content
    ?.filter((item) => item.type === 'text')
    .map((item) => item.text)
    .join('') ?? '';
}

function imageSummary(response) {
  const image = response.result?.content?.find((item) => item.type === 'image');
  if (!image) {
    return null;
  }
  return {
    mimeType: image.mimeType,
    dataLength: typeof image.data === 'string' ? image.data.length : null,
  };
}

function summarize(response) {
  return {
    isError: response.result?.isError === true,
    text: textOf(response),
    image: imageSummary(response),
    metaKeys: Object.keys(response.result?._meta ?? {}).sort(),
    rawResultKeys: Object.keys(response.result ?? {}).sort(),
  };
}

async function initialize(server) {
  const init = await server.request('initialize', {
    protocolVersion: '2024-11-05',
    clientInfo: { name: 'compare-node-repl-codex', version: '0.1.0' },
    capabilities: {},
  });
  server.notify('notifications/initialized');
  return init;
}

async function callTool(server, name, args = {}, meta) {
  return await server.request('tools/call', {
    name,
    arguments: args,
    ...(meta ? { _meta: meta } : {}),
  });
}

function compactTools(response) {
  return response.result.tools.map((tool) => ({
    name: tool.name,
    description: tool.description,
    inputSchema: tool.inputSchema,
  }));
}

async function runProbe(server, shared) {
  const result = {};
  result.initialize = await initialize(server);
  result.tools = compactTools(await server.request('tools/list'));
  result.implicitExpression = summarize(await callTool(server, 'js', { code: '1 + 1' }));
  result.consoleLog = summarize(await callTool(server, 'js', { code: 'console.log("log-ok")' }));
  result.write = summarize(await callTool(server, 'js', { code: 'nodeRepl.write("write-ok")' }));
  result.apiShape = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(JSON.stringify(Object.fromEntries(Object.keys(nodeRepl).sort().map((key) => [key, typeof nodeRepl[key]]))))',
  }));
  result.hiddenApiShape = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(JSON.stringify({ addNodeModuleDir: typeof nodeRepl.addNodeModuleDir, config: typeof nodeRepl.config, fetch: typeof nodeRepl.fetch, import: typeof nodeRepl.import, launchServices: typeof nodeRepl.launchServices, nativePipe: typeof nodeRepl.nativePipe, withSuspendedTimeout: typeof nodeRepl.withSuspendedTimeout }))',
  }));
  result.consoleTwoLogs = summarize(await callTool(server, 'js', {
    code: 'console.log("a"); console.log("b")',
  }));
  result.writeTrailingNewline = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write("x\\n")',
  }));
  result.writeNonString = summarize(await callTool(server, 'js', { code: 'nodeRepl.write(123)' }));
  result.topLevelAwait = summarize(await callTool(server, 'js', {
    code: 'await import("node:os").then((os) => nodeRepl.write(os.platform()))',
  }));
  result.blockProcessImport = summarize(await callTool(server, 'js', {
    code: 'try { await import("node:process"); nodeRepl.write("allowed"); } catch (error) { nodeRepl.write("blocked:" + error.message) }',
  }));
  result.envShape = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(JSON.stringify({ hasEnv: !!nodeRepl.env, pathType: typeof nodeRepl.env?.PATH, keys: Object.keys(nodeRepl.env ?? {}).slice(0, 5) }))',
  }));
  result.fetchDataUrl = summarize(await callTool(server, 'js', {
    code: 'const text = await nodeRepl.fetch("data:text/plain,fetch-ok").then((res) => res.text()); nodeRepl.write(text)',
  }));
  result.requestMeta = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(JSON.stringify(nodeRepl.requestMeta))',
  }, { session_id: 's1', turn_id: 't1' }));
  result.setResponseMeta = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.setResponseMeta({ probe: "ok" }); nodeRepl.write("meta-ok")',
  }));
  result.emitImageBytes = summarize(await callTool(server, 'js', {
    code: 'await nodeRepl.emitImage(new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))',
  }));
  result.emitImageDataUrl = summarize(await callTool(server, 'js', {
    code: 'await nodeRepl.emitImage("data:image/png;base64,iVBORw0KGgo=")',
  }));
  result.addModuleDir = summarize(await callTool(server, 'js_add_node_module_dir', {
    path: shared.tempNodeModules,
  }));
  result.addModuleDirAgain = summarize(await callTool(server, 'js_add_node_module_dir', {
    path: shared.tempNodeModules,
  }));
  result.addedModuleDirPackageResolve = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.resolvePackages([${JSON.stringify(shared.fixturePackageName)}]))`,
  }));
  result.addedModuleDirPackageImport = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.fixturePackageName)}).then((mod) => nodeRepl.write(mod.fixtureValue))`,
  }));
  result.localModuleGlobal = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.run())`,
  }));
  result.localModuleReloadFirst = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.reloadModule)}).then((mod) => nodeRepl.write(String(mod.count)))`,
  }));
  result.localModuleReloadSecond = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.reloadModule)}).then((mod) => nodeRepl.write(String(mod.count)))`,
  }));
  result.bundledPackageResolve = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.resolvePackages(${JSON.stringify(bundledPackageProbes)}))`,
  }));
  result.localStaticImports = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.staticEntryModule)}).then((mod) => mod.runStatic())`,
  }));
  result.trustedHiddenApiShape = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.hidden())`,
  }));
  result.trustedConfigShape = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.configShape())`,
  }));
  result.trustedConfigReadProbe = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.configReadProbe())`,
  }));
  result.trustedFetchDataUrl = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.trustedFetchDataUrl())`,
  }));
  result.nativePipeShape = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.nativePipeShape(${JSON.stringify(shared.nativePipePath)}))`,
  }));
  result.nativePipeRoundtrip = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.nativePipeRoundtrip(${JSON.stringify(shared.nativePipePath)}))`,
  }));
  result.withSuspendedTimeout = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.withSuspendedTimeoutProbe())`,
    timeout_ms: 50,
  }));
  result.reset = summarize(await callTool(server, 'js_reset'));
  result.afterReset = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(typeof answer)',
  }));
  return result;
}

function diffValues(left, right, path = '') {
  const diffs = [];
  if (JSON.stringify(left) === JSON.stringify(right)) {
    return diffs;
  }
  if (left == null || right == null || typeof left !== 'object' || typeof right !== 'object') {
    diffs.push({ path, codex: left, autodev: right });
    return diffs;
  }
  const keys = new Set([...Object.keys(left), ...Object.keys(right)]);
  for (const key of [...keys].sort()) {
    diffs.push(...diffValues(left[key], right[key], path ? `${path}.${key}` : key));
  }
  return diffs;
}

async function main() {
  if (!existsSync(codexCommand)) {
    throw new Error(`Codex node_repl not found: ${codexCommand}`);
  }
  if (!existsSync(autoDevCommand)) {
    throw new Error(`AutoDev node_repl not found: ${autoDevCommand}`);
  }

  const trustedScratchParent = firstTrustedCodePath() ?? tmpdir();
  const tempRoot = await mkdtemp(join(trustedScratchParent, 'node-repl-compare-'));
  const tempNodeModules = join(tempRoot, 'node_modules');
  const fixturePackageName = 'node-repl-fixture';
  const fixturePackageRoot = join(tempNodeModules, fixturePackageName);
  const tempModule = join(tempRoot, 'uses-node-repl.mjs');
  const reloadModule = join(tempRoot, 'reload-count.mjs');
  const staticEntryModule = join(tempRoot, 'static-entry.mjs');
  const staticChildModule = join(tempRoot, 'static-child.mjs');
  const nativePipePath = join(tempRoot, 'native-pipe.sock');
  await mkdir(tempNodeModules);
  await mkdir(fixturePackageRoot);
  await writeFile(join(fixturePackageRoot, 'package.json'), JSON.stringify({
    name: fixturePackageName,
    type: 'module',
    exports: './index.mjs',
  }, null, 2) + '\n', 'utf8');
  await writeFile(join(fixturePackageRoot, 'index.mjs'), 'export const fixtureValue = "fixture-ok";\n', 'utf8');
  await writeFile(reloadModule, 'globalThis.__nodeReplReloadCount = (globalThis.__nodeReplReloadCount ?? 0) + 1;\nexport const count = globalThis.__nodeReplReloadCount;\n', 'utf8');
  await writeFile(staticChildModule, 'export const childValue = "child-ok";\n', 'utf8');
  await writeFile(staticEntryModule, `import { childValue } from "./static-child.mjs";
import * as semver from "semver";
export function runStatic() {
  globalThis.nodeRepl.write(JSON.stringify({ childValue, semverValidType: typeof semver.valid }));
}
`, 'utf8');
  await writeFile(tempModule, `export function run() { globalThis.nodeRepl.write("module-ok"); }
export function hidden() { globalThis.nodeRepl.write(JSON.stringify({ config: typeof globalThis.nodeRepl.config, createElicitation: typeof globalThis.nodeRepl.createElicitation, fetch: typeof globalThis.nodeRepl.fetch, launchServices: typeof globalThis.nodeRepl.launchServices, nativePipe: typeof globalThis.nodeRepl.nativePipe, withSuspendedTimeout: typeof globalThis.nodeRepl.withSuspendedTimeout })); }
export function resolvePackages(packages) {
  const result = {};
  for (const name of packages) {
    try {
      import.meta.resolve(name);
      result[name] = "ok";
    } catch (error) {
      result[name] = { error: error?.name ?? "Error" };
    }
  }
  globalThis.nodeRepl.write(JSON.stringify(result));
}
function summarize(value) {
  if (value == null || typeof value !== "object") return { type: typeof value, value };
  const keys = Object.keys(value).sort();
  return { type: Array.isArray(value) ? "array" : "object", keys, valueTypes: Object.fromEntries(keys.slice(0, 8).map((key) => [key, typeof value[key]])) };
}
export function configShape() {
  const config = globalThis.nodeRepl.config;
  globalThis.nodeRepl.write(JSON.stringify(Object.fromEntries(Object.keys(config ?? {}).sort().map((key) => [key, typeof config[key]]))));
}
export async function configReadProbe() {
  const config = globalThis.nodeRepl.config;
  const result = {};
  for (const [name, call] of Object.entries({
    readRequirements: () => config.readRequirements(),
    read: () => config.read({ cwd: globalThis.nodeRepl.cwd, includeLayers: false }),
    readTomlBrowser: () => config.readToml("browser/config.toml"),
    readTomlMissing: () => config.readToml("node-repl-compare/missing.toml"),
  })) {
    try {
      result[name] = summarize(await call());
    } catch (error) {
      result[name] = { error: error?.message ?? String(error) };
    }
  }
  globalThis.nodeRepl.write(JSON.stringify(result));
}
export async function trustedFetchDataUrl() {
  const text = await globalThis.nodeRepl.fetch("data:text/plain,trusted-fetch-ok").then((response) => response.text());
  globalThis.nodeRepl.write(text);
}
export async function nativePipeRoundtrip(pipePath) {
  const socket = await globalThis.nodeRepl.nativePipe.createConnection(pipePath);
  const text = await new Promise((resolve, reject) => {
    let output = "";
    socket.on("data", (chunk) => {
      output += typeof chunk === "string" ? chunk : new TextDecoder().decode(chunk);
      if (output.includes("pong")) {
        socket.end();
        resolve(output);
      }
    });
    socket.on("error", reject);
    socket.write(new TextEncoder().encode("ping"));
  });
  globalThis.nodeRepl.write(text);
}
export async function nativePipeShape(pipePath) {
  const connection = await globalThis.nodeRepl.nativePipe.createConnection(pipePath);
  const prototype = Object.getPrototypeOf(connection);
  const shape = {
    type: Object.prototype.toString.call(connection),
    keys: Object.keys(connection).sort(),
    own: Object.getOwnPropertyNames(connection).sort(),
    proto: prototype ? Object.getOwnPropertyNames(prototype).sort() : [],
    types: Object.fromEntries(["read", "write", "send", "close", "end", "setEncoding", "readable", "writable", "reader", "writer"].map((key) => [key, typeof connection[key]])),
    readableProto: connection.readable ? Object.getOwnPropertyNames(Object.getPrototypeOf(connection.readable)).sort() : null,
    writableProto: connection.writable ? Object.getOwnPropertyNames(Object.getPrototypeOf(connection.writable)).sort() : null,
  };
  try {
    if (typeof connection.close === "function") await connection.close();
    else if (typeof connection.end === "function") connection.end();
  } catch {}
  globalThis.nodeRepl.write(JSON.stringify(shape));
}
export async function withSuspendedTimeoutProbe() {
  await globalThis.nodeRepl.withSuspendedTimeout(() => new Promise((resolve) => {
    setTimeout(() => {
      globalThis.nodeRepl.write("suspended-ok");
      resolve();
    }, 120);
  }));
}
`, 'utf8');

  const nativePipeServer = await startNativePipeFixture(nativePipePath);
  const codex = startServer('codex', codexCommand, codexEnv());
  const autoDev = startServer('autodev', autoDevCommand, autoDevEnv());

  try {
    const shared = { tempNodeModules, tempModule, reloadModule, staticEntryModule, fixturePackageName, nativePipePath };
    const codexResult = await runProbe(codex, shared);
    const autoDevResult = await runProbe(autoDev, shared);
    const diffs = diffValues(codexResult, autoDevResult);

    console.log(JSON.stringify({
      codexCommand,
      autoDevCommand,
      comparedAt: new Date().toISOString(),
      diffCount: diffs.length,
      diffs,
      codex: codexResult,
      autodev: autoDevResult,
    }, null, 2));
  } finally {
    codex.stop();
    autoDev.stop();
    await new Promise((resolveClose) => nativePipeServer.close(resolveClose));
    await rm(tempRoot, { recursive: true, force: true });
  }
}

async function startNativePipeFixture(pipePath) {
  await rm(pipePath, { force: true });
  const server = createServer((socket) => {
    socket.on('data', (chunk) => {
      if (chunk.toString('utf8').includes('ping')) {
        socket.write('pong');
      }
    });
  });
  await new Promise((resolveListen, reject) => {
    server.once('error', reject);
    server.listen(pipePath, () => {
      server.removeListener('error', reject);
      resolveListen();
    });
  });
  return server;
}

function firstTrustedCodePath() {
  const rawValue = codexConfigEnv.NODE_REPL_TRUSTED_CODE_PATHS;
  if (!rawValue) {
    return null;
  }
  for (const entry of rawValue.split(/[,:;]/).map((value) => value.trim()).filter(Boolean)) {
    if (existsSync(entry)) {
      return entry;
    }
  }
  return null;
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
