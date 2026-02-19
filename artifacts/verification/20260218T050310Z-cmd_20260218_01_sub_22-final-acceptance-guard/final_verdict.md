# cmd_20260218_01_sub_22 Final Acceptance Guard

## Scope
Build a final acceptance statement that separates auth-401 recurrence from etensu not-found 404.

## Fixed rule (applied)
- 404 from `/orca/master/etensu` is excluded from auth failure only when:
  - response code is `TENSU_NOT_FOUND`
  - endpoint health check (`/orca/master/etensu?page=1&size=1`) is 200
- Any 401 mixed into final run remains immediate NG.
- Missing trace bundle remains NG.
- SKIP > 0 remains NG.

## Final judgement sentence template
`Final judgement for cmd_20260218_01 = PASS only if (401 mixed = false) AND (trace bundle complete = true) AND (SKIP = 0), while etensu 404 with code=TENSU_NOT_FOUND is excluded from failure.`

## Current judgement (as-of 2026-02-18T05:03:10Z)
`NG (trace_missing)` because `cmd_20260218_01_sub_18` run artifacts are not present yet, so 401/SKIP gates cannot be closed.

## Operator note
When sub_18 artifacts arrive, re-evaluate only three gates in order:
1. `401 mixed`
2. `trace bundle complete`
3. `SKIP = 0`
Do not treat `TENSU_NOT_FOUND` 404 as auth regression when the fixed rule is satisfied.
