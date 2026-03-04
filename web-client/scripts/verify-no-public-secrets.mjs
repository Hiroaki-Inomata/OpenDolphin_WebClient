#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptPath = fileURLToPath(import.meta.url);
const scriptDir = path.dirname(scriptPath);
const repoRootDir = path.resolve(scriptDir, '..', '..');

const PUBLIC_PREFIX = ['VITE', ''].join('_');
const KEYWORDS = ['PASSWORD', 'PASS', 'SECRET', 'TOKEN', 'APIKEY', 'API_KEY', 'PRIVATE', 'CREDENTIAL'];
const ENV_KEY_PATTERN = /^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=/;

const isTrackedEnvFile = (relativePath) => /^\.env(?:\..*)?$/.test(path.posix.basename(relativePath));

const listTrackedEnvFiles = () => {
  try {
    const output = execFileSync('git', ['-C', repoRootDir, 'ls-files', '-z'], {
      encoding: 'utf8',
      maxBuffer: 16 * 1024 * 1024,
    });
    return output
      .split('\0')
      .map((entry) => entry.trim())
      .filter(Boolean)
      .filter((entry) => isTrackedEnvFile(entry.replaceAll('\\', '/')))
      .map((entry) => path.resolve(repoRootDir, entry));
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);
    console.error(`[verify:no-public-secrets] git ls-files の実行に失敗しました: ${reason}`);
    process.exit(2);
  }
};

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

const files = listTrackedEnvFiles();
const findings = files.flatMap((filePath) => scanFile(filePath));

if (findings.length > 0) {
  console.error('[verify:no-public-secrets] VITE_公開変数に秘密名キーワードを含むキーを検出しました。');
  for (const finding of findings) {
    console.error(` - ${path.relative(repoRootDir, finding.filePath)}:${finding.line} ${finding.key}`);
  }
  process.exit(2);
}

console.log('[verify:no-public-secrets] 問題は検出されませんでした。');
