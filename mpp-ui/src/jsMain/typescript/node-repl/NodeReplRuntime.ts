import { builtinModules, createRequire } from 'node:module';
import * as childProcess from 'node:child_process';
import * as crypto from 'node:crypto';
import * as fs from 'node:fs';
import * as net from 'node:net';
import * as os from 'node:os';
import * as path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import * as util from 'node:util';
import * as vm from 'node:vm';

export interface NodeReplExecutionOptions {
  code: string;
  timeoutMs?: number;
  requestMeta?: Record<string, unknown>;
}

export interface NodeReplImageContent {
  type: 'image';
  data: string;
  mimeType: string;
}

export interface NodeReplTextContent {
  type: 'text';
  text: string;
}

export interface NodeReplExecutionResult {
  content: Array<NodeReplTextContent | NodeReplImageContent>;
  responseMeta?: Record<string, unknown>;
}

interface ActiveExecution {
  cellIndex: number;
  output: string[];
  images: NodeReplImageContent[];
  responseMeta: Record<string, unknown>;
  requestMeta: Record<string, unknown>;
  timeoutSuspendedAt: number | null;
  timeoutSuspendedMs: number;
  timeoutSuspensionDepth: number;
}

type ModuleBindingKind = 'class' | 'const' | 'function' | 'let' | 'var';

interface ModuleBindingDeclaration {
  captureIndex: number;
  kind: ModuleBindingKind;
  name: string;
}

const DEFAULT_TIMEOUT_MS = 30_000;
const DEFAULT_NATIVE_PIPE_CONNECT_TIMEOUT_MS = 1_000;
const NODE_MODULE_DIRS_ENV = 'NODE_REPL_NODE_MODULE_DIRS';
const NODE_REPL_ENV_ALLOWLIST_ENV = 'NODE_REPL_UNTRUSTED_ENV_ALLOWLIST';
const NODE_REPL_NATIVE_PIPE_CONNECT_TIMEOUT_ENV = 'NODE_REPL_NATIVE_PIPE_CONNECT_TIMEOUT_MS';
const NODE_REPL_TRUSTED_BROWSER_CLIENT_SHA256S_ENV = 'NODE_REPL_TRUSTED_BROWSER_CLIENT_SHA256S';
const NODE_REPL_TRUSTED_CODE_PATHS_ENV = 'NODE_REPL_TRUSTED_CODE_PATHS';
const PACKAGE_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..');
const BLOCKED_BUILTIN_MODULES = new Set(['process', 'node:process']);
const BUILTIN_MODULES = new Set([
  ...builtinModules,
  ...builtinModules.map((moduleName) => `node:${moduleName}`),
]);

export class NodeReplRuntime {
  private context: vm.Context;
  private consoleApi: Console;
  private nodeReplApi: Record<string, unknown>;
  private additionalModuleDirs: string[] = [];
  private activeExecution: ActiveExecution | null = null;
  private activeImportVersion = 0;
  private activeModuleCache: Map<string, any> | null = null;
  private fileHashCache = new Map<string, string | null>();
  private fullEnvApi: Record<string, string>;
  private readonly homeDir = os.homedir();
  private importVersion = 0;
  private nextCellIndex = 0;
  private requireForResolve = createRequire(path.join(process.cwd(), 'node_repl_runtime.js'));
  private readonly topLevelModuleBindingKey = '__nodeReplTopLevelModuleBindings';
  private readonly tmpDir = os.tmpdir();
  private untrustedEnvApi: Record<string, string>;

  constructor(private readonly cwd: string = process.cwd()) {
    this.fullEnvApi = this.createFullEnvApi();
    this.untrustedEnvApi = this.createUntrustedEnvApi();
    this.additionalModuleDirs = this.readInitialModuleDirs();
    this.context = this.createContext();
  }

  reset(): void {
    this.context = this.createContext();
  }

  addNodeModuleDir(dirPath: string): boolean {
    if (!dirPath || typeof dirPath !== 'string') {
      throw new Error('path must be a non-empty string');
    }
    if (!path.isAbsolute(dirPath)) {
      throw new Error('path must be an absolute node_modules directory');
    }
    if (path.basename(dirPath) !== 'node_modules') {
      throw new Error('path must point to a node_modules directory');
    }

    const resolvedPath = path.resolve(dirPath);
    if (this.moduleSearchRoots().includes(resolvedPath)) {
      return false;
    }

    this.additionalModuleDirs.push(resolvedPath);
    return true;
  }

  getModuleDirs(): string[] {
    return [...this.moduleSearchRoots()];
  }

  async execute(options: NodeReplExecutionOptions): Promise<NodeReplExecutionResult> {
    const code = options.code;
    if (!code || code.trim().length === 0) {
      throw new Error('js expects non-empty JavaScript source');
    }

    const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
    const activeExecution: ActiveExecution = {
      cellIndex: this.nextCellIndex++,
      output: [],
      images: [],
      responseMeta: {},
      requestMeta: options.requestMeta ?? {},
      timeoutSuspendedAt: null,
      timeoutSuspendedMs: 0,
      timeoutSuspensionDepth: 0,
    };
    this.activeExecution = activeExecution;
    this.activeImportVersion = ++this.importVersion;
    this.activeModuleCache = new Map();

    const previousNodeRepl = (globalThis as any).nodeRepl;
    const hadNodeRepl = Object.prototype.hasOwnProperty.call(globalThis, 'nodeRepl');
    const previousConsole = globalThis.console;
    (globalThis as any).nodeRepl = this.nodeReplApi;
    (globalThis as any).console = this.consoleApi;

    try {
      await this.withTimeout(this.evaluate(code), timeoutMs);

      const content: Array<NodeReplTextContent | NodeReplImageContent> = [];
      if (activeExecution.output.length > 0) {
        content.push({ type: 'text', text: activeExecution.output.join('') });
      }
      content.push(...activeExecution.images);

      if (content.length === 0) {
        content.push({ type: 'text', text: '' });
      }

      return {
        content,
        responseMeta: Object.keys(activeExecution.responseMeta).length > 0
          ? activeExecution.responseMeta
          : undefined,
      };
    } catch (error) {
      if (error instanceof NodeReplTimeoutError) {
        this.reset();
      }
      throw error;
    } finally {
      if (hadNodeRepl) {
        (globalThis as any).nodeRepl = previousNodeRepl;
      } else {
        delete (globalThis as any).nodeRepl;
      }
      (globalThis as any).console = previousConsole;
      this.activeExecution = null;
      this.activeImportVersion = 0;
      this.activeModuleCache = null;
    }
  }

  private createContext(): vm.Context {
    const sandbox: Record<string, unknown> = {
      Buffer,
      URL,
      URLSearchParams,
      TextDecoder,
      TextEncoder,
      atob: globalThis.atob,
      btoa: globalThis.btoa,
      clearImmediate,
      clearInterval,
      clearTimeout,
      crypto: globalThis.crypto,
      fetch: globalThis.fetch,
      FormData: globalThis.FormData,
      Headers: globalThis.Headers,
      performance: globalThis.performance,
      queueMicrotask,
      Request: globalThis.Request,
      Response: globalThis.Response,
      setImmediate,
      setInterval,
      setTimeout,
      structuredClone: globalThis.structuredClone,
      tmpDir: this.tmpDir,
    };

    this.consoleApi = this.createConsole();
    this.nodeReplApi = this.createNodeReplApi();
    sandbox.console = this.consoleApi;
    sandbox.nodeRepl = this.nodeReplApi;

    return vm.createContext(sandbox, {
      name: 'autodev-node-repl',
      origin: pathToFileURL(this.cwd).href,
    });
  }

