#!/usr/bin/env node

import { readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptPath = fileURLToPath(import.meta.url);
const scriptDir = path.dirname(scriptPath);
const webClientDir = path.resolve(scriptDir, '..');
const repoRootDir = path.resolve(webClientDir, '..');

const PUBLIC_PREFIX = ['VITE', ''].join('_');
const KEYWORDS = ['PASSWORD', 'PASS', 'SECRET', 'TOKEN', 'APIKEY', 'API_KEY', 'PRIVATE', 'CREDENTIAL'];
const ENV_KEY_PATTERN = /^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=/;

const listEnvFiles = (dir) =>
  readdirSync(dir, { withFileTypes: true })
    .filter((entry) => entry.isFile() && entry.name.startsWith('.env'))
    .map((entry) => path.join(dir, entry.name));

const hasSecretLikeName = (key) => {
  const upper = key.toUpperCase();
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

const files = [...listEnvFiles(repoRootDir), ...listEnvFiles(webClientDir)];
const findings = files.flatMap((filePath) => scanFile(filePath));

if (findings.length > 0) {
  console.error('[verify:no-public-secrets] VITE_公開変数に秘密名キーワードを含むキーを検出しました。');
  for (const finding of findings) {
    console.error(` - ${path.relative(repoRootDir, finding.filePath)}:${finding.line} ${finding.key}`);
  }
  process.exit(2);
}

console.log('[verify:no-public-secrets] 問題は検出されませんでした。');
