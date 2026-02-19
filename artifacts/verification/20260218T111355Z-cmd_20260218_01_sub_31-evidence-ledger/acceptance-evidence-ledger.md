# cmd_20260218_01_sub_31 受入証跡台帳（sub_28/sub_30 焦点版）

- generated_at: 2026-02-18T20:13:55+0900
- owner: ashigaru4
- parent_cmd: cmd_20260218_01
- focus: sub_28(blocked), sub_30(in_progress)
- source_of_truth: queue/tasks/*.yaml, queue/reports/*.yaml, artifacts/verification/*

| subtask | assignee | task_status | latest_report | blocker_or_progress | timeout_evidence_paths (/orca/master) | evidence_paths | missing | next_action |
|---|---|---|---|---|---|---|---|---|
| cmd_20260218_01_sub_28 | ashigaru2 | blocked (2026-02-18T16:14:55+0900) | blocked (queue/reports/ashigaru2_report.yaml, 2026-02-18T16:14:55+0900) | 検証基盤輻輳。rerun系プロセス停滞 + 直接疎通curl(8s) timeout再現。UI遮蔽対策とwait有界化は実施済み。 | queue/reports/ashigaru2_report.yaml ("/orca/master/* direct curl timeout(8s)") ; artifacts/verification/20260218T055908Z-cmd_20260218_01_sub_8-major-category-rerun-kit/runs/20260218T161930Z-cmd_20260218_01_sub_28-major-category-rerun-final3/logs/failure-context.json (TimeoutError + endpointPart=/orca/master/*) | artifacts/verification/20260218T055908Z-cmd_20260218_01_sub_8-major-category-rerun-kit/runs/20260218T161930Z-cmd_20260218_01_sub_28-major-category-rerun-final3/summary.md ; artifacts/verification/20260218T055908Z-cmd_20260218_01_sub_8-major-category-rerun-kit/runs/20260218T161930Z-cmd_20260218_01_sub_28-major-category-rerun-final3/logs/master-responses.ndjson ; artifacts/verification/20260218T064009Z-cmd_20260218_01_sub_28-major-category-rerun-r2/recheck-ui-sub28.mjs | passCount=5 未達, traceId未採取, blocked解消未了 | sub_30の環境整理結果を受けて再走窓を確保し、同一RUNで passCount=5 + master-responses.ndjson >0 を再証明 |
| cmd_20260218_01_sub_30 | ashigaru6 | assigned (2026-02-18T16:16:52+0900) | 直近は sub_29 done (queue/reports/ashigaru6_report.yaml, 2026-02-18T15:34:00+0900) | 進行中。目的は rerun停滞実態整理と /orca/master 直接疎通再計測、sub_28再実行窓の確保。 | artifacts/verification/20260218T153230Z-cmd_20260218_01_sub_29-network-hook-hardening/checklist.md (timeout/遅延時の診断手順定義) ; pending: sub_30実行結果のtimeout証跡未提出 | queue/tasks/ashigaru6.yaml ; queue/reports/ashigaru6_report.yaml ; artifacts/verification/20260218T153230Z-cmd_20260218_01_sub_29-network-hook-hardening/sub29-handoff.md | sub_30の実行報告YAML未提出, rerun窓確保証跡未提出, /orca/master直接疎通再計測ログ未提出 | ashigaru6がsub_30報告で timeout/再試行判定付きログを提出後、sub_28をblocked解除して再走へ接続 |

## Notes
- `timeout_evidence_paths` 列を必須列として固定。現時点で sub_28 は実証あり、sub_30 は定義済みだが実測ログ待ち。
- 最終判定者向けに「証跡あり/未提出」を同一表で識別できるよう、`missing` と `next_action` を更新。