  private createConsole(): Console {
    const write = (args: unknown[], newline: boolean) => {
      const text = args.map((item) => this.formatValue(item)).join(' ');
      if (newline) {
        this.writeConsoleLine(text);
        return;
      }
      this.writeOutput(text);
    };

    return {
      log: (...args: unknown[]) => write(args, true),
      info: (...args: unknown[]) => write(args, true),
      warn: (...args: unknown[]) => write(args, true),
      error: (...args: unknown[]) => write(args, true),
      debug: (...args: unknown[]) => write(args, true),
      dir: (value?: unknown) => write([value], true),
      time: () => undefined,
      timeEnd: () => undefined,
      trace: (...args: unknown[]) => write(args, true),
    } as unknown as Console;
  }

  private createNodeReplApi(): Record<string, unknown> {
    const api: Record<string, unknown> = {
      cwd: this.cwd,
      homeDir: this.homeDir,
      tmpDir: this.tmpDir,
      write: (text: unknown) => {
        if (typeof text !== 'string') {
          throw new Error('nodeRepl.write expected a string');
        }
        this.writeOutput(text);
      },
      emitImage: async (imageLike: unknown) => this.emitImage(imageLike),
      setResponseMeta: (meta: Record<string, unknown>) => {
        if (!this.isPlainObject(meta)) {
          throw new Error('nodeRepl.setResponseMeta expected a plain object');
        }
        Object.assign(this.getActiveExecution().responseMeta, meta);
      },
    };

    Object.defineProperty(api, 'env', {
      enumerable: true,
      get: () => this.isTrustedStack() ? this.fullEnvApi : this.untrustedEnvApi,
    });

    Object.defineProperty(api, 'requestMeta', {
      enumerable: true,
      get: () => this.activeExecution?.requestMeta ?? {},
    });

    this.defineTrustedApiGetter(api, 'config', () => this.createConfigApi());
    this.defineTrustedApiGetter(api, 'fetch', () => (input: RequestInfo | URL, init?: RequestInit) => this.trustedFetch(input, init));
    this.defineTrustedApiGetter(api, 'nativePipe', () => ({
      createConnection: (pipePath: string) => this.createNativePipeConnection(pipePath),
    }));
    this.defineTrustedApiGetter(api, 'withSuspendedTimeout', () => async (callback: unknown) => {
      if (typeof callback !== 'function') {
        throw new Error('nodeRepl.withSuspendedTimeout expects a function');
      }
      return await this.runWithSuspendedTimeout(callback as () => unknown | Promise<unknown>);
    });
    this.defineTrustedApiGetter(api, 'createElicitation', () => async () => {
      throw new Error('nodeRepl.createElicitation is unavailable because the MCP client does not support form elicitation');
    });
    this.defineTrustedApiGetter(api, 'launchServices', () => ({
      openApplication: (target: unknown) => this.openApplication(target),
    }));

    return api;
  }

  private defineTrustedApiGetter(api: Record<string, unknown>, key: string, createValue: () => unknown): void {
    Object.defineProperty(api, key, {
      enumerable: false,
      configurable: false,
      get: () => this.isTrustedStack() ? createValue() : undefined,
    });
  }

  private createFullEnvApi(): Record<string, string> {
    const env: Record<string, string> = {};
    for (const [key, value] of Object.entries(process.env)) {
      if (typeof value === 'string') {
        env[key] = value;
      }
    }
    return Object.freeze(env);
  }

  private createUntrustedEnvApi(): Record<string, string> {
    const allowlist = this.parseEnvAllowlist();
    if (allowlist.length === 0) {
      return Object.freeze({});
    }
    const env: Record<string, string> = {};
    for (const key of allowlist) {
      const value = process.env[key];
      if (typeof value === 'string') {
        env[key] = value;
      }
    }
    return Object.freeze(env);
  }

  private isTrustedStack(): boolean {
    const stack = new Error().stack ?? '';
    const stackFiles = this.extractStackFiles(stack);
    if (stackFiles.length === 0) {
      return false;
    }

    const trustedRoots = this.readTrustedCodePaths();
    const trustedHashes = this.readTrustedBrowserClientHashes();
    for (const filePath of stackFiles) {
      if (trustedRoots.some((root) => this.isPathInside(filePath, root))) {
        return true;
      }
      if (trustedHashes.size > 0 && trustedHashes.has(this.sha256File(filePath) ?? '')) {
        return true;
      }
    }
    return false;
  }

