#!/usr/bin/env node

/**
 * Prepare a bundled Node.js runtime for the node_repl MCP server.
 *
 * Usage:
 *   node scripts/prepare-node-runtime.js
 *   AUTODEV_NODE_VERSION=22.22.0 node scripts/prepare-node-runtime.js
 *   AUTODEV_NODE_SOURCE=/path/to/node node scripts/prepare-node-runtime.js
 */

import { createWriteStream, existsSync, mkdirSync, rmSync, statSync, copyFileSync, chmodSync } from 'node:fs';
import { cp } from 'node:fs/promises';
import { get } from 'node:https';
import { dirname, resolve, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { tmpdir } from 'node:os';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = resolve(__dirname, '..');

const platformArch = mapPlatformArch(process.platform, process.arch);
const nodeVersion = (process.env.AUTODEV_NODE_VERSION || process.versions.node).replace(/^v/, '');
const sourcePath = process.env.AUTODEV_NODE_SOURCE;
const targetDir = resolve(rootDir, 'vendor', 'node', platformArch);

if (!platformArch) {
  console.error(`Unsupported platform for bundled Node runtime: ${process.platform}/${process.arch}`);
  process.exit(1);
}

mkdirSync(targetDir, { recursive: true });

if (sourcePath) {
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
