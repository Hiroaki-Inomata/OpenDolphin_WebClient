# D担当メモ: DB/Flyway 整備

- RUN_ID: `20260221T032745Z`
- 対象: `server-modernized` DB/Flyway

## 実施内容
- Flyway 正本を `server-modernized/tools/flyway/sql` に一本化し、`src/main/resources/db/migration` をミラーとして同期。
- `V0240` 重複を解消。
  - `V0240__fix_opendolphin_fk_and_freedocument_seq.sql` は維持
  - 患者複合キー migration を `V0244__patient_facility_patientid_unique.sql` として追加
  - `src/main/resources/db/migration/V0240__patient_facility_patientid_unique.sql` は削除
- recommendation index (`V0242__order_recommendation_indexes.sql`) を正本ディレクトリにも追加。
- `V0245__validate_not_valid_foreign_keys.sql` を追加し、孤児参照をクリーンアップしてから `NOT VALID` FK を validate する follow-up を実装。
- `ChartEventHistoryRepositoryImpl#purge` を施設スコープで削除するよう修正（全施設横断削除を防止）。
- `ChartEventHistoryRepositoryImplTest` を追加し、facility スコープ削除を検証。

## 運用ドキュメント更新
- `server-modernized/tools/flyway/README.md`
  - 正本/ミラー方針
  - 同期確認コマンド
  - 患者重複棚卸し SQL と対応手順
  - `NOT VALID` FK 棚卸し SQL

## 補足
- 既存 migration の checksum 破壊を避けるため、既存 version 番号の SQL は原則保持し、新規 version で追加対応した。

## 検証結果
- 2系統同期チェック: `name_diff/content_diff/version_dup` はすべて `<none>`。
- Flyway（正本 `tools/flyway/sql`）:
  - `migrate -outOfOrder=true` 実行で `V0245` 適用成功。
  - `validate` 成功。
  - `NOT VALID` FK 件数は `0` を確認。
  - `V0245` 実行時に削除された孤児参照:
    - `d_appo`: 1
    - `d_diagnosis`: 1
    - `d_letter_module`: 1
    - `d_patient_visit`: 2
    - `d_roles`: 3
- テスト:
  - `mvn -q -pl server-modernized test` PASS。
  - `mvn -q test` は既存依存解決エラー（`opendolphin:itext-font:1.0`, `com.apple:AppleJavaExtensions:1.6`）で FAIL。
