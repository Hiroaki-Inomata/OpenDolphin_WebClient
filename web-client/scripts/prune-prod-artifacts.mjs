import fs from 'node:fs';
import path from 'node:path';

const distDir = process.env.DIST_DIR ? path.resolve(process.env.DIST_DIR) : path.resolve(process.cwd(), 'dist');
const targets = ['mockServiceWorker.js'];

if (!fs.existsSync(distDir) || !fs.statSync(distDir).isDirectory()) {
  console.error(`[prune-prod-artifacts] dist dir missing: ${distDir}`);
  process.exit(2);
}

const removed = [];
const skipped = [];

for (const target of targets) {
  const fullPath = path.join(distDir, target);
  if (!fs.existsSync(fullPath)) {
    skipped.push(target);
    continue;
  }
  fs.rmSync(fullPath, { force: true });
  removed.push(target);
}

if (removed.length > 0) {
  console.info(`[prune-prod-artifacts] removed: ${removed.join(', ')}`);
}
if (skipped.length > 0) {
  console.info(`[prune-prod-artifacts] not found: ${skipped.join(', ')}`);
}

process.exit(0);
