# cmd_20260218_01_sub_21 並走支援報告（ashigaru6）

- run_id: 20260218T140430Z-cmd_20260218_01_sub_21
- executed_at: 2026-02-18T14:04:24+0900
- target: cmd_20260218_01_sub_8 再検証手順の並走実施

## Preflight
- `http://localhost:5173` 応答: 200
- `node` version: v22.16.0
- `playwright` version: 1.56.1

## 並走結果
- 主要カテゴリ5件のうち `pass=0 / partial=1 / fail=4`。
- `keyword` 再試験 API は 5/5 が 401。
- よって、`sub_8` の「SKIP=0 で受入判定可能」条件は未達。

## 失敗時ログ補完
- 実画面検証: `summary-v2.md`, `summary-v2.json`
- network(401+traceId): `network-401.tsv`
- console: `console-log.json`
- screenshot: `screenshots/*.png`

## traceId（401）
- 8634040f-9ee2-4336-87ff-986bb45c80d5
- 1c8c420b-0e84-4edb-835c-0e11298cffc0
- 7cd79afb-cefa-41f1-b0c0-597e46a49cbf
- 9f595662-f473-43a7-ae78-38b35033a62f
- b5d2cecd-80e2-4dbe-98d3-fb5ac85d5ed4

## 受入チェックシート転記判定
- PASS転記: 実施せず（失敗ゼロ条件を満たさないため）。
- 代替対応: 受入証跡台帳 `cmd_20260218_01_sub_20` に fail 根拠を追記。
