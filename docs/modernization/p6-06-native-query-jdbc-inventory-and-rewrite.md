# P6-06 native query / raw JDBC 棚卸しと書換え（第一段）

- 実施日: 2026-03-12
- RUN_ID: 20260311T210122Z
- 対象WBS: `P6-06`

## 目的
- `createNativeQuery` と raw JDBC の残存箇所を用途別に棚卸しし、
  「残す SQL」と「query service へ寄せる SQL」を明確化する。
- 直書きSQLが散在していた ORCAユーザー連携操作を query service へ集約する。

## 棚卸し結果（server-modernized/src/main/java）

### `createNativeQuery` 利用（件数上位）
- 8件: `open/dolphin/rest/ChartEventHistoryRepositoryImpl.java`（履歴テーブル専用の write/read）
- 6件: `open/dolphin/security/auth/LoginAttemptPolicyService.java`（認証失敗/ロックアウト管理）
- 4件: `open/dolphin/rest/AdminAccessResource.java`（管理者ユーザー補助テーブル）
- 2件: `open/dolphin/session/UserServiceBean.java`（ユーザーID採番 + 直接INSERT）
- 2件: `open/dolphin/session/SystemServiceBean.java`（施設採番 + DBサイズ取得）
- 2件: `open/dolphin/rest/orca/PrescriptionOrderRepository.java`（ORCA注文検索）
- 1件: `open/dolphin/rest/orca/OrcaOrderBundleResource.java`（module payload参照）

### raw JDBC 利用（ORCA境界）
- `open/orca/rest/OrcaResource.java`
- `open/orca/rest/OrcaMasterDao.java`
- `open/orca/rest/EtensuDao.java`
- `open/orca/rest/ORCAConnection.java`

## 用途分類と扱い
- ORCA外部DB境界（`open/orca/rest/**`）:
  - 当面は JDBC 維持（ORCAスキーマ依存が強く、JPA置換コストが高いため）。
  - ただし SQL 入口は DAO / gateway / query service に集約し、resource 直書きを減らす。
- 管理設定・ユーザー連携補助（`AdminAccessResource` / `AdminOrcaUserResource`）:
  - query service へ移行対象。
- 認証ロックアウト（`LoginAttemptPolicyService`）:
  - 専用 query service 化候補（次段）。
- 履歴系（`ChartEventHistoryRepositoryImpl`）:
  - すでに repository 実装へ寄っているため維持。

## 今回の書換え
- 追加: `server-modernized/src/main/java/open/dolphin/persistence/query/OrcaUserLinkQueryService.java`
  - ORCAユーザー連携テーブル（`d_orca_user_link`）向け native SQL を一元化。
  - テーブル存在確認、owner検索、upsert、delete、施設単位リンク読取を集約。
- 変更: `server-modernized/src/main/java/open/dolphin/rest/AdminAccessResource.java`
  - ORCAリンク関連 native SQL を `OrcaUserLinkQueryService` 呼び出しへ置換。
- 変更: `server-modernized/src/main/java/open/dolphin/rest/AdminOrcaUserResource.java`
  - リンク作成/削除/参照の native SQL を `OrcaUserLinkQueryService` 呼び出しへ置換。

## 置換後の判断基準（P6-06時点）
- 残すSQL:
  - ORCA外部DB境界で JPA 化に不向きな複雑SQL（`OrcaMasterDao` / `EtensuDao`）。
- 置換を進めるSQL:
  - Resource 層に散在する補助テーブル操作の native SQL。
  - 共通利用される native SQL（同一テーブル/同一条件式）を query service へ集約。

## 次段の優先候補
1. `LoginAttemptPolicyService` の native SQL を `security` 配下 query service へ分離。
2. `UserServiceBean` のユーザー直接INSERT経路を repository 化して SQL を閉じ込める。
3. `SystemServiceBean` の採番/DBサイズ取得を system query service へ移管。
