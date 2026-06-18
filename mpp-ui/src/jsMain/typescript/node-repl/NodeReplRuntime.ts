import { builtinModules, createRequire } from 'node:module';
import * as fs from 'node:fs';
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
const NODE_MODULE_DIRS_ENV = 'NODE_REPL_NODE_MODULE_DIRS';
const PACKAGE_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..');
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

  addNodeModuleDir(dirPath: string): string {
    if (!dirPath || typeof dirPath !== 'string') {
      throw new Error('path must be a non-empty string');
    }

    const resolvedPath = path.resolve(this.cwd, dirPath);
    if (!fs.existsSync(resolvedPath)) {
      throw new Error(`Node module directory does not exist: ${resolvedPath}`);
    }

    const stats = fs.statSync(resolvedPath);
    if (!stats.isDirectory()) {
      throw new Error(`Node module path is not a directory: ${resolvedPath}`);
    }

    if (!this.additionalModuleDirs.includes(resolvedPath)) {
      this.additionalModuleDirs.push(resolvedPath);
    }

    return resolvedPath;
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
      const value = await this.withTimeout(this.evaluate(code), timeoutMs);
      if (value !== undefined && activeExecution.output.length === 0 && activeExecution.images.length === 0) {
        activeExecution.output.push(this.formatValue(value));
      }

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
      import: (specifier: string) => this.resolveAndImport(specifier),
      write: (text: unknown) => this.writeOutput(String(text)),
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
      const match = imageLike.match(/^data:(image\/[A-Za-z0-9.+-]+);base64,(.+)$/);
      if (!match) {
        throw new Error('nodeRepl.emitImage only accepts image/* data URLs or { bytes, mimeType }');
      }
      return { type: 'image', mimeType: match[1], data: match[2] };
    }

    if (imageLike && typeof imageLike === 'object' && !Array.isArray(imageLike)) {
      const candidate = imageLike as { bytes?: unknown; mimeType?: unknown };
      if (typeof candidate.mimeType !== 'string' || !candidate.mimeType.startsWith('image/')) {
        throw new Error('nodeRepl.emitImage object input requires an image/* mimeType');
      }
      if (candidate.bytes instanceof Uint8Array) {
        return {
          type: 'image',
          mimeType: candidate.mimeType,
          data: Buffer.from(candidate.bytes).toString('base64'),
        };
      }
      if (typeof candidate.bytes === 'string') {
        return {
          type: 'image',
          mimeType: candidate.mimeType,
          data: Buffer.from(candidate.bytes).toString('base64'),
        };
      }
    }

    throw new Error('nodeRepl.emitImage only accepts image/* data URLs or { bytes, mimeType }');
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
