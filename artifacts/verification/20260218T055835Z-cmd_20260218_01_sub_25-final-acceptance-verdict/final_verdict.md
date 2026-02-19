# cmd_20260218_01_sub_25 Final Acceptance Verdict

- generated_at_utc: 2026-02-18T05:58:35Z
- parent_cmd: cmd_20260218_01
- fixed_rule: Exclude 'TENSU_NOT_FOUND' 404 from auth regression judgement.

## Gate Evaluation
1. 401 mixed: **FAIL**
2. trace bundle complete (sub_18 + sub_21 + sub_23): **FAIL**
3. SKIP = 0: **PENDING** (sub_18 summary unavailable)

## Final Judgement
NG (401_mixed + trace_missing)

## TraceId Evidence (401)
- 8634040f-9ee2-4336-87ff-986bb45c80d5
- 1c8c420b-0e84-4edb-835c-0e11298cffc0
- 7cd79afb-cefa-41f1-b0c0-597e46a49cbf
- 9f595662-f473-43a7-ae78-38b35033a62f
- b5d2cecd-80e2-4dbe-98d3-fb5ac85d5ed4

## Missing Items (must be provided to re-open PASS judgement)
- cmd_20260218_01_sub_18 artifact bundle (summary.json, summary.md, master-responses.ndjson)
- cmd_20260218_01_sub_23 artifact bundle (task/report/evidence not found in primary YAML and verification tree)

## Notes
- Even with 404 exclusion rule applied, 401混在 remains immediate NG.
- This verdict supersedes sub_22 draft guard and finalizes current state under sub_25.
