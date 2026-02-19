# cmd_20260218_01_sub_11 rerun readiness checklist

## Scope
- Goal: one-shot verdict for the 5 major categories after fix application.
- Focus: shared execution command and shared verification template (non-overlap with ashigaru5 sub_8).

## Fixed execution order (must not change)
1. prescription (`+処方`, `/orca/master/generic-class`)
2. injection (`+注射`, `/orca/master/generic-class`)
3. test (`+検査`, `/orca/master/kensa-sort`)
4. procedure (`+処置`, `/orca/master/material`)
5. charge (`+算定`, `/orca/master/etensu`)

## Command template (shared)
```bash
cd /Users/Hayato/Documents/GitHub/OpenDolphin_WebClient
bash artifacts/verification/20260218T010341Z-cmd_20260218_01_sub_11-rerun-kit/run-major-category-rerun.sh
```

## Preflight (required)
- App is reachable: `curl -sS -o /dev/null -w '%{http_code}' http://localhost:5173` returns `200`.
- Playwright runtime is available: `node -v` and `npm -v` succeed.
- Session seed credentials are valid (defaults embedded in script, override by env if needed).

## Pass/Fail rule
- Per-category pass condition: `HTTP=200` and `candidateShown=yes` and `selected=yes` and `reflected=yes`.
- Overall pass condition: all 5 categories are `pass`.
- SKIP rule: `SKIP=0` is mandatory. Any skip is treated as incomplete/fail.

## Fixed failure logs (always collected)
- `logs/master-responses.ndjson` (captured `/orca/master/*` responses)
- `logs/console.log` (browser console)
- `logs/failure-context.json` (only failed categories)
- `screenshots/NN-<category>.png` or `screenshots/NN-<category>-error.png`

## Output contract
- `summary.json`: machine-readable verdict and per-category detail.
- `summary.md`: report-ready table for Karo/dashboard handoff.
