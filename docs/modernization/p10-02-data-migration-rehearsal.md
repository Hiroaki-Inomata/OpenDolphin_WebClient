# P10-02 データ移行の通し試験

- 日付: 2026-03-12
- RUN_ID: 20260312T090057Z
- タスク: P10-02

## 目的
- 初期化、移行、件数照合、簡易業務確認、再実行確認を同一手順で通し、`P6-09` の one-shot 移行が再現可能であることを確認する。

## 実施環境
- 既存の `opendolphin-postgres-modernized-validation` は別 worktree 所有だったため、運用ルールに従って非操作。
- 本 RUN 専用で DB コンテナを作成:
  - `opendolphin-postgres-modernized-e730-20260312t090057z`
  - Port: `55452`
  - DB: `opendolphin_modern`
  - User: `opendolphin_validation`
- ホストに `psql` が無いため、`postgres:14` クライアントコンテナ経由で SQL/移行スクリプトを実行。

## 実施内容
1. 検証 DB を初期化し、以下を順に適用。
   - `V0300__baseline_fresh_schema.sql`
   - `V0301__orca_patient_sync_state_store.sql`
   - `V0302__module_payload_table.sql`
   - `V0303__performance_index_tuning.sql`
   - `V0304__runtime_state_store.sql`
   - `P1_03__minimal_baseline_seed.sql`
2. `medOrder` / `progressCourse` の envelope 形式データ（2件）を `d_module` に投入。
3. `run-module-payload-migration.sh` を `RUN_ID-r1` で実行。
4. 同一手順を `RUN_ID-r2` で再実行（リラン確認）。
5. 件数照合と簡易業務確認を実施。

## 結果（件数照合）
- 事前:
  - `envelope_modules=2`
  - `payload_rows_before=0`
- 1回目実行後:
  - `after_payload_rows=2`
  - `after_missing_rows=0`
  - `missing_payload_rows=0`
- 2回目実行後（再実行）:
  - `before_payload_rows=2`
  - `after_payload_rows=2`
  - `after_missing_rows=0`
  - `missing_payload_rows=0`
- 実行履歴:
  - `d_module_payload_migration_run` に `RUN_ID-r1` / `RUN_ID-r2` が `status=completed` で記録された。

## 簡易業務確認
- fixture 由来の業務基礎データ件数を確認:
  - `patients=3`
  - `documents=4`
  - `modules=6`
- 移行後も fixture の患者・カルテ関連データが欠損していないことを確認。

## 差異と修正
- 差異:
  - 旧 `module-payload-migrate-once.sql` は同一 statement 内で `after_payload_rows`/`after_missing_rows` を算出しており、`r1` の記録値が実テーブル件数と不一致になる場合があった。
- 修正:
  - `server-modernized/tools/flyway/scripts/module-payload-migrate-once.sql` を修正し、upsert 後に別 `UPDATE` で `after_*` を再計算する方式へ変更。
- 再検証:
  - 修正後の `r1` / `r2` で `d_module_payload_migration_run` と `module-payload-verify.sql` の件数が一致することを確認。

## 再実行手順（停止復旧含む）
1. DB 接続情報を設定して one-shot スクリプトを実行する。
2. 中断時は同じ `RUN_ID` で再実行して上書き更新、もしくは新しい `RUN_ID` で再実行して履歴を分離する。
3. `module-payload-verify.sql` で `missing_payload_rows=0` を必ず確認する。
4. `d_module_payload_migration_run` の `status=completed` と `after_missing_rows=0` を確認する。

