# cmd_20260218_01_sub_30: sub_28 rerun window procedure

## 1) Congestion diagnosis (measured)
- Multiple concurrent rerun groups were active at the same time (see `process_snapshot.txt`).
- `/orca/master/*` direct calls from localhost timed out at 5s for all 4 endpoints (see `direct_master_connectivity.tsv`).
- Frontend root (`http://localhost:5173/`) remained healthy (`200`) while master endpoints timed out, indicating backend/proxy congestion rather than total frontend outage (`root_health.txt`).

## 2) Window enforcement applied
- Updated script: `artifacts/verification/20260218T055908Z-cmd_20260218_01_sub_8-major-category-rerun-kit/run-major-category-rerun.sh`
- Added single-run lock directory: `.major-category-rerun.lock`
- Behavior:
  - First runner acquires lock and proceeds.
  - Concurrent runner exits immediately with `exit 42` and `busy:` message.

## 3) sub_28 rerun command (same acceptance indicators)
```bash
cd /Users/Hayato/Documents/GitHub/OpenDolphin_WebClient
RUN_ID=20260218T112512Z-cmd_20260218_01_sub_28-rerun-windowed \
  bash artifacts/verification/20260218T055908Z-cmd_20260218_01_sub_8-major-category-rerun-kit/run-major-category-rerun.sh
```

## 4) Pass/fail contract (unchanged)
- `summary.json`: per-category `status`, `responseStatus`, and overall verdict.
- `logs/master-responses.ndjson`: `/orca/master/*` evidence.
- Acceptance checker:
```bash
node artifacts/verification/20260218T055908Z-cmd_20260218_01_sub_8-major-category-rerun-kit/verify-sub8-acceptance.mjs \
  artifacts/verification/20260218T055908Z-cmd_20260218_01_sub_8-major-category-rerun-kit/runs/<RUN_ID>/summary.json
```
- Rule: SKIP=0 required; any skip is fail/incomplete.

## 5) Pre-run gate (operator)
```bash
ps -Ao pid,ppid,etime,command | rg 'major-category-rerun-check.mjs|run-major-category-rerun.sh' -S
```
- If active runners exist, wait for completion.
- Do not start parallel reruns manually.
