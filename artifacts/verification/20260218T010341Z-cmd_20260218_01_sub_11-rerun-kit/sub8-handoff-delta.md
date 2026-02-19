# cmd_20260218_01_sub_15 sub_8 integration delta

## Purpose
- Bridge `sub_11` rerun-kit and ashigaru5 `sub_8` acceptance rerun without changing fixed scope/order.

## Delta 1: Preflight startup
- Start with `WEB_CLIENT_MODE=npm ./setup-modernized-env.sh` and confirm chart page is reachable before rerun.
- Verify app health first: `curl -sS -o /dev/null -w '%{http_code}' http://localhost:5173` must return `200`.
- Use dedicated run id to avoid mix logs:
  - `RUN_ID=$(date -u +%Y%m%dT%H%M%SZ)-sub8-final`
  - `ARTIFACT_DIR=artifacts/verification/20260218T010341Z-cmd_20260218_01_sub_11-rerun-kit/runs/$RUN_ID`

## Delta 2: Log capture handoff
- Keep `master-responses.ndjson`, `console.log`, `failure-context.json`, `summary.json`, `summary.md` as mandatory artifacts.
- For `sub_8` report, attach:
  - one successful category row from `summary.md`
  - one `/orca/master/*` line with `status: 200` from `master-responses.ndjson`
  - `overall`, `passCount`, `failCount`, `skipCount` fields from `summary.json`

## Delta 3: Judgment criteria handoff
- Accept only when all are true:
  - `overall == "pass"`
  - `passCount == 5` and `failCount == 0`
  - `skipCount == 0`

## SKIP=0 final three-point check
1. `summary.json` has `skipCount: 0` and `skipRule` unchanged.
2. `summary.md` includes `SKIP policy: SKIP must be 0. Any SKIP = FAIL (incomplete).`
3. Execution output has five category rows and none are omitted.
