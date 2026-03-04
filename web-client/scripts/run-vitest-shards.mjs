import { spawnSync } from 'node:child_process';

const DEFAULT_SHARDS = 8;
const rawShards = process.env.VITEST_SHARDS ?? String(DEFAULT_SHARDS);
const shardCount = Number.parseInt(rawShards, 10);

if (!Number.isInteger(shardCount) || shardCount <= 0) {
  console.error(`[test:ci:shards] invalid VITEST_SHARDS: "${rawShards}"`);
  process.exit(1);
}

const npmCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm';
let failed = false;

console.log(`[test:ci:shards] running ${shardCount} shard(s)`);

for (let index = 1; index <= shardCount; index += 1) {
  const shard = `${index}/${shardCount}`;
  console.log(`[test:ci:shards] shard ${shard} start`);

  const result = spawnSync(npmCommand, ['run', 'test:ci', '--', `--shard=${shard}`], {
    stdio: 'inherit',
    env: process.env,
  });

  const exitCode = typeof result.status === 'number' ? result.status : 1;
  if (exitCode !== 0) {
    failed = true;
    console.error(`[test:ci:shards] shard ${shard} failed (exit ${exitCode})`);
  } else {
    console.log(`[test:ci:shards] shard ${shard} passed`);
  }
}

if (failed) {
  console.error('[test:ci:shards] completed with failures');
  process.exit(1);
}

console.log('[test:ci:shards] all shards passed');
