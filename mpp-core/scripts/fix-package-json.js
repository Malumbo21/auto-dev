#!/usr/bin/env node

/**
 * Fix package.json for mpp-core
 * Updates the package name to use @xiuper scope and correct version
 */

import { readFileSync, writeFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const packageJsonPath = resolve(__dirname, '../build/dist/js/productionLibrary/package.json');
const rootPropertiesPath = resolve(__dirname, '../../gradle.properties');

// Read mppVersion from gradle.properties
let mppVersion = '0.0.1';
try {
  const properties = readFileSync(rootPropertiesPath, 'utf-8');
  const match = properties.match(/mppVersion\s*=\s*(.+)/);
  if (match) {
    mppVersion = match[1].trim();
  }
} catch (error) {
  console.warn('⚠️  Could not read gradle.properties, using default version:', mppVersion);
}

try {
  const packageJson = JSON.parse(readFileSync(packageJsonPath, 'utf-8'));
  
  // Update name to use @xiuper scope
  packageJson.name = '@xiuper/mpp-core';
  packageJson.version = mppVersion;
  
  writeFileSync(packageJsonPath, JSON.stringify(packageJson, null, 2) + '\n');
  
  console.log('✅ Fixed package.json:');
  console.log('   Name:', packageJson.name);
  console.log('   Version:', packageJson.version);
} catch (error) {
  console.error('❌ Error fixing package.json:', error.message);
  process.exit(1);
}

