import { builtinModules, createRequire } from 'node:module';
import * as childProcess from 'node:child_process';
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
const PACKAGE_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..');
const BLOCKED_BUILTIN_MODULES = new Set(['process', 'node:process']);
const BUILTIN_MODULES = new Set([
  ...builtinModules,
  ...builtinModules.map((moduleName) => `node:${moduleName}`),
]);

export class NodeReplRuntime {
  private context: vm.Context;
  private additionalModuleDirs: string[] = [];
  private activeExecution: ActiveExecution | null = null;
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

    sandbox.console = this.createConsole();
    sandbox.nodeRepl = this.createNodeReplApi();
    (globalThis as any).nodeRepl = sandbox.nodeRepl;

    return vm.createContext(sandbox, {
      name: 'autodev-node-repl',
      origin: pathToFileURL(this.cwd).href,
    });
  }

  private createConsole(): Console {
    const write = (args: unknown[], newline: boolean) => {
      const text = args.map((item) => this.formatValue(item)).join(' ');
      this.writeOutput(newline ? `${text}\n` : text);
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
      addNodeModuleDir: (dirPath: string) => this.addNodeModuleDir(dirPath),
      config: this.createConfigApi(),
      env: this.createEnvApi(),
      fetch: (input: RequestInfo | URL, init?: RequestInit) => fetch(input, init),
      import: (specifier: string) => this.resolveAndImport(specifier),
      nativePipe: {
        createConnection: (pipePath: string) => this.createNativePipeConnection(pipePath),
      },
      withSuspendedTimeout: async (callback: unknown) => {
        if (typeof callback !== 'function') {
          throw new Error('nodeRepl.withSuspendedTimeout expects a function');
        }
        return await (callback as () => unknown | Promise<unknown>)();
      },
      launchServices: {
        openApplication: (applicationPathOrBundleId: string) => this.openApplication(applicationPathOrBundleId),
      },
      write: (text: unknown) => {
        if (typeof text !== 'string') {
          throw new Error('nodeRepl.write expects a string');
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

    return api;
  }

  private createEnvApi(): Record<string, string> {
    const allowlist = this.parseEnvAllowlist();
    const keys = allowlist.length > 0 ? allowlist : Object.keys(process.env);
    const env: Record<string, string> = {};
    for (const key of keys) {
      const value = process.env[key];
      if (typeof value === 'string') {
        env[key] = value;
      }
    }
    return Object.freeze(env);
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
      read: async () => ({}),
      readRequirements: async () => ({}),
      readToml: async () => ({}),
      writeToml: async () => undefined,
      writeValue: async () => undefined,
      batchWrite: async () => undefined,
    };
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
      throw new Error(`Importing ${specifier} is not allowed in node_repl`);
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
