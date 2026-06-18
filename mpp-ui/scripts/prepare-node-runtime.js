#!/usr/bin/env node

/**
 * Prepare a bundled Node.js runtime for the node_repl MCP server.
 *
 * Usage:
 *   node scripts/prepare-node-runtime.js
 *   AUTODEV_NODE_VERSION=22.22.0 node scripts/prepare-node-runtime.js
 *   AUTODEV_NODE_SOURCE=/path/to/node node scripts/prepare-node-runtime.js
 */

import { createWriteStream, existsSync, mkdirSync, rmSync, statSync, copyFileSync, chmodSync, readFileSync } from 'node:fs';
import { cp } from 'node:fs/promises';
import { get } from 'node:https';
import { dirname, resolve, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { tmpdir } from 'node:os';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = resolve(__dirname, '..');
const defaultCodexCuaNodeDir = '/Applications/Codex.app/Contents/Resources/cua_node';
const defaultNodeVersion = '24.14.0';

const platformArch = mapPlatformArch(process.platform, process.arch);
const codexRuntimeSource = process.env.AUTODEV_NODE_SOURCE
  ? ''
  : detectCodexRuntimeSource(platformArch);
const sourcePath = process.env.AUTODEV_NODE_SOURCE || codexRuntimeSource;
const nodeVersion = (process.env.AUTODEV_NODE_VERSION || readCodexNodeVersion(codexRuntimeSource) || defaultNodeVersion).replace(/^v/, '');
const targetDir = resolve(rootDir, 'vendor', 'node', platformArch);

if (!platformArch) {
  console.error(`Unsupported platform for bundled Node runtime: ${process.platform}/${process.arch}`);
  process.exit(1);
}

mkdirSync(targetDir, { recursive: true });

if (sourcePath) {
  if (sourcePath === codexRuntimeSource) {
    console.log(`Using Codex CUA Node runtime source: ${sourcePath}`);
  }
  await copyNodeFromSource(resolve(sourcePath), targetDir);
} else {
  await downloadNodeRuntime(nodeVersion, platformArch, targetDir);
}

const nodeBin = process.platform === 'win32'
  ? join(targetDir, 'node.exe')
  : join(targetDir, 'bin', 'node');

if (!existsSync(nodeBin)) {
  console.error(`Bundled Node runtime was not created at ${nodeBin}`);
  process.exit(1);
}

console.log(`Bundled Node runtime ready: ${nodeBin}`);

function mapPlatformArch(platform, arch) {
  const normalizedArch = arch === 'x64' ? 'x64' : arch === 'arm64' ? 'arm64' : '';
  if (!normalizedArch) {
    return '';
  }
  if (platform === 'darwin') {
    return `darwin-${normalizedArch}`;
  }
  if (platform === 'linux') {
    return `linux-${normalizedArch}`;
  }
  if (platform === 'win32') {
    return `win32-${normalizedArch}`;
  }
  return '';
}

async function copyNodeFromSource(source, target) {
  if (!existsSync(source)) {
    throw new Error(`AUTODEV_NODE_SOURCE does not exist: ${source}`);
  }

  const stats = statSync(source);
  rmSync(target, { recursive: true, force: true });

  if (stats.isFile()) {
    const binDir = process.platform === 'win32' ? target : join(target, 'bin');
    mkdirSync(binDir, { recursive: true });
    const targetBinary = process.platform === 'win32' ? join(target, 'node.exe') : join(binDir, 'node');
    copyFileSync(source, targetBinary);
    chmodSync(targetBinary, 0o755);
    return;
  }

  const sourceBinary = process.platform === 'win32'
    ? join(source, 'node.exe')
    : join(source, 'bin', 'node');

  if (!existsSync(sourceBinary)) {
    throw new Error(`AUTODEV_NODE_SOURCE must be a node binary or Node distribution directory: ${source}`);
  }

  mkdirSync(target, { recursive: true });
  await cp(source, target, { recursive: true, force: true });
}

function detectCodexRuntimeSource(platformKey) {
  if (process.env.AUTODEV_NODE_USE_CODEX_SOURCE === '0') {
    return '';
  }

  const source = resolve(process.env.AUTODEV_CODEX_CUA_NODE_DIR || defaultCodexCuaNodeDir);
  if (!existsSync(source)) {
    return '';
  }

  const manifest = readJsonIfExists(join(source, 'manifest.json'));
  if (manifest?.target && manifest.target !== platformKey) {
    return '';
  }

  const nodeBin = process.platform === 'win32'
    ? join(source, 'node.exe')
    : join(source, 'bin', 'node');
  return existsSync(nodeBin) ? source : '';
}

function readCodexNodeVersion(source) {
  if (!source) {
    return '';
  }
  const manifest = readJsonIfExists(join(source, 'manifest.json'));
  return typeof manifest?.node_version === 'string' ? manifest.node_version : '';
}

function readJsonIfExists(filePath) {
  if (!existsSync(filePath)) {
    return null;
  }
  try {
    return JSON.parse(readFileSync(filePath, 'utf8'));
  } catch {
    return null;
  }
}

async function downloadNodeRuntime(version, platformKey, target) {
  const nodeDistName = platformKey.startsWith('win32')
    ? `node-v${version}-${platformKey}.zip`
    : `node-v${version}-${platformKey}.tar.xz`;
  const url = `https://nodejs.org/dist/v${version}/${nodeDistName}`;
  const tempRoot = resolve(tmpdir(), `autodev-node-${process.pid}`);
  const archivePath = resolve(tempRoot, nodeDistName);

  rmSync(tempRoot, { recursive: true, force: true });
  mkdirSync(tempRoot, { recursive: true });

  console.log(`Downloading Node.js v${version} from ${url}`);
  await downloadFile(url, archivePath);

  rmSync(target, { recursive: true, force: true });
  mkdirSync(dirname(target), { recursive: true });

  if (nodeDistName.endsWith('.zip')) {
    execFileSync('powershell.exe', [
      '-NoProfile',
      '-Command',
      `Expand-Archive -LiteralPath ${JSON.stringify(archivePath)} -DestinationPath ${JSON.stringify(tempRoot)}`,
    ], { stdio: 'inherit' });
  } else {
    execFileSync('tar', ['-xJf', archivePath, '-C', tempRoot], { stdio: 'inherit' });
  }

  const extractedDir = resolve(tempRoot, nodeDistName.replace(/\.tar\.xz$|\.zip$/u, ''));
  if (!existsSync(extractedDir)) {
    throw new Error(`Node archive did not extract to expected directory: ${extractedDir}`);
  }

  await cp(extractedDir, target, { recursive: true, force: true });
  rmSync(tempRoot, { recursive: true, force: true });
}

function downloadFile(url, targetFile) {
  return new Promise((resolvePromise, reject) => {
    const request = get(url, (response) => {
      if (response.statusCode && response.statusCode >= 300 && response.statusCode < 400 && response.headers.location) {
        downloadFile(response.headers.location, targetFile).then(resolvePromise, reject);
        return;
      }

      if (response.statusCode !== 200) {
        reject(new Error(`Download failed with HTTP ${response.statusCode}: ${url}`));
        response.resume();
        return;
      }

      const file = createWriteStream(targetFile);
      response.pipe(file);
      file.on('finish', () => {
        file.close(resolvePromise);
      });
      file.on('error', reject);
    });

    request.on('error', reject);
  });
}
