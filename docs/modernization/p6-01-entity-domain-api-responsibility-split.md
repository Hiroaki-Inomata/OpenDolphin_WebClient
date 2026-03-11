# P6-01 entity・domain・API の責務分担設計（RUN_ID: 20260311T163550Z）

## 目的
- `entity`（永続化都合）、`domain`（業務ルール）、`api-contract`（公開契約）の責務を明確に分離し、変更時の波及先を予測可能にする。

## 層別責務
| 層 | 主責務 | 保持してよい情報 | 禁止事項 |
|---|---|---|---|
| persistence(entity) | DB 永続化・検索最適化 | 主キー/外部キー、監査列、検索用補助列、正規化済み列 | HTTP入出力契約、画面表示都合の派生値、業務判定ロジック |
| domain | 業務ルール・整合性判定 | 業務上の不変条件、状態遷移、集約内整合 | JPAアノテーション依存、HTTPヘッダ/URI依存 |
| api-contract | 外部公開契約 | リクエスト/レスポンスDTO、エラー契約、互換期間のフィールド | Entity直参照、DB列名依存、トランザクション制御 |

## 主要4領域の分担
| 対象 | persistence(entity) | domain | api-contract |
|---|---|---|---|
| Patient | `d_patient` 系の実体、索引最適化列 | 患者同一性判定、更新衝突方針、必須項目規則 | 患者検索/更新DTO、公開時の null/省略規約 |
| User | 認証/権限テーブル実体、監査列 | ロール評価、管理者判定、パスワード更新規則 | ユーザー管理API DTO、権限不足時エラー契約 |
| Document | カルテ文書・版管理の永続化モデル | 文書改訂ルール、署名整合、参照可否判定 | 文書一覧/詳細/改訂の入出力DTO |
| Module | ORCA/オーダ由来 module の保存形 | module種別ごとの整合性・変換規則 | module公開DTO（クライアント送受信用） |

## 依存ルール
- `api` -> `domain` -> `persistence` の単方向依存を維持する。
- `api-contract` は `domain`/`persistence` のどちらにも依存しない。
- mapper は server 層（application service）に置き、`entity <-> domain` と `domain <-> dto` を分離する。

## 実装ガイド（後続タスクへの接続）
1. `P6-02`: 日時型移行は domain を正本にし、entity/api は変換で追従。
2. `P6-03`: `ModuleModel` の置換設計は domain 集約を先に確定し、entity 形式は後追い。
3. `P6-05`: repository/query 層は domain モデル返却を基本とし、entity 直返しを禁止。

## 完了条件との対応
- 三者の役割を表形式で固定し、Patient/User/Document/Module の4対象で責務を定義。
- 禁止事項と依存方向を明文化し、後続実装タスクでの判断基準を提供。
