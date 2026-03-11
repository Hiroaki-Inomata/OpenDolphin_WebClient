# P6-03 ModuleModel bean_json 置換設計

- 実施日: 2026-03-12
- RUN_ID: 20260311T170115Z
- 対象WBS: `P6-03`

## 目的
- `d_module.bean_json` の「Javaクラス構造依存 + 非構造検索」の制約を解消し、検索・更新・監査に耐える保存形式へ移行する。
- P6-04（実装）と P6-08（migration）で迷わないよう、採用方式・非採用方式・移行順序を固定する。

## 現状整理
- `ModuleModel` は `bean_json (jsonb)` のみを保持し、`ModuleJsonConverter` で POJO 復元している。
- 実運用で主に参照される module entity は以下。
  - `medOrder`
  - `generalOrder`
  - `otherOrder`
  - `treatmentOrder`
  - `surgeryOrder`
  - `radiologyOrder`
  - `testOrder`
  - `physiologyOrder`
  - `bacteriaOrder`
  - `injectionOrder`
  - `baseChargeOrder`
  - `instractionChargeOrder`
  - `progressCourse`
- 集計・検索は `moduleInfo.entity` を軸にしており、payload 内部項目での検索は困難。

## 比較検討
| 案 | 概要 | 利点 | 課題 |
|---|---|---|---|
| A. 型別テーブル完全分離 | entityごとに専用テーブルへ分解 | SQL検索性能が高い | 変更量が大きく、移行難易度が高い |
| B. versioned JSON（採用） | 共通テーブルに `module_type` / `schema_version` / 正規化JSON を保持 | 変更コストと柔軟性のバランスが良い | JSON規約と変換ルールの厳格運用が必要 |

## 採用方針（固定）
- **B. versioned JSON** を採用する。
- 新規保存先を `d_module_payload`（新設）に分離し、`d_module` から payload 責務を切り離す。

### 目標スキーマ（P6-04 実装対象）
- `d_module_payload`
  - `module_id` (PK/FK -> `d_module.id`)
  - `module_type` (`moduleInfo.entity` を正規化)
  - `schema_version` (int, 初期値 `1`)
  - `payload_json` (jsonb)
  - `payload_hash` (sha256, 監査比較用)
  - `created_at`, `updated_at`

### module_type 正規化ルール
- `module_type` は `moduleInfo.entity` をそのまま使う（既存運用と整合）。
- `progressCourse` は独立 type として扱う（オーダ系と混在させない）。

### payload_json の規約
- Javaクラス名は保持しない。
- 文字列キーは snake_case ではなく既存業務語彙の lowerCamel を維持。
- 日時は ISO-8601（UTC基準）で統一。
- null 項目は保存しない（デフォルト値は復元側で補完）。

## 移行方式（P6-08 / P6-09 連携）
1. 新テーブル追加（P6-08）。
2. 既存 `bean_json` を one-shot 変換で `d_module_payload` へ展開（P6-09）。
3. 読み書きを `d_module_payload` 優先へ切替（P6-04）。
4. 安定化後に `d_module.bean_json` を削除候補へ移す（別migration）。

## 非機能要件
- 検索性能:
  - `(module_type, updated_at)` 複合 index を必須化。
  - 必要に応じて `payload_json` の GIN index を追加。
- 監査:
  - `payload_hash` で改訂差分比較を高速化。
- 後方互換:
  - 移行期間中のみ `bean_json` 読取 fallback を許容し、書込は新形式のみ。

## P6-04 への実装指示
- 先行実装対象は `medOrder` と `progressCourse` の2系統。
- `ModuleJsonConverter` は「旧JSON復元器」へ縮退し、新保存の責務は `ModulePayloadMapper`（新設）へ移管する。
