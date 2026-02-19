# cmd_20260218_01_sub_29 /orca/master network capture hardening checklist

## Goal
- Ensure `/orca/master/*` responses are captured per category with no blind spots during Playwright reruns.
- Provide a repeatable checklist that ashigaru2 can execute immediately.

## Target script
- `artifacts/verification/20260218T055908Z-cmd_20260218_01_sub_8-major-category-rerun-kit/major-category-rerun-check.mjs`

## Capture hardening points (must all be true)
1. Register `waitForRequest` before keyword input.
2. Link request to response via `await request.response()`.
3. Add fallback lookup from `page.on('response')` timeline (`atMs >= actionStartedAt`).
4. Normalize search trigger by `searchInput.fill('')` then `searchInput.fill(keyword)`.
5. Persist `logs/master-responses.ndjson` for postmortem.

## Preflight
- `curl -sS -o /dev/null -w '%{http_code}' http://localhost:5173` returns `200`.
- `node -v` and `npm -v` succeed.
- Playwright browser dependencies are installed.

## Execution command
```bash
cd /Users/Hayato/Documents/GitHub/OpenDolphin_WebClient
RUN_ID=20260218T153230Z-cmd_20260218_01_sub_29-hook-check \
  bash artifacts/verification/20260218T055908Z-cmd_20260218_01_sub_8-major-category-rerun-kit/run-major-category-rerun.sh
```

## Verification commands
```bash
# 1) Confirm summary has no missing HTTP values unless true UI failure
jq '.categories[] | {category,responseStatus,traceId,status}' \
  artifacts/verification/20260218T055908Z-cmd_20260218_01_sub_8-major-category-rerun-kit/runs/20260218T153230Z-cmd_20260218_01_sub_29-hook-check/summary.json

# 2) Confirm network log captured /orca/master calls
rg -n '"url":.*"/orca/master/' \
  artifacts/verification/20260218T055908Z-cmd_20260218_01_sub_8-major-category-rerun-kit/runs/20260218T153230Z-cmd_20260218_01_sub_29-hook-check/logs/master-responses.ndjson
```

## Fail handling
- If `responseStatus` is null and `master-responses.ndjson` has endpoint records: classify as UI path issue (candidate/reflection).
- If both are absent: classify as trigger issue (input/event not fired) and rerun once.
- If rerun still absent: attach console + screenshot and escalate as blocked.
