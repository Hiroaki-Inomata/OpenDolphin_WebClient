# cmd_20260218_01_sub_8 handoff

## Deliverables
- rerun script: `run-major-category-rerun.sh`
- automation body: `major-category-rerun-check.mjs`
- acceptance guard (SKIP=0 + all pass): `verify-sub8-acceptance.mjs`
- fixed-order checklist: `checklist.md`

## Fixed execution order (locked)
1. prescription (`/orca/master/generic-class`)
2. injection (`/orca/master/generic-class`)
3. test (`/orca/master/kensa-sort`)
4. procedure (`/orca/master/material`)
5. charge (`/orca/master/etensu`)

## Re-run command
```bash
cd /Users/Hayato/Documents/GitHub/OpenDolphin_WebClient
bash artifacts/verification/20260218T055908Z-cmd_20260218_01_sub_8-major-category-rerun-kit/run-major-category-rerun.sh
```

## Acceptance command (SKIP=0 mandatory)
```bash
node artifacts/verification/20260218T055908Z-cmd_20260218_01_sub_8-major-category-rerun-kit/verify-sub8-acceptance.mjs \
  artifacts/verification/20260218T055908Z-cmd_20260218_01_sub_8-major-category-rerun-kit/runs/<RUN_ID>/summary.json
```

## Latest execution evidence
- run: `20260218T060734Z-major-category-rerun`
- summary: `runs/20260218T060734Z-major-category-rerun/summary.md`
- machine result: `runs/20260218T060734Z-major-category-rerun/summary.json`
- failure context: `runs/20260218T060734Z-major-category-rerun/logs/failure-context.json`
- acceptance verdict: `reject` (`skipCount=0`, `fail=5`)

## Failure log contract (always collected)
- `logs/master-responses.ndjson`
- `logs/console.log`
- `logs/failure-context.json`
- `screenshots/NN-<category>.png` or `screenshots/NN-<category>-error.png`
