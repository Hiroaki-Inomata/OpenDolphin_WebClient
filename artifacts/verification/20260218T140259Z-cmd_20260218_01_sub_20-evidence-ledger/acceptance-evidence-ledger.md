# cmd_20260218_01_sub_20 受入証跡台帳（更新版）

- generated_at: 2026-02-18T15:05:00+0900
- owner: ashigaru4
- parent_cmd: cmd_20260218_01
- source_of_truth: queue/tasks/*.yaml, queue/reports/*.yaml

| subtask | assignee | task_status | latest_report | artifact_paths | traceId | missing |
|---|---|---|---|---|---|---|
| cmd_20260218_01_sub_6 | ashigaru1 | assigned | none (sub_6未報告) | none | none | report/artifact/traceId |
| cmd_20260218_01_sub_8 | ashigaru5 | assigned | none (sub_8未報告) | none | none | report/artifact/traceId |
| cmd_20260218_01_sub_18 | ashigaru2 | assigned | none (sub_18未報告) | none | none | report/artifact/traceId |
| cmd_20260218_01_sub_19 | ashigaru3 | done | done (2026-02-18T14:03:19+0900) | queue/reports/ashigaru3_report.yaml | none (review task) | artifact/traceId |
| cmd_20260218_01_sub_21 | ashigaru6 | done | done (2026-02-18T14:06:17+0900) | artifacts/verification/20260218T140430Z-cmd_20260218_01_sub_21-parallel-sub8/summary-v2.md ; artifacts/verification/20260218T140430Z-cmd_20260218_01_sub_21-parallel-sub8/network-401.tsv ; artifacts/verification/20260218T140430Z-cmd_20260218_01_sub_21-parallel-sub8/console-log.json | 8634040f-9ee2-4336-87ff-986bb45c80d5 ; 1c8c420b-0e84-4edb-835c-0e11298cffc0 ; 7cd79afb-cefa-41f1-b0c0-597e46a49cbf ; 9f595662-f473-43a7-ae78-38b35033a62f ; b5d2cecd-80e2-4dbe-98d3-fb5ac85d5ed4 | PASS転記条件未達（401混在） |
| cmd_20260218_01_sub_22 | ashigaru7 | superseded | superseded by cmd_20260218_01_sub_25 (latest report: in_progress, 2026-02-18T14:57:04+0900) | artifacts/verification/20260218T055835Z-cmd_20260218_01_sub_25-final-acceptance-verdict/final_verdict.md ; artifacts/verification/20260218T055835Z-cmd_20260218_01_sub_25-final-acceptance-verdict/evidence.tsv | 8634040f-9ee2-4336-87ff-986bb45c80d5 ; 1c8c420b-0e84-4edb-835c-0e11298cffc0 ; 7cd79afb-cefa-41f1-b0c0-597e46a49cbf ; 9f595662-f473-43a7-ae78-38b35033a62f ; b5d2cecd-80e2-4dbe-98d3-fb5ac85d5ed4 | sub_18/sub_23 trace bundle missing, SKIP判定待ち |

## Notes
- sub_22 は redo により `cmd_20260218_01_sub_25` へ移行済み。最終判定ファイルは存在するが判定は `NG (401_mixed + trace_missing)`。
- 現時点の不足項目（sub_6/sub_8/sub_18/sub_19）を家老へ即時通達対象として保持。
