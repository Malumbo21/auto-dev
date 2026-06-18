#!/usr/bin/env node

import { spawn, spawnSync } from 'node:child_process';
import { createServer as createHttpServer } from 'node:http';
import { createInterface } from 'node:readline';
import { existsSync, readFileSync } from 'node:fs';
import { mkdir, mkdtemp, readdir, readFile, rm, writeFile } from 'node:fs/promises';
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
const requestMetaEnv = JSON.stringify({
  env_key: 'env-value',
  session_id: 'env-session',
  turn_id: 'env-turn',
  sandbox: { danger: true },
});
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
    NODE_REPL_TRUSTED_ENV_PROBE: 'trusted-env-ok',
    NODE_REPL_REQUEST_META: requestMetaEnv,
  };
}

function autoDevEnv() {
  return {
    ...process.env,
    NODE_REPL_NATIVE_PIPE_CONNECT_TIMEOUT_MS: '1000',
    NODE_REPL_NODE_MODULE_DIRS: resolve(repoRoot, 'mpp-ui/vendor/node_modules'),
    NODE_REPL_REQUEST_META: requestMetaEnv,
    NODE_REPL_TRUSTED_CODE_PATHS: codexConfigEnv.NODE_REPL_TRUSTED_CODE_PATHS ?? repoRoot,
    NODE_REPL_TRUSTED_BROWSER_CLIENT_SHA256S: codexConfigEnv.NODE_REPL_TRUSTED_BROWSER_CLIENT_SHA256S,
    NODE_REPL_TRUSTED_ENV_PROBE: 'trusted-env-ok',
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
        reject(new Error(`${label} timed out waiting for ${describeRequest(method, params)}; stderr=${stderr}`));
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

function describeRequest(method, params) {
  if (method !== 'tools/call') {
    return method;
  }
  const toolName = params?.name ?? 'unknown-tool';
  const code = params?.arguments?.code;
  if (typeof code !== 'string') {
    return `${method} ${toolName}`;
  }
  return `${method} ${toolName}: ${code.replace(/\s+/g, ' ').slice(0, 160)}`;
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

function compactRpcResponse(response) {
  if (response.error) {
    return {
      kind: 'error',
      code: response.error.code,
      message: response.error.message,
      dataType: response.error.data == null ? null : typeof response.error.data,
    };
  }
  return {
    kind: 'result',
    result: response.result ?? null,
    resultKeys: response.result && typeof response.result === 'object' ? Object.keys(response.result).sort() : [],
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

async function runActiveExecRegistryProbe(label, command, env) {
  const registryDir = await mkdtemp(join(tmpdir(), `node-repl-active-execs-${label}-`));
  const server = startServer(`${label}-active-exec`, command, {
    ...env,
    NODE_REPL_ACTIVE_EXEC_REGISTRY_DIR: registryDir,
    NODE_REPL_REQUEST_META: JSON.stringify({
      session_id: 'registry-session-env',
      turn_id: 'registry-turn-env',
    }),
  });

  try {
    await initialize(server);
    const pendingResult = callTool(server, 'js', {
      code: 'await new Promise((resolve) => setTimeout(resolve, 400)); nodeRepl.write("registry-done")',
      timeout_ms: 3000,
    }, {
      session_id: 'registry-session',
      turn_id: 'registry-turn',
    });
    await sleep(150);
    const during = await readActiveExecRegistrySnapshot(registryDir);
    const response = summarize(await pendingResult);
    const after = await readActiveExecRegistrySnapshot(registryDir);
    return { during, response, after };
  } finally {
    server.stop();
    await rm(registryDir, { recursive: true, force: true });
  }
}

async function readActiveExecRegistrySnapshot(registryDir) {
  if (!existsSync(registryDir)) {
    return { fileCount: 0, records: [] };
  }
  const files = (await readdir(registryDir)).sort();
  const records = [];
  for (const file of files) {
    try {
      records.push(normalizeActiveExecRecord(JSON.parse(await readFile(join(registryDir, file), 'utf8'))));
    } catch (error) {
      records.push({
        parseError: error?.message ?? String(error),
      });
    }
  }
  return {
    fileCount: files.length,
    records,
  };
}

function normalizeActiveExecRecord(record) {
  return {
    execIdType: typeof record.execId,
    kernelPidType: typeof record.kernelPid,
    keys: Object.keys(record).sort(),
    nodeReplPidType: typeof record.nodeReplPid,
    sessionId: record.sessionId ?? null,
    startedAtMsType: typeof record.startedAtMs,
    turnId: record.turnId ?? null,
    version: record.version ?? null,
  };
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
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

function runCli(command, args = [], env = process.env) {
  const result = spawnSync(command, args, {
    cwd: repoRoot,
    env,
    encoding: 'utf8',
  });
  return {
    status: result.status,
    stdout: result.stdout,
    stderr: result.stderr,
  };
}

async function runProbe(server, shared) {
  const result = {};
  result.initialize = await initialize(server);
  result.ping = compactRpcResponse(await server.request('ping'));
  result.resourcesList = compactRpcResponse(await server.request('resources/list'));
  result.resourceTemplatesList = compactRpcResponse(await server.request('resources/templates/list'));
  result.promptsList = compactRpcResponse(await server.request('prompts/list'));
  result.loggingSetLevel = compactRpcResponse(await server.request('logging/setLevel', { level: 'debug' }));
  result.tools = compactTools(await server.request('tools/list'));
  result.toolCallMissingParams = compactRpcResponse(await server.request('tools/call'));
  result.toolCallMissingName = compactRpcResponse(await server.request('tools/call', { arguments: {} }));
  result.toolCallNonStringName = compactRpcResponse(await server.request('tools/call', { name: 123, arguments: {} }));
  result.unknownTool = summarize(await callTool(server, 'unknown_tool', {}));
  result.jsMissingCode = summarize(await callTool(server, 'js', {}));
  result.jsWhitespaceCode = summarize(await callTool(server, 'js', { code: '  \n\t' }));
  result.jsZeroTimeout = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write("zero-timeout-ok")',
    timeout_ms: 0,
  }));
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
  result.consoleMethodShape = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(JSON.stringify(Object.fromEntries(["assert", "clear", "count", "countReset", "dirxml", "group", "groupEnd", "table", "time", "timeEnd", "trace"].map((key) => [key, typeof console[key]]))))',
  }));
  result.consoleFormatsValues = summarize(await callTool(server, 'js', {
    code: 'console.log(undefined, null, true, 123, { nested: { value: 1 } }, [1, "two"])',
  }));
  result.consoleTraceFormat = summarize(await callTool(server, 'js', {
    code: 'console.trace("trace-probe")',
  }));
  result.consoleExtraMethodsBehavior = summarize(await callTool(server, 'js', {
    code: 'console.assert(false, "assert-probe"); console.count("count-probe"); console.count("count-probe"); console.countReset("count-probe"); console.table([{ a: 1 }]); console.group("group-probe"); console.log("inside-group"); console.groupEnd(); console.clear(); console.dirxml({ x: 1 }); nodeRepl.write("after-console-extra")',
  }));
  result.writeTrailingNewline = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write("x\\n")',
  }));
  result.writeNonString = summarize(await callTool(server, 'js', { code: 'nodeRepl.write(123)' }));
  result.throwString = summarize(await callTool(server, 'js', { code: 'throw "string-throw"' }));
  result.throwNumber = summarize(await callTool(server, 'js', { code: 'throw 123' }));
  result.rejectPlainObject = summarize(await callTool(server, 'js', {
    code: 'await Promise.reject({ message: "plain-message", code: 7 })',
  }));
  result.savedWriteSeed = summarize(await callTool(server, 'js', {
    code: 'globalThis.savedNodeReplWrite = nodeRepl.write; nodeRepl.write("saved-write-seed")',
  }));
  result.savedWriteRef = summarize(await callTool(server, 'js', {
    code: 'globalThis.savedNodeReplWrite("saved-write-ref")',
  }));
  result.savedEmitImageSeed = summarize(await callTool(server, 'js', {
    code: 'globalThis.savedNodeReplEmitImage = nodeRepl.emitImage; nodeRepl.write("saved-image-seed")',
  }));
  result.savedEmitImageRef = summarize(await callTool(server, 'js', {
    code: 'await globalThis.savedNodeReplEmitImage(new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))',
  }));
  result.lateAsyncWriteScheduled = summarize(await callTool(server, 'js', {
    code: 'globalThis.lateAsyncWriteResult = "pending"; setTimeout(() => { try { nodeRepl.write("late-write"); globalThis.lateAsyncWriteResult = "success"; } catch (error) { globalThis.lateAsyncWriteResult = error?.message ?? String(error); } }, 30); nodeRepl.write("scheduled")',
  }));
  await sleep(80);
  result.lateAsyncWriteResult = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(String(globalThis.lateAsyncWriteResult))',
  }));
  result.topLevelAwait = summarize(await callTool(server, 'js', {
    code: 'await import("node:os").then((os) => nodeRepl.write(os.platform()))',
  }));
  result.topLevelAwaitDeclaresBindings = summarize(await callTool(server, 'js', {
    code: 'const awaitConst = 101; let awaitLet = 102; var awaitVar = 103; function awaitFunction() { return 104; } await Promise.resolve(); nodeRepl.write("await-bindings-ok")',
  }));
  result.afterTopLevelAwaitBindings = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(JSON.stringify({ awaitConst, awaitLet, awaitVar, awaitFunction: awaitFunction() }))',
  }));
  result.topLevelImportMeta = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(JSON.stringify({ dirname: import.meta.dirname ?? null, filename: import.meta.filename ?? null, main: import.meta.main ?? null, resolveSemver: import.meta.resolve("semver"), url: import.meta.url }))',
  }));
  result.topLevelStaticImport = summarize(await callTool(server, 'js', {
    code: 'import { platform } from "node:os"; nodeRepl.write(platform())',
  }));
  result.bindingSeed = summarize(await callTool(server, 'js', {
    code: 'const carryConst = 11; let carryLet = 12; function carryFunction() { return 13; } class CarryClass { static value = 14; } nodeRepl.write("binding-seed-ok")',
  }));
  result.importMetaReadsPriorBindings = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(JSON.stringify({ carryConst, carryLet, carryFunction: carryFunction(), carryClass: CarryClass.value, main: import.meta.main }))',
  }));
  result.importMetaDeclaresBindings = summarize(await callTool(server, 'js', {
    code: 'const importMetaConst = 21; let importMetaLet = 22; function importMetaFunction() { return 23; } nodeRepl.write(JSON.stringify({ urlType: typeof import.meta.url }))',
  }));
  result.afterImportMetaBindings = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(JSON.stringify({ importMetaConst, importMetaLet, importMetaFunction: importMetaFunction() }))',
  }));
  result.importMetaDeclaresMultipleBindings = summarize(await callTool(server, 'js', {
    code: 'const multiConstA = 31, multiConstB = 32; let multiLetA = 33, multiLetB = 34; var multiVarA = 35, multiVarB = 36; nodeRepl.write(JSON.stringify({ urlType: typeof import.meta.url }))',
  }));
  result.afterImportMetaMultipleBindings = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(JSON.stringify({ multiConstA, multiConstB, multiLetA, multiLetB, multiVarA, multiVarB }))',
  }));
  result.importMetaFunctionRedeclare = summarize(await callTool(server, 'js', {
    code: 'function importMetaFunction() { return 99; } nodeRepl.write(String(importMetaFunction()))',
  }));
  result.importMetaDeclaresDestructuredBindings = summarize(await callTool(server, 'js', {
    code: 'const { destructuredA, sourceB: destructuredB } = { destructuredA: 41, sourceB: 42 }; let [arrayBindingA, arrayBindingB] = [43, 44]; nodeRepl.write(JSON.stringify({ urlType: typeof import.meta.url }))',
  }));
  result.afterImportMetaDestructuredBindings = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(JSON.stringify({ destructuredA, destructuredB, arrayBindingA, arrayBindingB }))',
  }));
  result.importMetaIgnoresNestedBindings = summarize(await callTool(server, 'js', {
    code: `function nestedImportMetaProbe() {
  const nestedLocal = 61;
  return nestedLocal;
}
nodeRepl.write(JSON.stringify({ value: nestedImportMetaProbe(), urlType: typeof import.meta.url }))`,
  }));
  result.afterImportMetaNestedBindings = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(JSON.stringify({ fn: nestedImportMetaProbe(), nestedLocal: typeof nestedLocal }))',
  }));
  result.scriptThrowAfterBinding = summarize(await callTool(server, 'js', {
    code: 'const throwSeed = 81; throw new Error("throw-seed")',
  }));
  result.afterScriptThrowBinding = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(String(throwSeed))',
  }));
  result.importMetaThrowAfterBinding = summarize(await callTool(server, 'js', {
    code: 'const moduleThrowSeed = 82; nodeRepl.write(String(import.meta.main)); throw new Error("module-throw-seed")',
  }));
  result.afterImportMetaThrowBinding = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(typeof moduleThrowSeed)',
  }));
  result.blockProcessImport = summarize(await callTool(server, 'js', {
    code: 'try { await import("node:process"); nodeRepl.write("allowed"); } catch (error) { nodeRepl.write("blocked:" + error.message) }',
  }));
  result.envShape = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(JSON.stringify({ hasEnv: !!nodeRepl.env, pathType: typeof nodeRepl.env?.PATH, keys: Object.keys(nodeRepl.env ?? {}).slice(0, 5) }))',
  }));
  result.untrustedEnvProbe = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(JSON.stringify({ probe: nodeRepl.env.NODE_REPL_TRUSTED_ENV_PROBE ?? null }))',
  }));
  result.globalTmpDir = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(JSON.stringify({ matchesNodeReplTmpDir: typeof tmpDir === "string" && tmpDir === nodeRepl.tmpDir, tmpDirType: typeof tmpDir }))',
  }));
  result.fetchDataUrl = summarize(await callTool(server, 'js', {
    code: 'const text = await nodeRepl.fetch("data:text/plain,fetch-ok").then((res) => res.text()); nodeRepl.write(text)',
  }));
  result.requestMeta = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(JSON.stringify(nodeRepl.requestMeta))',
  }, { session_id: 's1', turn_id: 't1' }));
  result.requestMetaFromEnv = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(JSON.stringify(nodeRepl.requestMeta))',
  }));
  result.setResponseMeta = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.setResponseMeta({ probe: "ok" }); nodeRepl.write("meta-ok")',
  }));
  result.setResponseMetaMerge = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.setResponseMeta({ alpha: 1, shared: "first" }); nodeRepl.setResponseMeta({ beta: 2, shared: "second" }); nodeRepl.write("meta-merge-ok")',
  }));
  result.setResponseMetaArray = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.setResponseMeta([])',
  }));
  result.emitImageBytes = summarize(await callTool(server, 'js', {
    code: 'await nodeRepl.emitImage(new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))',
  }));
  result.emitImageDataUrl = summarize(await callTool(server, 'js', {
    code: 'await nodeRepl.emitImage("data:image/png;base64,iVBORw0KGgo=")',
  }));
  result.emitImageObjectDataUrl = summarize(await callTool(server, 'js', {
    code: 'await nodeRepl.emitImage({ image_url: "data:image/png;base64,iVBORw0KGgo=" })',
  }));
  result.emitImagePromiseBytes = summarize(await callTool(server, 'js', {
    code: 'await nodeRepl.emitImage(Promise.resolve(new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])))',
  }));
  result.emitImageErrorSemantics = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.emitImageErrorSemantics())`,
  }));
  result.addModuleDir = summarize(await callTool(server, 'js_add_node_module_dir', {
    path: shared.tempNodeModules,
  }));
  result.addModuleDirAgain = summarize(await callTool(server, 'js_add_node_module_dir', {
    path: shared.tempNodeModules,
  }));
  result.addModuleDirMissingPath = summarize(await callTool(server, 'js_add_node_module_dir', {}));
  result.addModuleDirRelativePath = summarize(await callTool(server, 'js_add_node_module_dir', {
    path: 'node_modules',
  }));
  result.addModuleDirNonNodeModules = summarize(await callTool(server, 'js_add_node_module_dir', {
    path: shared.tempRoot,
  }));
  result.addModuleDirMissingDirectory = summarize(await callTool(server, 'js_add_node_module_dir', {
    path: shared.missingNodeModules,
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
  result.localJsonImportAttributes = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.jsonModule)}, { with: { type: "json" } }).then((mod) => nodeRepl.write(JSON.stringify(mod.default)))`,
  }));
  result.dataJsonImportAttributes = summarize(await callTool(server, 'js', {
    code: 'await import("data:application/json,%7B%22dataJson%22%3A72%7D", { with: { type: "json" } }).then((mod) => nodeRepl.write(JSON.stringify(mod.default)))',
  }));
  result.importMetaShape = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.importMetaShape())`,
  }));
  result.trustedHiddenApiShape = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.hidden())`,
  }));
  result.trustedConfigShape = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.configShape())`,
  }));
  result.trustedEnvProbe = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.trustedEnvProbe())`,
  }));
  result.trustedConfigReadProbe = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.configReadProbe())`,
  }));
  result.createElicitationErrorSemantics = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.createElicitationErrorSemantics())`,
  }));
  result.trustedFetchDataUrl = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.trustedFetchDataUrl())`,
  }));
  result.trustedFetchHttpRoundtrip = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.trustedFetchHttpRoundtrip(${JSON.stringify(shared.fetchUrl)}))`,
  }));
  result.launchServicesErrorSemantics = summarize(await callTool(server, 'js', {
    code: `await import(${JSON.stringify(shared.tempModule)}).then((mod) => mod.launchServicesErrorSemantics())`,
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
  result.timeoutResetsKernelSeed = summarize(await callTool(server, 'js', {
    code: 'const timeoutSeed = 91; nodeRepl.write("timeout-seed")',
  }));
  result.timeoutResetsKernel = summarize(await callTool(server, 'js', {
    code: 'await new Promise((resolve) => setTimeout(resolve, 80)); nodeRepl.write("timeout-missed")',
    timeout_ms: 20,
  }));
  result.afterTimeoutReset = summarize(await callTool(server, 'js', {
    code: 'nodeRepl.write(typeof timeoutSeed)',
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
  const jsonModule = join(tempRoot, 'fixture.json');
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
  await writeFile(jsonModule, JSON.stringify({ jsonValue: 71 }) + '\n', 'utf8');
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
export function importMetaShape() {
  globalThis.nodeRepl.write(JSON.stringify({
    dirname: import.meta.dirname ?? null,
    filename: import.meta.filename ?? null,
    main: import.meta.main ?? null,
    resolvePackage: import.meta.resolve("semver"),
    resolveSelf: import.meta.resolve("./uses-node-repl.mjs"),
    url: import.meta.url,
  }));
}
function summarize(value) {
  if (value == null || typeof value !== "object") return { type: typeof value, value };
  const keys = Object.keys(value).sort();
  return { type: Array.isArray(value) ? "array" : "object", keys, valueTypes: Object.fromEntries(keys.slice(0, 8).map((key) => [key, typeof value[key]])) };
}
async function capture(label, call) {
  try {
    const value = await call();
    return { label, ok: true, value: value === undefined ? null : value };
  } catch (error) {
    return { label, ok: false, message: error?.message ?? String(error) };
  }
}
export function configShape() {
  const config = globalThis.nodeRepl.config;
  globalThis.nodeRepl.write(JSON.stringify(Object.fromEntries(Object.keys(config ?? {}).sort().map((key) => [key, typeof config[key]]))));
}
export function trustedEnvProbe() {
  const env = globalThis.nodeRepl.env ?? {};
  globalThis.nodeRepl.write(JSON.stringify({
    pathType: typeof env.PATH,
    probe: env.NODE_REPL_TRUSTED_ENV_PROBE ?? null,
  }));
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
export async function trustedFetchHttpRoundtrip(url) {
  const response = await globalThis.nodeRepl.fetch(url, {
    method: "POST",
    headers: {
      "content-type": "text/plain",
      "x-node-repl-probe": "yes",
    },
    body: "payload",
  });
  globalThis.nodeRepl.write(await response.text());
}
export async function createElicitationErrorSemantics() {
  const createElicitation = globalThis.nodeRepl.createElicitation;
  const results = [];
  results.push(await capture("no-args", () => createElicitation()));
  results.push(await capture("empty-object", () => createElicitation({})));
  results.push(await capture("string", () => createElicitation("prompt")));
  globalThis.nodeRepl.write(JSON.stringify(results));
}
export async function emitImageErrorSemantics() {
  const results = [];
  results.push(await capture("empty-bytes", () => globalThis.nodeRepl.emitImage(new Uint8Array([]))));
  results.push(await capture("text-data-url", () => globalThis.nodeRepl.emitImage("data:text/plain;base64,aGk=")));
  results.push(await capture("invalid-base64", () => globalThis.nodeRepl.emitImage("data:image/png;base64,%%%")));
  results.push(await capture("http-image-url", () => globalThis.nodeRepl.emitImage({ image_url: "https://example.com/a.png" })));
  results.push(await capture("empty-mime", () => globalThis.nodeRepl.emitImage({ bytes: new Uint8Array([1, 2, 3]), mimeType: "" })));
  globalThis.nodeRepl.write(JSON.stringify(results));
}
export async function launchServicesErrorSemantics() {
  const openApplication = globalThis.nodeRepl.launchServices.openApplication;
  const results = [];
  results.push(await capture("string-target", () => openApplication("com.apple.TextEdit")));
  results.push(await capture("empty-object", () => openApplication({})));
  results.push(await capture("two-keys", () => openApplication({ applicationPath: "/Applications/TextEdit.app", bundleIdentifier: "com.apple.TextEdit" })));
  globalThis.nodeRepl.write(JSON.stringify(results));
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
  const httpFixture = await startHttpFetchFixture();
  const codex = startServer('codex', codexCommand, codexEnv());
  const autoDev = startServer('autodev', autoDevCommand, autoDevEnv());

  try {
    const missingNodeModules = join(tempRoot, 'missing', 'node_modules');
    const shared = { tempRoot, tempNodeModules, missingNodeModules, tempModule, reloadModule, staticEntryModule, jsonModule, fixturePackageName, nativePipePath, fetchUrl: httpFixture.url };
    const codexResult = {
      cliHelp: runCli(codexCommand, ['--help'], codexEnv()),
      activeExecRegistry: await runActiveExecRegistryProbe('codex', codexCommand, codexEnv()),
      ...(await runProbe(codex, shared)),
    };
    const autoDevResult = {
      cliHelp: runCli(autoDevCommand, ['--help'], autoDevEnv()),
      activeExecRegistry: await runActiveExecRegistryProbe('autodev', autoDevCommand, autoDevEnv()),
      ...(await runProbe(autoDev, shared)),
    };
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
    await new Promise((resolveClose) => httpFixture.server.close(resolveClose));
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

async function startHttpFetchFixture() {
  const server = createHttpServer((request, response) => {
    let body = '';
    request.setEncoding('utf8');
    request.on('data', (chunk) => {
      body += chunk;
    });
    request.on('end', () => {
      response.setHeader('content-type', 'application/json');
      response.end(JSON.stringify({
        body,
        contentType: request.headers['content-type'] ?? null,
        method: request.method,
        probeHeader: request.headers['x-node-repl-probe'] ?? null,
        url: request.url,
      }));
    });
  });
  await new Promise((resolveListen, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      server.removeListener('error', reject);
      resolveListen();
    });
  });
  const address = server.address();
  if (!address || typeof address === 'string') {
    throw new Error('HTTP fetch fixture did not bind to a TCP port');
  }
  return {
    server,
    url: `http://127.0.0.1:${address.port}/node-repl-fetch`,
  };
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