  private extractStackFiles(stack: string): string[] {
    const files = new Set<string>();
    for (const match of stack.matchAll(/file:\/\/[^)\s]+/g)) {
      try {
        files.add(fileURLToPath(match[0].replace(/:\d+:\d+$/, '')));
      } catch {
        // Ignore stack fragments that are not file URLs.
      }
    }
    for (const match of stack.matchAll(/(?:^|\s|\()((?:\/[^):\s]+)+\.[cm]?js)(?::\d+:\d+)?/g)) {
      files.add(path.resolve(match[1]));
    }
    return [...files];
  }

  private readTrustedCodePaths(): string[] {
    const rawValue = process.env[NODE_REPL_TRUSTED_CODE_PATHS_ENV];
    if (!rawValue) {
      return [];
    }
    return rawValue
      .split(new RegExp(`[${this.escapeRegExp(path.delimiter)},]`))
      .map((entry) => entry.trim())
      .filter(Boolean)
      .map((entry) => path.resolve(entry));
  }

  private readTrustedBrowserClientHashes(): Set<string> {
    const rawValue = process.env[NODE_REPL_TRUSTED_BROWSER_CLIENT_SHA256S_ENV];
    if (!rawValue) {
      return new Set();
    }
    return new Set(rawValue
      .split(/[,:;]/)
      .map((entry) => entry.trim().toLowerCase())
      .filter(Boolean));
  }

  private isPathInside(filePath: string, rootPath: string): boolean {
    const relative = path.relative(rootPath, filePath);
    return relative === '' || (!!relative && !relative.startsWith('..') && !path.isAbsolute(relative));
  }

  private sha256File(filePath: string): string | null {
    if (this.fileHashCache.has(filePath)) {
      return this.fileHashCache.get(filePath) ?? null;
    }
    try {
      const stats = fs.statSync(filePath);
      if (!stats.isFile()) {
        this.fileHashCache.set(filePath, null);
        return null;
      }
      const hash = crypto.createHash('sha256').update(fs.readFileSync(filePath)).digest('hex');
      this.fileHashCache.set(filePath, hash);
      return hash;
    } catch {
      this.fileHashCache.set(filePath, null);
      return null;
    }
  }

  private escapeRegExp(value: string): string {
    return value.replace(/[\\^$.*+?()[\]{}|]/g, '\\$&');
  }

  private parseEnvAllowlist(): string[] {
    const rawValue = process.env[NODE_REPL_ENV_ALLOWLIST_ENV];
    if (!rawValue) {
      return [];
    }
    return rawValue
      .split(',')
      .map((entry) => entry.trim())
      .filter(Boolean);
  }

  private createConfigApi(): Record<string, unknown> {
    return {
      read: async () => ({
        config: await this.readTomlConfig('config.toml'),
        origins: {},
      }),
      readRequirements: async () => ({
        requirements: await this.readTomlConfig('requirements.toml'),
      }),
      readToml: async (configPath: string) => this.readTomlConfig(configPath),
      writeToml: async (configPath: string, value: unknown) => this.writeTomlConfig(configPath, value),
      writeValue: async (configPath: string, keyPath: string | string[], value: unknown) => {
        const config = await this.readTomlConfig(configPath);
        this.setNestedValue(config, Array.isArray(keyPath) ? keyPath : String(keyPath).split('.'), value);
        await this.writeTomlConfig(configPath, config);
      },
      batchWrite: async (configPath: string, writes: Array<{ path?: string[]; keyPath?: string[]; value: unknown }>) => {
        const config = await this.readTomlConfig(configPath);
        for (const write of writes ?? []) {
          const keyPath = write.path ?? write.keyPath;
          if (Array.isArray(keyPath)) {
            this.setNestedValue(config, keyPath, write.value);
          }
        }
        await this.writeTomlConfig(configPath, config);
      },
    };
  }

  private async readTomlConfig(configPath: string): Promise<Record<string, unknown>> {
    const resolvedPath = this.resolveCodexConfigPath(configPath);
    if (!fs.existsSync(resolvedPath)) {
      return {};
    }
    const content = await fs.promises.readFile(resolvedPath, 'utf8');
    return this.parseToml(content);
  }

  private async writeTomlConfig(configPath: string, value: unknown): Promise<void> {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      throw new Error('nodeRepl.config.writeToml expects an object');
    }
    const resolvedPath = this.resolveCodexConfigPath(configPath);
    await fs.promises.mkdir(path.dirname(resolvedPath), { recursive: true });
    await fs.promises.writeFile(resolvedPath, this.serializeToml(value as Record<string, unknown>), 'utf8');
  }

  private resolveCodexConfigPath(configPath: string): string {
    if (!configPath || typeof configPath !== 'string') {
      throw new Error('nodeRepl.config path must be a non-empty string');
    }
    if (path.isAbsolute(configPath)) {
      throw new Error('nodeRepl.config path must be relative to CODEX_HOME');
    }
    const codexHome = path.resolve(process.env.CODEX_HOME || path.join(os.homedir(), '.codex'));
    const resolvedPath = path.resolve(codexHome, configPath);
    if (!this.isPathInside(resolvedPath, codexHome)) {
      throw new Error('nodeRepl.config path must stay inside CODEX_HOME');
    }
    return resolvedPath;
  }

  private parseToml(content: string): Record<string, unknown> {
    const root: Record<string, unknown> = {};
    let current: Record<string, unknown> = root;

    for (const rawLine of content.split(/\r?\n/)) {
      const line = this.stripTomlComment(rawLine).trim();
      if (!line) {
        continue;
      }

      const section = line.match(/^\[([^\]]+)\]$/);
      if (section) {
        current = this.ensureTomlSection(root, section[1].split('.').map((part) => part.trim()).filter(Boolean));
        continue;
      }

      const assignment = line.match(/^([A-Za-z0-9_.-]+)\s*=\s*(.*)$/);
      if (!assignment) {
        continue;
      }
      current[assignment[1]] = this.parseTomlValue(assignment[2].trim());
    }

    return root;
  }

  private stripTomlComment(line: string): string {
    let inString = false;
    let escaped = false;
    for (let index = 0; index < line.length; index += 1) {
      const char = line[index];
      if (char === '\\' && inString) {
        escaped = !escaped;
        continue;
      }
      if (char === '"' && !escaped) {
        inString = !inString;
      }
      if (char === '#' && !inString) {
        return line.slice(0, index);
      }
      escaped = false;
    }
    return line;
  }

  private ensureTomlSection(root: Record<string, unknown>, pathParts: string[]): Record<string, unknown> {
    let current = root;
    for (const part of pathParts) {
      const value = current[part];
      if (!value || typeof value !== 'object' || Array.isArray(value)) {
        current[part] = {};
      }
      current = current[part] as Record<string, unknown>;
    }
    return current;
  }

  private parseTomlValue(rawValue: string): unknown {
    if (rawValue.startsWith('"') && rawValue.endsWith('"')) {
      return JSON.parse(rawValue);
    }
    if (rawValue === 'true') {
      return true;
    }
    if (rawValue === 'false') {
      return false;
    }
    if (rawValue.startsWith('[') && rawValue.endsWith(']')) {
      try {
        return JSON.parse(rawValue);
      } catch {
        return rawValue;
      }
    }
    const numberValue = Number(rawValue);
    if (Number.isFinite(numberValue)) {
      return numberValue;
    }
    return rawValue;
  }

  private serializeToml(value: Record<string, unknown>): string {
    const lines: string[] = [];
    this.serializeTomlSection(value, [], lines);
    return `${lines.join('\n')}\n`;
  }

  private serializeTomlSection(value: Record<string, unknown>, sectionPath: string[], lines: string[]): void {
    const scalarEntries = Object.entries(value).filter(([, entryValue]) => !this.isTomlSection(entryValue));
    const sectionEntries = Object.entries(value).filter(([, entryValue]) => this.isTomlSection(entryValue));

    if (sectionPath.length > 0) {
      if (lines.length > 0) {
        lines.push('');
      }
      lines.push(`[${sectionPath.join('.')}]`);
    }
    for (const [key, entryValue] of scalarEntries) {
      lines.push(`${key} = ${this.serializeTomlValue(entryValue)}`);
    }
    for (const [key, entryValue] of sectionEntries) {
      this.serializeTomlSection(entryValue as Record<string, unknown>, [...sectionPath, key], lines);
    }
  }

  private serializeTomlValue(value: unknown): string {
    if (typeof value === 'string') {
      return JSON.stringify(value);
    }
    if (typeof value === 'number' || typeof value === 'boolean') {
      return String(value);
    }
    if (Array.isArray(value)) {
      return JSON.stringify(value);
    }
    if (value == null) {
      return '""';
    }
    return JSON.stringify(String(value));
  }

  private isTomlSection(value: unknown): boolean {
    return !!value && typeof value === 'object' && !Array.isArray(value);
  }

  private setNestedValue(root: Record<string, unknown>, keyPath: string[], value: unknown): void {
    const parts = keyPath.map((part) => String(part).trim()).filter(Boolean);
    if (parts.length === 0) {
      throw new Error('nodeRepl.config write path must not be empty');
    }
    let current = root;
    for (const part of parts.slice(0, -1)) {
      const nextValue = current[part];
      if (!nextValue || typeof nextValue !== 'object' || Array.isArray(nextValue)) {
        current[part] = {};
      }
      current = current[part] as Record<string, unknown>;
    }
    current[parts[parts.length - 1]] = value;
  }

  private async createNativePipeConnection(pipePath: string): Promise<net.Socket> {
    if (!pipePath || typeof pipePath !== 'string') {
      throw new Error('nodeRepl.nativePipe.createConnection expects a pipe path');
    }

    const timeoutMs = this.nativePipeConnectTimeoutMs();
    const socket = await new Promise<net.Socket>((resolve, reject) => {
      const socket = net.createConnection(pipePath);
      let settled = false;
      let timeout: NodeJS.Timeout | undefined;

      const cleanup = () => {
        socket.removeListener('connect', onConnect);
        socket.removeListener('error', onError);
        if (timeout) {
          clearTimeout(timeout);
        }
      };
      const fail = (error: Error) => {
        if (settled) {
          return;
        }
        settled = true;
        cleanup();
        socket.destroy();
        reject(error);
      };
      const onConnect = () => {
        if (settled) {
          return;
        }
        settled = true;
        cleanup();
        resolve(socket);
      };
      const onError = (error: Error) => fail(error);

      socket.once('connect', onConnect);
      socket.once('error', onError);
      if (timeoutMs > 0) {
        timeout = setTimeout(() => {
          fail(new Error(`native pipe connection timed out after ${timeoutMs}ms: ${pipePath}`));
        }, timeoutMs);
      }
    });
    return this.wrapNativePipeSocket(socket) as unknown as net.Socket;
  }

  private wrapNativePipeSocket(socket: net.Socket): Record<string, unknown> {
    const listenerMap = new Map<Function, Function>();
    const wrapper = {
      end: () => {
        socket.end();
      },
      off: (eventName: string, listener: (...args: unknown[]) => void) => {
        const mapped = listenerMap.get(listener) as ((...args: unknown[]) => void) | undefined;
        socket.off(eventName, mapped ?? listener);
        listenerMap.delete(listener);
        return wrapper;
      },
      on: (eventName: string, listener: (...args: unknown[]) => void) => {
        if (typeof listener !== 'function') {
          throw new Error('native pipe listener must be a function');
        }
        const mapped = (...args: unknown[]) => listener(...args);
        listenerMap.set(listener, mapped);
        socket.on(eventName, mapped);
        return wrapper;
      },
      write: (data: Uint8Array | ArrayBuffer) => {
        const payload = this.toNativePipeBytes(data);
        return socket.write(payload);
      },
    };
    return wrapper;
  }

  private toNativePipeBytes(data: unknown): Buffer {
    if (Object.prototype.toString.call(data) === '[object ArrayBuffer]') {
      return Buffer.from(data as ArrayBuffer);
    }
    if (ArrayBuffer.isView(data)) {
      return Buffer.from(data.buffer, data.byteOffset, data.byteLength);
    }
    throw new Error('native pipe write expected bytes');
  }

  private nativePipeConnectTimeoutMs(): number {
    const rawValue = process.env[NODE_REPL_NATIVE_PIPE_CONNECT_TIMEOUT_ENV];
    if (!rawValue) {
      return DEFAULT_NATIVE_PIPE_CONNECT_TIMEOUT_MS;
    }
    const value = Number(rawValue);
    return Number.isFinite(value) && value >= 0 ? value : DEFAULT_NATIVE_PIPE_CONNECT_TIMEOUT_MS;
  }

  private async openApplication(target: unknown): Promise<Record<string, never>> {
    if (!this.isPlainObject(target)) {
      throw new Error('nodeRepl.launchServices.openApplication expected a target object');
    }

    const applicationPath = typeof target.applicationPath === 'string' && target.applicationPath.length > 0
      ? target.applicationPath
      : null;
    const bundleIdentifier = typeof target.bundleIdentifier === 'string' && target.bundleIdentifier.length > 0
      ? target.bundleIdentifier
      : null;
    if ((applicationPath ? 1 : 0) + (bundleIdentifier ? 1 : 0) !== 1) {
      throw new Error('nodeRepl.launchServices.openApplication expected exactly one of applicationPath or bundleIdentifier');
    }
    if (process.platform !== 'darwin') {
      throw new Error('nodeRepl.launchServices.openApplication is only available on macOS');
    }

    const args = applicationPath ? [applicationPath] : ['-b', bundleIdentifier as string];

    await new Promise<void>((resolve, reject) => {
      const child = childProcess.spawn('open', args, { stdio: 'ignore' });
      child.on('error', reject);
      child.on('exit', (code) => {
        if (code === 0) {
          resolve();
          return;
        }
        reject(new Error(`open exited with code ${code ?? 1}`));
      });
    });
    return {};
  }

  private async trustedFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
    try {
      const url = this.readFetchUrl(input);
      if (!/^https?:\/\//i.test(url)) {
        throw new Error('unsupported fetch scheme');
      }
      return await fetch(input, init);
    } catch {
      throw new Error('nodeRepl.fetch request failed');
    }
  }

  private readFetchUrl(input: RequestInfo | URL): string {
    if (typeof input === 'string') {
      return input;
    }
    if (input instanceof URL) {
      return input.href;
    }
    const candidate = input as { url?: unknown };
    if (typeof candidate.url === 'string') {
      return candidate.url;
    }
    return String(input);
  }

  private async runWithSuspendedTimeout<T>(callback: () => T | Promise<T>): Promise<T> {
    const activeExecution = this.getActiveExecution();
    if (activeExecution.timeoutSuspensionDepth === 0) {
      activeExecution.timeoutSuspendedAt = Date.now();
    }
    activeExecution.timeoutSuspensionDepth += 1;
    try {
      return await callback();
    } finally {
      activeExecution.timeoutSuspensionDepth -= 1;
      if (activeExecution.timeoutSuspensionDepth === 0 && activeExecution.timeoutSuspendedAt != null) {
        activeExecution.timeoutSuspendedMs += Date.now() - activeExecution.timeoutSuspendedAt;
        activeExecution.timeoutSuspendedAt = null;
      }
    }
  }

  private async evaluate(code: string): Promise<unknown> {
    const staticImportSpecifier = this.readTopLevelStaticImportSpecifier(code);
    if (staticImportSpecifier) {
      throw new Error(`Top-level static import "${staticImportSpecifier}" is not supported in node_repl. Use await import("${staticImportSpecifier}") instead.`);
    }

    if (this.shouldRunAsTopLevelModule(code)) {
      return await this.runTopLevelModule(code);
    }

    try {
      return await this.runScript(code);
    } catch (error) {
      if (this.isImportMetaSyntaxError(error)) {
        return await this.runTopLevelModule(code);
      }
      if (this.isTopLevelAwaitSyntaxError(error)) {
        return this.runTopLevelAwait(code);
      }
      throw error;
    }
  }

  private async runScript(code: string): Promise<unknown> {
    const script = new vm.Script(code, {
      filename: 'node_repl_input.js',
      importModuleDynamically: ((specifier: string) => this.resolveAndImport(specifier)) as any,
    });
    return script.runInContext(this.context);
  }

  private async runTopLevelModule(code: string): Promise<unknown> {
    const sourceTextModule = (vm as any).SourceTextModule;
    if (typeof sourceTextModule !== 'function') {
      throw new Error('vm.SourceTextModule is unavailable; start node_repl with --experimental-vm-modules');
    }

    const bindingDeclarations = this.readTopLevelBindingDeclarations(code);
    const cellPath = this.currentCellPath();
    const module = new sourceTextModule(this.appendTopLevelModuleBindingCapture(code, bindingDeclarations), {
      context: this.context,
      identifier: pathToFileURL(cellPath).href,
      initializeImportMeta: (meta: Record<string, unknown>) => {
        meta.url = pathToFileURL(cellPath).href;
        meta.filename = cellPath;
        meta.dirname = this.cwd;
        meta.main = true;
        meta.resolve = (innerSpecifier: string) => this.resolveImportMeta(innerSpecifier, cellPath);
      },
      importModuleDynamically: (innerSpecifier: string) => this.resolveAndImportFrom(innerSpecifier, cellPath) as any,
    });
    await module.link((specifier: string, referencingModule: { identifier: string }) => this.getLinkedVmModule(
      specifier,
      this.filePathFromModuleIdentifier(referencingModule.identifier),
    ));
    try {
      await module.evaluate();
      await this.commitTopLevelModuleBindings(bindingDeclarations);
      return module.namespace;
    } catch (error) {
      await this.commitTopLevelModuleBindings(bindingDeclarations);
      throw error;
    }
  }

  private currentCellPath(): string {
    return path.join(this.cwd, `.node_repl_cell_${this.getActiveExecution().cellIndex}.mjs`);
  }

  private appendTopLevelModuleBindingCapture(code: string, declarations: ModuleBindingDeclaration[]): string {
    if (declarations.length === 0) {
      return code;
    }
    const captureGroups = new Map<number, ModuleBindingDeclaration[]>();
    for (const declaration of declarations) {
      const group = captureGroups.get(declaration.captureIndex) ?? [];
      group.push(declaration);
      captureGroups.set(declaration.captureIndex, group);
    }

    let source = '';
    let lastIndex = 0;
    for (const [captureIndex, group] of [...captureGroups.entries()].sort(([left], [right]) => left - right)) {
      source += code.slice(lastIndex, captureIndex);
      source += this.moduleBindingCaptureStatement(group);
      lastIndex = captureIndex;
    }
    source += code.slice(lastIndex);
    source += this.moduleBindingCaptureStatement(declarations);
    return source;
  }

  private moduleBindingCaptureStatement(declarations: ModuleBindingDeclaration[]): string {
    const entries = declarations
      .map((declaration) => `${JSON.stringify(declaration.name)}: ${declaration.name}`)
      .join(', ');
    return `;\nglobalThis[${JSON.stringify(this.topLevelModuleBindingKey)}] = Object.assign(globalThis[${JSON.stringify(this.topLevelModuleBindingKey)}] ?? {}, { ${entries} });\n`;
  }

  private async commitTopLevelModuleBindings(declarations: ModuleBindingDeclaration[]): Promise<void> {
    if (declarations.length === 0) {
      return;
    }
    const values = (this.context as any)[this.topLevelModuleBindingKey];
    if (!values || typeof values !== 'object') {
      return;
    }

    const statements = declarations
      .filter((declaration) => Object.prototype.hasOwnProperty.call(values, declaration.name))
      .map((declaration) => this.moduleBindingCommitStatement(declaration))
      .join('\n');
    if (!statements) {
      return;
    }

    await this.runScript(statements);
  }

  private moduleBindingCommitStatement(declaration: ModuleBindingDeclaration): string {
    const access = `globalThis[${JSON.stringify(this.topLevelModuleBindingKey)}][${JSON.stringify(declaration.name)}]`;
    if (declaration.kind === 'let') {
      return `let ${declaration.name} = ${access};`;
    }
    if (declaration.kind === 'var') {
      return `var ${declaration.name} = ${access};`;
    }
    return `const ${declaration.name} = ${access};`;
  }

  private async runTopLevelAwait(code: string): Promise<unknown> {
    try {
      return await this.runScript(`(async () => (${code}\n))()`);
    } catch (expressionError) {
      if (!this.isSyntaxError(expressionError)) {
        throw expressionError;
      }
      return this.runScript(`(async () => {\n${code}\n})()`);
    }
  }

  private async resolveAndImport(specifier: string): Promise<unknown> {
    return this.resolveAndImportFrom(specifier, null);
  }

  private async resolveAndImportFrom(specifier: string, referrerPath: string | null): Promise<unknown> {
    const resolved = this.resolveModuleSpecifier(specifier, referrerPath);
    if (resolved.kind === 'local') {
      return this.importLocalModule(resolved.url);
    }
    return import(resolved.importSpecifier);
  }

  private resolveModuleSpecifier(specifier: string, referrerPath: string | null): { kind: 'local'; url: URL } | { kind: 'native'; importSpecifier: string } {
    if (!specifier || typeof specifier !== 'string') {
      throw new Error('import specifier must be a non-empty string');
    }

    if (BLOCKED_BUILTIN_MODULES.has(specifier)) {
      throw new Error(`Importing module "${specifier}" is not allowed in node_repl`);
    }

    if (BUILTIN_MODULES.has(specifier)) {
      return { kind: 'native', importSpecifier: specifier };
    }

    if (specifier.startsWith('file://')) {
      return { kind: 'local', url: this.versionedLocalModuleUrl(this.assertSupportedLocalModuleUrl(new URL(specifier), specifier)) };
    }

    if (specifier.startsWith('data:')) {
      throw new Error(this.unsupportedImportSpecifierMessage(specifier));
    }

    if (specifier.startsWith('node:')) {
      return { kind: 'native', importSpecifier: specifier };
    }

    if (specifier.startsWith('.') || specifier.startsWith('/') || specifier.startsWith('..')) {
      const baseDir = referrerPath ? path.dirname(referrerPath) : this.cwd;
      const resolvedPath = path.resolve(baseDir, specifier);
      return { kind: 'local', url: this.versionedLocalModuleUrl(this.assertSupportedLocalModuleUrl(pathToFileURL(resolvedPath), specifier)) };
    }

    const resolvedPath = this.requireForResolve.resolve(specifier, {
      paths: this.moduleSearchRoots(),
    });
    return { kind: 'native', importSpecifier: pathToFileURL(resolvedPath).href };
  }

  private assertSupportedLocalModuleUrl(moduleUrl: URL, specifier: string): URL {
    const filePath = this.filePathFromUrl(moduleUrl);
    const extension = path.extname(filePath).toLowerCase();
    if (extension !== '.js' && extension !== '.mjs') {
      throw new Error(`Unsupported import specifier ${JSON.stringify(specifier)} in node_repl. Only .js and .mjs files are supported.`);
    }
    return moduleUrl;
  }

  private unsupportedImportSpecifierMessage(specifier: string): string {
    return `Unsupported import specifier ${JSON.stringify(specifier)} in node_repl. Use a package name like "lodash" or "@scope/pkg", or a relative/absolute/file:// .js/.mjs path.`;
  }

  private versionedLocalModuleUrl(moduleUrl: URL): URL {
    if (this.activeImportVersion > 0) {
      moduleUrl.searchParams.set('node_repl_exec', String(this.activeImportVersion));
    }
    return moduleUrl;
  }

  private async importLocalModule(moduleUrl: URL): Promise<unknown> {
    const module = await this.getLocalSourceModule(moduleUrl);
    if (module.status === 'unlinked') {
      await module.link((specifier: string, referencingModule: { identifier: string }) => this.getLinkedVmModule(
        specifier,
        this.filePathFromModuleIdentifier(referencingModule.identifier),
      ));
    }
    if (module.status !== 'evaluated') {
      await module.evaluate();
    }
    return module.namespace;
  }

  private async getLinkedVmModule(specifier: string, referrerPath: string | null): Promise<any> {
    const resolved = this.resolveModuleSpecifier(specifier, referrerPath);
    if (resolved.kind === 'local') {
      return this.getLocalSourceModule(resolved.url);
    }
    return this.getNativeSyntheticModule(resolved.importSpecifier);
  }

  private async getLocalSourceModule(moduleUrl: URL): Promise<any> {
    const sourceTextModule = (vm as any).SourceTextModule;
    if (typeof sourceTextModule !== 'function') {
      throw new Error('vm.SourceTextModule is unavailable; start node_repl with --experimental-vm-modules');
    }

    const cacheKey = `local:${moduleUrl.href}`;
    const cache = this.getActiveModuleCache();
    const cached = cache.get(cacheKey);
    if (cached) {
      return cached;
    }

    const filePath = this.filePathFromUrl(moduleUrl);
    const source = await fs.promises.readFile(filePath, 'utf8');
    const module = new sourceTextModule(source, {
      context: this.context,
      identifier: moduleUrl.href,
      initializeImportMeta: (meta: Record<string, unknown>) => {
        meta.url = pathToFileURL(filePath).href;
        meta.filename = filePath;
        meta.dirname = path.dirname(filePath);
        meta.main = false;
        meta.resolve = (innerSpecifier: string) => this.resolveImportMeta(innerSpecifier, filePath);
      },
      importModuleDynamically: (innerSpecifier: string) => this.resolveAndImportFrom(innerSpecifier, filePath) as any,
    });
    cache.set(cacheKey, module);
    return module;
  }

  private async getNativeSyntheticModule(importSpecifier: string): Promise<any> {
    const syntheticModule = (vm as any).SyntheticModule;
    if (typeof syntheticModule !== 'function') {
      throw new Error('vm.SyntheticModule is unavailable; start node_repl with --experimental-vm-modules');
    }

    const cacheKey = `native:${importSpecifier}`;
    const cache = this.getActiveModuleCache();
    const cached = cache.get(cacheKey);
    if (cached) {
      return cached;
    }

    const namespace = await import(importSpecifier);
    const exportNames = Object.keys(namespace);
    const module = new syntheticModule(exportNames, function initializeSyntheticModule(this: any) {
      for (const exportName of exportNames) {
        this.setExport(exportName, namespace[exportName]);
      }
    }, {
      context: this.context,
      identifier: `node-repl:${importSpecifier}`,
    });
    cache.set(cacheKey, module);
    return module;
  }

  private resolveImportMeta(specifier: string, referrerPath: string): string {
    if (this.isBarePackageImportSpecifier(specifier) && !BUILTIN_MODULES.has(specifier)) {
      this.resolveModuleSpecifier(specifier, referrerPath);
      return specifier;
    }
    const resolved = this.resolveModuleSpecifier(specifier, referrerPath);
    if (resolved.kind === 'local') {
      return pathToFileURL(this.filePathFromUrl(resolved.url)).href;
    }
    return resolved.importSpecifier;
  }

  private isBarePackageImportSpecifier(specifier: string): boolean {
    if (!specifier || typeof specifier !== 'string' || specifier.trim() !== specifier) {
      return false;
    }
    if (specifier.startsWith('.') || specifier.startsWith('/') || specifier.startsWith('\\')) {
      return false;
    }
    if (specifier.startsWith('file:') || specifier.startsWith('data:') || specifier.startsWith('node:')) {
      return false;
    }
    if (/^[A-Za-z][A-Za-z\d+.-]*:/.test(specifier)) {
      return false;
    }
    return !specifier.includes('\\');
  }

  private getActiveModuleCache(): Map<string, any> {
    if (!this.activeModuleCache) {
      this.activeModuleCache = new Map();
    }
    return this.activeModuleCache;
  }

  private filePathFromModuleIdentifier(identifier: string): string | null {
    if (!identifier.startsWith('file://')) {
      return null;
    }
    try {
      return this.filePathFromUrl(new URL(identifier));
    } catch {
      return null;
    }
  }

  private filePathFromUrl(moduleUrl: URL): string {
    const fileUrl = new URL(moduleUrl.href);
    fileUrl.search = '';
    fileUrl.hash = '';
    return fileURLToPath(fileUrl);
  }

  private moduleSearchRoots(): string[] {
    const roots = [
      ...this.packagedModuleDirs(),
      ...this.additionalModuleDirs,
      this.cwd,
      path.join(this.cwd, 'node_modules'),
    ];
    return [...new Set(roots)];
  }

  private async emitImage(imageLike: unknown): Promise<void> {
    const image = this.normalizeImage(await imageLike);
    this.getActiveExecution().images.push(image);
  }

  private normalizeImage(imageLike: unknown): NodeReplImageContent {
    if (typeof imageLike === 'string') {
      return this.parseImageDataUrl(imageLike);
    }

    const directBytes = this.toImageBuffer(imageLike);
    if (directBytes) {
      return {
        type: 'image',
        mimeType: this.inferImageMimeType(directBytes),
        data: directBytes.toString('base64'),
      };
    }

    if (this.isPlainObject(imageLike)) {
      const candidate = imageLike as {
        base64?: unknown;
        bytes?: unknown;
        data?: unknown;
        mimeType?: unknown;
        mime_type?: unknown;
      };

      if (typeof candidate.base64 === 'string') {
        const bytes = Buffer.from(candidate.base64, 'base64');
        const mimeType = this.readImageMimeType(candidate) ?? this.inferImageMimeType(bytes);
        return {
          type: 'image',
          mimeType,
          data: candidate.base64,
        };
      }

      const bytes = this.toImageBuffer(candidate.bytes) ?? this.toImageBuffer(candidate.data);
      if (bytes) {
        const mimeType = this.readImageMimeType(candidate) ?? this.inferImageMimeType(bytes);
        return {
          type: 'image',
          mimeType,
          data: bytes.toString('base64'),
        };
      }
    }

    throw new Error('nodeRepl.emitImage received an unsupported value');
  }

  private parseImageDataUrl(value: string): NodeReplImageContent {
    if (!value.startsWith('data:')) {
      throw new Error('nodeRepl.emitImage only accepts data URLs');
    }
    const match = value.match(/^data:([^,]*),(.*)$/s);
    if (!match) {
      throw new Error('nodeRepl.emitImage received a malformed data URL');
    }
    const mediaType = match[1];
    const data = match[2];
    const mimeType = mediaType.split(';')[0];
    if (!mimeType.startsWith('image/')) {
      throw new Error('nodeRepl.emitImage only accepts image/* data URLs');
    }
    if (!mediaType.split(';').slice(1).some((part) => part.toLowerCase() === 'base64')) {
      throw new Error('nodeRepl.emitImage received a malformed data URL');
    }
    if (!this.isValidBase64(data)) {
      throw new Error('nodeRepl.emitImage received an invalid base64 data URL');
    }
    return { type: 'image', mimeType, data };
  }

  private readImageMimeType(candidate: { mimeType?: unknown; mime_type?: unknown }): string | null {
    const mimeType = typeof candidate.mimeType === 'string' ? candidate.mimeType : candidate.mime_type;
    if (typeof mimeType !== 'string') {
      return null;
    }
    if (mimeType.length === 0) {
      throw new Error('nodeRepl.emitImage expected a non-empty mimeType');
    }
    if (!mimeType.startsWith('image/')) {
      throw new Error('nodeRepl.emitImage expected a non-empty mimeType');
    }
    return mimeType;
  }

  private toImageBuffer(value: unknown): Buffer | null {
    if (Object.prototype.toString.call(value) === '[object ArrayBuffer]') {
      return Buffer.from(value as ArrayBuffer);
    }
    if (ArrayBuffer.isView(value)) {
      return Buffer.from(value.buffer, value.byteOffset, value.byteLength);
    }
    if (typeof value === 'string') {
      const dataUrl = this.parseImageDataUrl(value);
      return Buffer.from(dataUrl.data, 'base64');
    }
    return null;
  }

  private inferImageMimeType(bytes: Buffer): string {
    if (bytes.length >= 8
      && bytes[0] === 0x89
      && bytes[1] === 0x50
      && bytes[2] === 0x4e
      && bytes[3] === 0x47
      && bytes[4] === 0x0d
      && bytes[5] === 0x0a
      && bytes[6] === 0x1a
      && bytes[7] === 0x0a) {
      return 'image/png';
    }
    if (bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) {
      return 'image/jpeg';
    }
    if (bytes.length >= 12
      && bytes.subarray(0, 4).toString('ascii') === 'RIFF'
      && bytes.subarray(8, 12).toString('ascii') === 'WEBP') {
      return 'image/webp';
    }
    throw new Error('nodeRepl.emitImage could not infer image MIME type from bytes; expected PNG, JPEG, or WebP data');
  }

  private isValidBase64(value: string): boolean {
    if (!/^[A-Za-z0-9+/]*={0,2}$/.test(value) || value.length % 4 === 1) {
      return false;
    }
    try {
      Buffer.from(value, 'base64');
      return true;
    } catch {
      return false;
    }
  }

  private isPlainObject(value: unknown): value is Record<string, unknown> {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return false;
    }
    return Object.prototype.toString.call(value) === '[object Object]';
  }

  private writeOutput(text: string): void {
    this.getActiveExecution().output.push(text);
  }

  private writeConsoleLine(text: string): void {
    const activeExecution = this.getActiveExecution();
    const previous = activeExecution.output.at(-1);
    if (previous !== undefined && !previous.endsWith('\n')) {
      activeExecution.output.push('\n');
    }
    activeExecution.output.push(text);
  }

  private getActiveExecution(): ActiveExecution {
    if (!this.activeExecution) {
      throw new Error('node_repl exec context not found');
    }
    return this.activeExecution;
  }

  private formatValue(value: unknown): string {
    if (typeof value === 'string') {
      return value;
    }
    return util.inspect(value, {
      colors: false,
      depth: 6,
      maxArrayLength: 100,
      breakLength: 100,
    });
  }

  private async withTimeout<T>(promise: Promise<T>, timeoutMs: number): Promise<T> {
    if (!Number.isFinite(timeoutMs) || timeoutMs <= 0) {
      return promise;
    }

    const startedAt = Date.now();
    let settled = false;
    let timeout: NodeJS.Timeout | undefined;

    return await new Promise<T>((resolve, reject) => {
      const finish = (callback: () => void) => {
        if (settled) {
          return;
        }
        settled = true;
        if (timeout) {
          clearTimeout(timeout);
        }
        callback();
      };

      const scheduleCheck = () => {
        if (settled) {
          return;
        }
        const suspendedMs = this.currentTimeoutSuspendedMs();
        const elapsedMs = Date.now() - startedAt - suspendedMs;
        if (elapsedMs >= timeoutMs) {
          finish(() => reject(new NodeReplTimeoutError(timeoutMs)));
          return;
        }
        timeout = setTimeout(scheduleCheck, Math.max(1, Math.min(25, timeoutMs - elapsedMs)));
      };

      promise.then(
        (value) => finish(() => resolve(value)),
        (error) => finish(() => reject(error)),
      );
      scheduleCheck();
    });
  }

  private currentTimeoutSuspendedMs(): number {
    const activeExecution = this.activeExecution;
    if (!activeExecution) {
      return 0;
    }
    if (activeExecution.timeoutSuspensionDepth > 0 && activeExecution.timeoutSuspendedAt != null) {
      return activeExecution.timeoutSuspendedMs + Date.now() - activeExecution.timeoutSuspendedAt;
    }
    return activeExecution.timeoutSuspendedMs;
  }

  private readInitialModuleDirs(): string[] {
    const rawValue = process.env[NODE_MODULE_DIRS_ENV];
    if (!rawValue) {
      return [];
    }

    return rawValue
      .split(path.delimiter)
      .map((entry) => entry.trim())
      .filter(Boolean)
      .map((entry) => path.resolve(this.cwd, entry));
  }

  private packagedModuleDirs(): string[] {
    const dirs = [
      path.join(PACKAGE_ROOT, 'vendor', 'node_modules'),
      path.join(PACKAGE_ROOT, 'vendor', 'node', `${process.platform}-${process.arch}`, 'lib', 'node_modules'),
    ];
    return dirs.filter((dirPath) => fs.existsSync(dirPath));
  }

  private isTopLevelAwaitSyntaxError(error: unknown): boolean {
    return this.isSyntaxError(error)
      && /await is only valid in async functions|await is only valid in async function/.test((error as Error).message);
  }

  private shouldRunAsTopLevelModule(code: string): boolean {
    return /\bimport\s*\.\s*meta\b/.test(code);
  }

  private readTopLevelStaticImportSpecifier(code: string): string | null {
    const match = code.match(/^\s*import\s+(?:(?:["']([^"']+)["'])|(?:[\w*{}\s,]+?\s+from\s+["']([^"']+)["']))/m);
    return match?.[1] ?? match?.[2] ?? null;
  }

  private readTopLevelBindingDeclarations(code: string): ModuleBindingDeclaration[] {
    const declarations: ModuleBindingDeclaration[] = [];
    const seen = new Set<string>();
    const add = (kind: ModuleBindingKind, name: string | undefined, captureIndex: number) => {
      if (!name || seen.has(name)) {
        return;
      }
      seen.add(name);
      declarations.push({ captureIndex, kind, name });
    };

    let depth = 0;
    let quote: string | null = null;
    let escaped = false;
    let lineComment = false;
    let blockComment = false;

    for (let index = 0; index < code.length; index += 1) {
      const char = code[index];
      const nextChar = code[index + 1];

      if (lineComment) {
        if (char === '\n' || char === '\r') {
          lineComment = false;
        }
        continue;
      }
      if (blockComment) {
        if (char === '*' && nextChar === '/') {
          blockComment = false;
          index += 1;
        }
        continue;
      }
      if (quote) {
        if (escaped) {
          escaped = false;
          continue;
        }
        if (char === '\\') {
          escaped = true;
          continue;
        }
        if (char === quote) {
          quote = null;
        }
        continue;
      }

      if (char === '/' && nextChar === '/') {
        lineComment = true;
        index += 1;
        continue;
      }
      if (char === '/' && nextChar === '*') {
        blockComment = true;
        index += 1;
        continue;
      }
      if (char === '"' || char === '\'' || char === '`') {
        quote = char;
        continue;
      }

      if (depth === 0 && this.isStatementKeywordPosition(code, index)) {
        const variableKind = this.readVariableDeclarationKeywordAt(code, index);
        if (variableKind) {
          const declarationEnd = this.findVariableDeclarationEnd(code, index + variableKind.length);
          const declarationBody = code.slice(index + variableKind.length, declarationEnd);
          for (const declarator of this.splitTopLevelDeclarators(declarationBody)) {
            for (const name of this.readVariableDeclaratorBindingNames(declarator)) {
              add(variableKind, name, declarationEnd);
            }
          }
          index = Math.max(index, declarationEnd - 1);
          continue;
        }

        if (this.matchesKeywordAt(code, index, 'function')) {
          add('function', this.readDeclaredNameAfterKeyword(code, index + 'function'.length), this.findBlockDeclarationEnd(code, index));
        } else if (this.matchesKeywordAt(code, index, 'class')) {
          add('class', this.readDeclaredNameAfterKeyword(code, index + 'class'.length), this.findBlockDeclarationEnd(code, index));
        }
      }

      if (char === '(' || char === '[' || char === '{') {
        depth += 1;
        continue;
      }
      if (char === ')' || char === ']' || char === '}') {
        depth = Math.max(0, depth - 1);
      }
    }

    return declarations;
  }

  private readVariableDeclarationKeywordAt(code: string, index: number): ModuleBindingKind | null {
    for (const keyword of ['const', 'let', 'var'] as const) {
      if (this.matchesKeywordAt(code, index, keyword)) {
        return keyword;
      }
    }
    return null;
  }

  private matchesKeywordAt(code: string, index: number, keyword: string): boolean {
    if (code.slice(index, index + keyword.length) !== keyword) {
      return false;
    }
    const before = code[index - 1];
    const after = code[index + keyword.length];
    return !this.isIdentifierPart(before) && !this.isIdentifierPart(after);
  }

  private isIdentifierPart(char: string | undefined): boolean {
    return typeof char === 'string' && /[A-Za-z0-9_$]/.test(char);
  }

  private isStatementKeywordPosition(code: string, index: number): boolean {
    const previousIndex = this.previousNonWhitespaceIndex(code, index);
    if (previousIndex < 0) {
      return true;
    }

    const between = code.slice(previousIndex + 1, index);
    const previousChar = code[previousIndex];
    if (previousChar === ';' || previousChar === '{' || previousChar === '}') {
      return true;
    }
    if (/\b(?:async|default|export)\s*$/.test(code.slice(0, index))) {
      return true;
    }
    return /[\r\n]/.test(between) && !'=(:,[!+-*/%?&|'.includes(previousChar);
  }

  private previousNonWhitespaceIndex(code: string, index: number): number {
    for (let cursor = index - 1; cursor >= 0; cursor -= 1) {
      if (!/\s/.test(code[cursor])) {
        return cursor;
      }
    }
    return -1;
  }

  private readDeclaredNameAfterKeyword(code: string, index: number): string | undefined {
    const rest = code.slice(index).replace(/^\s*\*\s*/, '').trimStart();
    return rest.match(/^([A-Za-z_$][\w$]*)/)?.[1];
  }

  private findBlockDeclarationEnd(code: string, index: number): number {
    let blockStart = -1;
    let quote: string | null = null;
    let escaped = false;
    let lineComment = false;
    let blockComment = false;

    for (let cursor = index; cursor < code.length; cursor += 1) {
      const char = code[cursor];
      const nextChar = code[cursor + 1];
      if (lineComment) {
        if (char === '\n' || char === '\r') {
          lineComment = false;
        }
        continue;
      }
      if (blockComment) {
        if (char === '*' && nextChar === '/') {
          blockComment = false;
          cursor += 1;
        }
        continue;
      }
      if (quote) {
        if (escaped) {
          escaped = false;
          continue;
        }
        if (char === '\\') {
          escaped = true;
          continue;
        }
        if (char === quote) {
          quote = null;
        }
        continue;
      }

      if (char === '/' && nextChar === '/') {
        lineComment = true;
        cursor += 1;
        continue;
      }
      if (char === '/' && nextChar === '*') {
        blockComment = true;
        cursor += 1;
        continue;
      }
      if (char === '"' || char === '\'' || char === '`') {
        quote = char;
        continue;
      }
      if (char === '{') {
        blockStart = cursor;
        break;
      }
    }

    if (blockStart < 0) {
      return index;
    }
    return this.findMatchingBraceEnd(code, blockStart);
  }

  private findMatchingBraceEnd(code: string, index: number): number {
    let depth = 0;
    let quote: string | null = null;
    let escaped = false;
    let lineComment = false;
    let blockComment = false;

    for (let cursor = index; cursor < code.length; cursor += 1) {
      const char = code[cursor];
      const nextChar = code[cursor + 1];
      if (lineComment) {
        if (char === '\n' || char === '\r') {
          lineComment = false;
        }
        continue;
      }
      if (blockComment) {
        if (char === '*' && nextChar === '/') {
          blockComment = false;
          cursor += 1;
        }
        continue;
      }
      if (quote) {
        if (escaped) {
          escaped = false;
          continue;
        }
        if (char === '\\') {
          escaped = true;
          continue;
        }
        if (char === quote) {
          quote = null;
        }
        continue;
      }

      if (char === '/' && nextChar === '/') {
        lineComment = true;
        cursor += 1;
        continue;
      }
      if (char === '/' && nextChar === '*') {
        blockComment = true;
        cursor += 1;
        continue;
      }
      if (char === '"' || char === '\'' || char === '`') {
        quote = char;
        continue;
      }
      if (char === '{') {
        depth += 1;
        continue;
      }
      if (char === '}') {
        depth -= 1;
        if (depth === 0) {
          return cursor + 1;
        }
      }
    }
    return code.length;
  }

  private findVariableDeclarationEnd(code: string, index: number): number {
    let depth = 0;
    let quote: string | null = null;
    let escaped = false;
    let lineComment = false;
    let blockComment = false;

    for (let cursor = index; cursor < code.length; cursor += 1) {
      const char = code[cursor];
      const nextChar = code[cursor + 1];

      if (lineComment) {
        if (char === '\n' || char === '\r') {
          lineComment = false;
          if (depth === 0 && !this.variableDeclarationContinues(code.slice(index, cursor))) {
            return cursor;
          }
        }
        continue;
      }
      if (blockComment) {
        if (char === '*' && nextChar === '/') {
          blockComment = false;
          cursor += 1;
        }
        continue;
      }
      if (quote) {
        if (escaped) {
          escaped = false;
          continue;
        }
        if (char === '\\') {
          escaped = true;
          continue;
        }
        if (char === quote) {
          quote = null;
        }
        continue;
      }

      if (char === '/' && nextChar === '/') {
        lineComment = true;
        cursor += 1;
        continue;
      }
      if (char === '/' && nextChar === '*') {
        blockComment = true;
        cursor += 1;
        continue;
      }
      if (char === '"' || char === '\'' || char === '`') {
        quote = char;
        continue;
      }
      if (char === '(' || char === '[' || char === '{') {
        depth += 1;
        continue;
      }
      if (char === ')' || char === ']' || char === '}') {
        depth = Math.max(0, depth - 1);
        continue;
      }
      if (depth === 0 && char === ';') {
        return cursor;
      }
      if (depth === 0 && (char === '\n' || char === '\r') && !this.variableDeclarationContinues(code.slice(index, cursor))) {
        return cursor;
      }
    }
    return code.length;
  }

  private variableDeclarationContinues(declarationBody: string): boolean {
    const trimmed = declarationBody.trimEnd();
    return trimmed.length === 0 || /(?:[,=({[?:]|\+\+|--|&&|\|\||\?\?)$/.test(trimmed);
  }

  private readVariableDeclaratorBindingNames(declarator: string): string[] {
    const pattern = this.readVariableDeclaratorPattern(declarator).trim();
    return this.readBindingPatternNames(pattern);
  }

  private readVariableDeclaratorPattern(declarator: string): string {
    let depth = 0;
    let quote: string | null = null;
    let escaped = false;

    for (let index = 0; index < declarator.length; index += 1) {
      const char = declarator[index];
      if (quote) {
        if (escaped) {
          escaped = false;
          continue;
        }
        if (char === '\\') {
          escaped = true;
          continue;
        }
        if (char === quote) {
          quote = null;
        }
        continue;
      }

      if (char === '"' || char === '\'' || char === '`') {
        quote = char;
        continue;
      }
      if (char === '(' || char === '[' || char === '{') {
        depth += 1;
        continue;
      }
      if (char === ')' || char === ']' || char === '}') {
        depth = Math.max(0, depth - 1);
        continue;
      }
      if (char === '=' && depth === 0) {
        return declarator.slice(0, index);
      }
    }
    return declarator;
  }

  private readBindingPatternNames(pattern: string): string[] {
    const trimmed = pattern.trim().replace(/^\.\.\./, '').trim();
    const identifier = trimmed.match(/^([A-Za-z_$][\w$]*)$/)?.[1];
    if (identifier) {
      return [identifier];
    }

    if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
      return this.readObjectBindingPatternNames(trimmed.slice(1, -1));
    }
    if (trimmed.startsWith('[') && trimmed.endsWith(']')) {
      return this.readArrayBindingPatternNames(trimmed.slice(1, -1));
    }
    return [];
  }

  private readObjectBindingPatternNames(patternBody: string): string[] {
    const names: string[] = [];
    for (const part of this.splitTopLevelDeclarators(patternBody)) {
      const property = part.trim();
      if (!property) {
        continue;
      }
      const colonIndex = this.findTopLevelCharacter(property, ':');
      const bindingPattern = colonIndex >= 0
        ? property.slice(colonIndex + 1)
        : this.readVariableDeclaratorPattern(property);
      names.push(...this.readBindingPatternNames(bindingPattern));
    }
    return names;
  }

  private readArrayBindingPatternNames(patternBody: string): string[] {
    const names: string[] = [];
    for (const part of this.splitTopLevelDeclarators(patternBody)) {
      names.push(...this.readBindingPatternNames(this.readVariableDeclaratorPattern(part)));
    }
    return names;
  }

  private findTopLevelCharacter(value: string, target: string): number {
    let depth = 0;
    let quote: string | null = null;
    let escaped = false;

    for (let index = 0; index < value.length; index += 1) {
      const char = value[index];
      if (quote) {
        if (escaped) {
          escaped = false;
          continue;
        }
        if (char === '\\') {
          escaped = true;
          continue;
        }
        if (char === quote) {
          quote = null;
        }
        continue;
      }

      if (char === '"' || char === '\'' || char === '`') {
        quote = char;
        continue;
      }
      if (char === '(' || char === '[' || char === '{') {
        depth += 1;
        continue;
      }
      if (char === ')' || char === ']' || char === '}') {
        depth = Math.max(0, depth - 1);
        continue;
      }
      if (char === target && depth === 0) {
        return index;
      }
    }
    return -1;
  }

  private splitTopLevelDeclarators(value: string): string[] {
    const declarators: string[] = [];
    let current = '';
    let depth = 0;
    let quote: string | null = null;
    let escaped = false;

    for (const char of value) {
      if (quote) {
        current += char;
        if (escaped) {
          escaped = false;
          continue;
        }
        if (char === '\\') {
          escaped = true;
          continue;
        }
        if (char === quote) {
          quote = null;
        }
        continue;
      }

      if (char === '"' || char === '\'' || char === '`') {
        quote = char;
        current += char;
        continue;
      }
      if (char === '(' || char === '[' || char === '{') {
        depth += 1;
        current += char;
        continue;
      }
      if (char === ')' || char === ']' || char === '}') {
        depth = Math.max(0, depth - 1);
        current += char;
        continue;
      }
      if (char === ',' && depth === 0) {
        declarators.push(current);
        current = '';
        continue;
      }
      current += char;
    }

    if (current.trim()) {
      declarators.push(current);
    }
    return declarators;
  }

  private isImportMetaSyntaxError(error: unknown): boolean {
    return this.isSyntaxError(error)
      && /Cannot use 'import\.meta' outside a module/.test((error as Error).message);
  }

  private isSyntaxError(error: unknown): boolean {
    return error instanceof SyntaxError || (error instanceof Error && error.name === 'SyntaxError');
  }
}

export class NodeReplTimeoutError extends Error {
  constructor(_timeoutMs: number) {
    super('js execution timed out; kernel reset, rerun your request');
    this.name = 'NodeReplTimeoutError';
  }
}
