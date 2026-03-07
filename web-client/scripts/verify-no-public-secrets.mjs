#!/usr/bin/env node

import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptPath = fileURLToPath(import.meta.url);
const scriptDir = path.dirname(scriptPath);
const repoRootDir = path.resolve(scriptDir, '..');

const PUBLIC_PREFIX = ['VITE', ''].join('_');
const KEYWORDS = ['PASSWORD', 'PASS', 'SECRET', 'TOKEN', 'APIKEY', 'API_KEY', 'PRIVATE', 'CREDENTIAL'];
const DENYLIST = new Set(['VITE_ORCA_MASTER_USER', 'VITE_ORCA_MASTER_PASSWORD']);
const ENV_KEY_PATTERN = /^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=/;
const ENV_FILE_PATTERN = /^\.env(?:\..*)?$/;
const WALK_SKIP_DIRS = new Set(['.git', 'node_modules', 'dist', 'coverage', 'build', 'artifacts', 'test-results']);

const listEnvFiles = (rootDir) => {
  const found = [];
  const walk = (currentDir) => {
    const entries = readdirSync(currentDir, { withFileTypes: true });
    for (const entry of entries) {
      if (entry.name === '.' || entry.name === '..') continue;
      const fullPath = path.join(currentDir, entry.name);
      if (entry.isDirectory()) {
        if (WALK_SKIP_DIRS.has(entry.name)) continue;
        walk(fullPath);
        continue;
      }
      if (ENV_FILE_PATTERN.test(entry.name)) {
        found.push(fullPath);
      }
    }
  };
  walk(rootDir);
  return found;
};

const hasSecretLikeName = (key) => {
  const upper = key.toUpperCase();
  if (DENYLIST.has(upper)) {
    return true;
  }
  return upper.startsWith(PUBLIC_PREFIX) && KEYWORDS.some((keyword) => upper.includes(keyword));
};

const scanFile = (filePath) => {
  const content = readFileSync(filePath, 'utf8');
  const lines = content.split(/\r?\n/);
  const findings = [];
  lines.forEach((line, index) => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) return;
    const match = line.match(ENV_KEY_PATTERN);
    if (!match) return;
    const key = match[1];
    if (key && hasSecretLikeName(key)) {
      findings.push({ filePath, line: index + 1, key });
    }
  });
  return findings;
};

const files = listEnvFiles(repoRootDir);
const findings = files.flatMap((filePath) => scanFile(filePath));

if (findings.length > 0) {
  console.error('[verify:no-public-secrets] web-client 配下の .env* から VITE_公開変数に秘密名キーワードを含むキーを検出しました。');
  for (const finding of findings) {
    console.error(` - ${path.relative(repoRootDir, finding.filePath)}:${finding.line} ${finding.key}`);
  }
  process.exit(2);
}

console.log('[verify:no-public-secrets] web-client 配下の .env* に問題は検出されませんでした。');
