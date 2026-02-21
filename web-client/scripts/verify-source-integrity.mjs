import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(scriptDir, '..');

const targets = [
  { relPath: 'src/features/reception/pages', optional: false },
  { relPath: 'src/features/reception/components', optional: false },
  { relPath: 'src/features/charts/pages', optional: false },
  { relPath: 'src/features/charts/print', optional: false },
  { relPath: 'src/features/administration/components', optional: true },
  { relPath: 'src/features/images/pages', optional: false },
];

const ignoredPlaceholderNames = new Set(['.gitkeep', '.keep', '.DS_Store']);

const hasMeaningfulEntry = (dirPath) => {
  const entries = fs.readdirSync(dirPath, { withFileTypes: true });
  for (const entry of entries) {
    if (ignoredPlaceholderNames.has(entry.name)) {
      continue;
    }

    const fullPath = path.join(dirPath, entry.name);
    if (entry.isDirectory()) {
      if (hasMeaningfulEntry(fullPath)) {
        return true;
      }
      continue;
    }

    return true;
  }

  return false;
};

const failures = [];
const successes = [];
const skipped = [];

for (const target of targets) {
  const absolutePath = path.resolve(projectRoot, target.relPath);

  if (!fs.existsSync(absolutePath)) {
    if (target.optional) {
      skipped.push(`${target.relPath} (optional directory is absent)`);
      continue;
    }
    failures.push(`${target.relPath} (missing)`);
    continue;
  }

  if (!fs.statSync(absolutePath).isDirectory()) {
    failures.push(`${target.relPath} (exists but is not a directory)`);
    continue;
  }

  if (!hasMeaningfulEntry(absolutePath)) {
    failures.push(`${target.relPath} (empty)`);
    continue;
  }

  successes.push(target.relPath);
}

for (const dir of successes) {
  console.log(`[verify-source-integrity] OK: ${dir}`);
}

for (const dir of skipped) {
  console.log(`[verify-source-integrity] SKIP: ${dir}`);
}

if (failures.length > 0) {
  for (const message of failures) {
    console.error(`[verify-source-integrity] FAIL: ${message}`);
  }
  process.exit(1);
}

console.log('[verify-source-integrity] All required source directories contain files.');
