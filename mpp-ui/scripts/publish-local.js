#!/usr/bin/env node

/**
 * Local publish script for development
 * This creates a local npm package that can be installed with npm link
 */

import { readFileSync, existsSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const rootDir = resolve(__dirname, '..');
const packageJsonPath = resolve(rootDir, 'package.json');

console.log('📦 Local Publish Script for @autodev/cli\n');

try {
  // Step 1: Check if mpp-core is built
  console.log('1️⃣  Checking mpp-core build...');
  const mppCorePath = resolve(rootDir, '../mpp-core/build/packages/js/autodev-mpp-core.js');
  const mppCorePackageJson = resolve(rootDir, '../mpp-core/build/packages/js/package.json');

  if (!existsSync(mppCorePath) || !existsSync(mppCorePackageJson)) {
    console.error('❌ mpp-core not built!');
    console.error('   Run: npm run build:kotlin');
    process.exit(1);
  }
  console.log('✅ mpp-core build found\n');

  // Step 2: Verify package.json has local dependency
  console.log('2️⃣  Verifying package.json...');
  const packageJson = JSON.parse(readFileSync(packageJsonPath, 'utf-8'));
  const mppCoreDep = packageJson.dependencies['@xiuper/mpp-core'];

  if (!mppCoreDep || !mppCoreDep.startsWith('file:../mpp-core/build/packages/js')) {
    console.error('❌ package.json should use local file: dependency');
    console.error('   Expected: "file:../mpp-core/build/packages/js"');
    console.error('   Found:', mppCoreDep);
    process.exit(1);
  }
  console.log('✅ Using local mpp-core dependency\n');

  // Step 3: Build TypeScript
  console.log('3️⃣  Building TypeScript...');
  execSync('npm run build:ts', { cwd: rootDir, stdio: 'inherit' });
  console.log('✅ TypeScript build complete\n');

  // Step 4: Run tests
  console.log('4️⃣  Running tests...');
  try {
    execSync('npm test', { cwd: rootDir, stdio: 'inherit' });
    console.log('✅ Tests passed\n');
  } catch (error) {
    console.warn('⚠️  Some tests failed, continuing anyway...\n');
  }

  // Step 5: Prepare bundled Node runtime
  console.log('5. Preparing bundled Node runtime...');
  execSync('npm run prepare:node-runtime', { cwd: rootDir, stdio: 'inherit' });
  console.log('Bundled Node runtime ready\n');

  // Step 6: Prepare bundled node_modules
  console.log('6. Preparing bundled node_modules...');
  execSync('npm run prepare:node-modules', { cwd: rootDir, stdio: 'inherit' });
  console.log('Bundled node_modules ready\n');

  // Step 7: Create local package
  console.log('7. Creating local package...');
  execSync('npm pack', { cwd: rootDir, stdio: 'inherit' });
  console.log('✅ Package created\n');

  console.log('🎉 Local publish complete!\n');
  console.log('To install globally for testing:');
  console.log('  npm link');
  console.log('  autodev --help');
  console.log('\nOr install the .tgz file:');
  console.log('  npm install -g autodev-cli-' + packageJson.version + '.tgz');

} catch (error) {
  console.error('❌ Local publish failed:', error.message);
  process.exit(1);
}
