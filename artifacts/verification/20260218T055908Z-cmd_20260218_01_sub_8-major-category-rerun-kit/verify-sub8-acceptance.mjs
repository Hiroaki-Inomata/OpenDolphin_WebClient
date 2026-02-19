import fs from 'node:fs';
import path from 'node:path';

const summaryPath = process.argv[2];
if (!summaryPath) {
  console.error('usage: node verify-sub8-acceptance.mjs <summary.json>');
  process.exit(2);
}

const fullPath = path.resolve(summaryPath);
const summary = JSON.parse(fs.readFileSync(fullPath, 'utf8'));

const skipCount = Number(summary?.skipCount ?? 0);
const categories = Array.isArray(summary?.categories) ? summary.categories : [];
const failed = categories.filter((c) => c?.status !== 'pass');

const verdict = {
  file: fullPath,
  runId: summary?.runId ?? null,
  skipCount,
  total: categories.length,
  pass: categories.length - failed.length,
  fail: failed.length,
  overall: skipCount === 0 && failed.length === 0 ? 'accept' : 'reject',
  reasons: [],
};

if (skipCount !== 0) verdict.reasons.push(`SKIP must be 0 (actual=${skipCount})`);
if (failed.length > 0) verdict.reasons.push(`${failed.length} category checks failed`);

console.log(JSON.stringify(verdict, null, 2));
if (verdict.overall !== 'accept') process.exit(1);
