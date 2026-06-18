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
  output: string[];
  images: NodeReplImageContent[];
  responseMeta: Record<string, unknown>;
  requestMeta: Record<string, unknown>;
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
  private fileHashCache = new Map<string, string | null>();
  private requireForResolve = createRequire(path.join(process.cwd(), 'node_repl_runtime.js'));

  constructor(private readonly cwd: string = process.cwd()) {
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
    if (!fs.existsSync(resolvedPath)) {
      throw new Error(`Node module directory does not exist: ${resolvedPath}`);
    }

    const stats = fs.statSync(resolvedPath);
    if (!stats.isDirectory()) {
      throw new Error(`Node module path is not a directory: ${resolvedPath}`);
    }

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
      output: [],
      images: [],
      responseMeta: {},
      requestMeta: options.requestMeta ?? {},
    };
    this.activeExecution = activeExecution;

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
      homeDir: os.homedir(),
      tmpDir: os.tmpdir(),
      env: this.createEnvApi(),
      write: (text: unknown) => {
        if (typeof text !== 'string') {
          throw new Error('nodeRepl.write expected a string');
        }
        this.writeOutput(text);
      },
      emitImage: async (imageLike: unknown) => this.emitImage(imageLike),
      setResponseMeta: (meta: Record<string, unknown>) => {
        if (!meta || typeof meta !== 'object' || Array.isArray(meta)) {
          throw new Error('nodeRepl.setResponseMeta expects an object');
        }
        Object.assign(this.getActiveExecution().responseMeta, meta);
      },
    };

    Object.defineProperty(api, 'requestMeta', {
      enumerable: true,
      get: () => this.activeExecution?.requestMeta ?? {},
    });

    this.defineTrustedApiGetter(api, 'config', () => this.createConfigApi());
    this.defineTrustedApiGetter(api, 'fetch', () => (input: RequestInfo | URL, init?: RequestInit) => fetch(input, init));
    this.defineTrustedApiGetter(api, 'nativePipe', () => ({
      createConnection: (pipePath: string) => this.createNativePipeConnection(pipePath),
    }));
    this.defineTrustedApiGetter(api, 'withSuspendedTimeout', () => async (callback: unknown) => {
      if (typeof callback !== 'function') {
        throw new Error('nodeRepl.withSuspendedTimeout expects a function');
      }
      return await (callback as () => unknown | Promise<unknown>)();
    });
    this.defineTrustedApiGetter(api, 'launchServices', () => ({
      openApplication: (applicationPathOrBundleId: string) => this.openApplication(applicationPathOrBundleId),
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

  private createEnvApi(): Record<string, string> {
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
      .split(/[,:;]/)
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
    return await new Promise<net.Socket>((resolve, reject) => {
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
  }

  private nativePipeConnectTimeoutMs(): number {
    const rawValue = process.env[NODE_REPL_NATIVE_PIPE_CONNECT_TIMEOUT_ENV];
    if (!rawValue) {
      return DEFAULT_NATIVE_PIPE_CONNECT_TIMEOUT_MS;
    }
    const value = Number(rawValue);
    return Number.isFinite(value) && value >= 0 ? value : DEFAULT_NATIVE_PIPE_CONNECT_TIMEOUT_MS;
  }

  private async openApplication(applicationPathOrBundleId: string): Promise<void> {
    if (!applicationPathOrBundleId || typeof applicationPathOrBundleId !== 'string') {
      throw new Error('nodeRepl.launchServices.openApplication expects an application path or bundle id');
    }
    if (process.platform !== 'darwin') {
      throw new Error('nodeRepl.launchServices.openApplication is only available on macOS');
    }

    const args = applicationPathOrBundleId.endsWith('.app') || applicationPathOrBundleId.startsWith('/')
      ? [applicationPathOrBundleId]
      : ['-b', applicationPathOrBundleId];

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
  }

  private async evaluate(code: string): Promise<unknown> {
    try {
      return await this.runScript(code);
    } catch (error) {
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
    if (!specifier || typeof specifier !== 'string') {
      throw new Error('import specifier must be a non-empty string');
    }

    if (BLOCKED_BUILTIN_MODULES.has(specifier)) {
      throw new Error(`Importing module "${specifier}" is not allowed in node_repl`);
    }

    if (BUILTIN_MODULES.has(specifier)) {
      return import(specifier);
    }

    if (specifier.startsWith('file://') || specifier.startsWith('data:') || specifier.startsWith('node:')) {
      return import(specifier);
    }

    if (specifier.startsWith('.') || specifier.startsWith('/') || specifier.startsWith('..')) {
      const resolvedPath = path.resolve(this.cwd, specifier);
      return import(pathToFileURL(resolvedPath).href);
    }

    const resolvedPath = this.requireForResolve.resolve(specifier, {
      paths: this.moduleSearchRoots(),
    });
    return import(pathToFileURL(resolvedPath).href);
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
    const image = this.normalizeImage(imageLike);
    this.getActiveExecution().images.push(image);
  }

  private normalizeImage(imageLike: unknown): NodeReplImageContent {
    if (typeof imageLike === 'string') {
      const dataUrl = this.parseImageDataUrl(imageLike);
      if (!dataUrl) {
        throw new Error('nodeRepl.emitImage expects image bytes, an image/* data URL, or { bytes, mimeType }');
      }
      return dataUrl;
    }

    const directBytes = this.toImageBuffer(imageLike);
    if (directBytes) {
      return {
        type: 'image',
        mimeType: this.inferImageMimeType(directBytes),
        data: directBytes.toString('base64'),
      };
    }

    if (imageLike && typeof imageLike === 'object' && !Array.isArray(imageLike)) {
      const candidate = imageLike as {
        base64?: unknown;
        bytes?: unknown;
        data?: unknown;
        image_url?: unknown;
        mimeType?: unknown;
        mime_type?: unknown;
      };

      const imageUrl = this.extractImageUrl(candidate.image_url);
      if (imageUrl) {
        const dataUrl = this.parseImageDataUrl(imageUrl);
        if (!dataUrl) {
          throw new Error('nodeRepl.emitImage image_url must be an image/* data URL');
        }
        return dataUrl;
      }

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

    throw new Error('nodeRepl.emitImage expects image bytes, an image/* data URL, or { bytes, mimeType }');
  }

  private parseImageDataUrl(value: string): NodeReplImageContent | null {
    const match = value.match(/^data:(image\/[A-Za-z0-9.+-]+)(?:;[^,]*)?;base64,(.+)$/);
    if (!match) {
      return null;
    }
    return { type: 'image', mimeType: match[1], data: match[2] };
  }

  private extractImageUrl(value: unknown): string | null {
    if (typeof value === 'string' && value.length > 0) {
      return value;
    }
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      const candidate = value as { url?: unknown };
      return typeof candidate.url === 'string' && candidate.url.length > 0 ? candidate.url : null;
    }
    return null;
  }

  private readImageMimeType(candidate: { mimeType?: unknown; mime_type?: unknown }): string | null {
    const mimeType = typeof candidate.mimeType === 'string' ? candidate.mimeType : candidate.mime_type;
    if (typeof mimeType !== 'string') {
      return null;
    }
    if (!mimeType.startsWith('image/')) {
      throw new Error('nodeRepl.emitImage object input requires an image/* mimeType');
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
      if (dataUrl) {
        return Buffer.from(dataUrl.data, 'base64');
      }
      return Buffer.from(value, 'base64');
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
    throw new Error('nodeRepl.emitImage could not infer image MIME type; pass mimeType explicitly');
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
      throw new Error('nodeRepl API is only available during js execution');
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

    let timeout: NodeJS.Timeout | undefined;
    try {
      return await Promise.race([
        promise,
        new Promise<T>((_, reject) => {
          timeout = setTimeout(() => reject(new NodeReplTimeoutError(timeoutMs)), timeoutMs);
        }),
      ]);
    } finally {
      if (timeout) {
        clearTimeout(timeout);
      }
    }
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

  private isSyntaxError(error: unknown): boolean {
    return error instanceof SyntaxError || (error instanceof Error && error.name === 'SyntaxError');
  }
}

export class NodeReplTimeoutError extends Error {
  constructor(timeoutMs: number) {
    super(`js execution timed out after ${timeoutMs}ms; runtime reset`);
    this.name = 'NodeReplTimeoutError';
  }
}
