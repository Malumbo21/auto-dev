#!/usr/bin/env node

/**
 * Prepare bundled node_modules for the node_repl MCP runtime.
 *
 * Usage:
 *   node scripts/prepare-node-modules.js
 *   AUTODEV_NODE_MODULES_SOURCE=/Applications/Codex.app/Contents/Resources/cua_node/lib/node_modules node scripts/prepare-node-modules.js
 */

import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { cp } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = resolve(__dirname, '..');
const manifestPath = resolve(rootDir, 'node-repl.modules.json');
const targetDir = resolve(rootDir, 'vendor', 'node_modules');
const defaultCodexCuaNodeDir = '/Applications/Codex.app/Contents/Resources/cua_node';
const codexModulesSource = process.env.AUTODEV_NODE_MODULES_SOURCE
  ? ''
  : detectCodexNodeModulesSource();
const sourceDir = process.env.AUTODEV_NODE_MODULES_SOURCE || codexModulesSource;

if (sourceDir) {
  if (sourceDir === codexModulesSource) {
    console.log(`Using Codex CUA Node modules source: ${sourceDir}`);
  }
  await copyNodeModules(resolve(sourceDir), targetDir);
  console.log(`Bundled node_modules copied from ${sourceDir}`);
  console.log(`Bundled node_modules ready: ${targetDir}`);
  process.exit(0);
}

if (!existsSync(manifestPath)) {
  console.error(`Missing node_repl module manifest: ${manifestPath}`);
  process.exit(1);
}

const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
const dependencies = manifest.dependencies ?? {};
const optionalDependencies = manifest.optionalDependencies ?? {};
const codexPrivatePackages = manifest.codexPrivatePackages ?? {};
const packageNames = [
  ...Object.entries(dependencies),
  ...Object.entries(optionalDependencies),
].map(([name, version]) => `${name}@${version}`);

if (packageNames.length === 0) {
  rmSync(targetDir, { recursive: true, force: true });
  mkdirSync(targetDir, { recursive: true });
  console.log(`No node_repl modules configured; created empty ${targetDir}`);
  process.exit(0);
}

const workDir = resolve(rootDir, 'vendor', '.node-repl-modules-work');
rmSync(workDir, { recursive: true, force: true });
mkdirSync(workDir, { recursive: true });

writeFileSync(resolve(workDir, 'package.json'), JSON.stringify({
  private: true,
  name: '@xiuper/node-repl-modules',
  version: '0.0.0',
  dependencies,
  optionalDependencies,
}, null, 2) + '\n');

const env = {
  ...process.env,
  PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD: process.env.PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD ?? '1',
  npm_config_audit: 'false',
  npm_config_fund: 'false',
};

console.log(`Installing node_repl modules: ${packageNames.join(', ')}`);
if (Object.keys(codexPrivatePackages).length > 0) {
  console.log(`Codex private modules require a source copy and will not be installed from npm: ${Object.entries(codexPrivatePackages).map(([name, version]) => `${name}@${version}`).join(', ')}`);
}
execFileSync('npm', ['install', '--omit=dev', '--ignore-scripts'], {
  cwd: workDir,
  env,
  stdio: 'inherit',
});

await copyNodeModules(resolve(workDir, 'node_modules'), targetDir);
rmSync(workDir, { recursive: true, force: true });

console.log(`Bundled node_modules ready: ${targetDir}`);

async function copyNodeModules(source, target) {
  if (!existsSync(source)) {
    throw new Error(`node_modules source does not exist: ${source}`);
  }

  rmSync(target, { recursive: true, force: true });
  mkdirSync(dirname(target), { recursive: true });
  await cp(source, target, {
    recursive: true,
    force: true,
    verbatimSymlinks: false,
  });
  rmSync(resolve(target, '.bin'), { recursive: true, force: true });
}

function detectCodexNodeModulesSource() {
  if (process.env.AUTODEV_NODE_MODULES_USE_CODEX_SOURCE === '0') {
    return '';
  }

  const source = resolve(
    process.env.AUTODEV_CODEX_CUA_NODE_DIR || defaultCodexCuaNodeDir,
    'lib',
    'node_modules',
  );
  return existsSync(source) ? source : '';
}
