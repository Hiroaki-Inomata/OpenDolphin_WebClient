# P6-09 既存データ移行スクリプト（one-shot）

- 実施日: 2026-03-12
- RUN_ID: 20260311T210122Z
- 対象WBS: `P6-09`

## 目的
- `P6-08` で追加した `d_module_payload` へ既存データを一度きりで移行する。
- 移行途中で失敗しても、どこまで進んだかを `run_id` 単位で追跡できる状態にする。

## 追加ファイル
- `server-modernized/tools/flyway/scripts/run-module-payload-migration.sh`
- `server-modernized/tools/flyway/scripts/module-payload-migrate-once.sql`
- `server-modernized/tools/flyway/scripts/module-payload-verify.sql`

## 仕様
- 対象データ:
  - `d_module.entity in ('medOrder', 'progressCourse')`
  - かつ `bean_json` が versioned envelope (`schemaVersion` / `moduleType` / `payloadJson`) を持つ行。
- 実行ログ:
  - `opendolphin.d_module_payload_migration_run` に `run_id`・開始/終了時刻・件数を保存。
  - status は `running` / `completed`。
  - 失敗時は `running` 状態が残るため、途中状態の確認が可能。
- 再実行:
  - `ON CONFLICT (module_id) DO UPDATE` で冪等。
  - 同じ `RUN_ID` でも再実行可能（ログ行は上書き）。

## 実行例
```bash
DB_HOST=127.0.0.1 \
DB_NAME=opendolphin \
DB_USER=postgres \
DB_PASSWORD=secret \
DB_SSLMODE=disable \
RUN_ID=20260311T210122Z \
server-modernized/tools/flyway/scripts/run-module-payload-migration.sh
```

## 件数照合
- `module-payload-verify.sql` が以下を出力:
  - `total_modules`
  - `target_modules`
  - `envelope_modules`
  - `payload_rows`
  - `missing_payload_rows`
- 期待値:
  - `missing_payload_rows = 0`

## 検証
- PASS: `bash -n server-modernized/tools/flyway/scripts/run-module-payload-migration.sh`

## 次段（P10-02 へ引き継ぎ）
- 検証DBで one-shot 実行後、`d_module` と `d_module_payload` の件数差異を移行試験記録へ添付する。
