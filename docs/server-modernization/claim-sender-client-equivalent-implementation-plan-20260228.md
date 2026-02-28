# claim.sender=client 相当運用への移行実施計画

- RUN_ID: 20260228T131828Z
- 作成日: 2026-02-28
- 対象: `server-modernized` / `web-client`
- 目的: モダナイズ版サーバーを「単一サーバーで複数施設を扱い、施設ごとに接続先 ORCA を切替可能」な設計へ移行する。

## 1. 現状整理

- 施設識別は既にリクエスト単位で実施されている。
- ORCA 接続設定は単一レコード運用であり、施設別設定に未対応。
- ORCA 呼び出し Transport キャッシュも単一であり、施設別切替に未対応。
- 管理API（`/api/admin/orca/connection`）はグローバル設定前提。

## 2. 到達目標（Definition of Done）

1. サーバーは施設IDコンテキストに応じて ORCA 接続設定を解決できる。
2. ORCA 接続設定ストアは施設別レコードを永続化でき、既存単一形式から後方互換で読込できる。
3. ORCA 呼び出し時に施設別設定が選択される。
4. 管理APIはログイン施設コンテキストで接続設定を読み書きできる。
5. `web-client` は既存 UI/導線を維持したまま施設別設定を利用できる。
6. 主要テスト（少なくとも `web-client` の該当ユニットテスト + サーバーコンパイル）が通る。
7. 変更差分を最終監査し、仕様との対応関係を説明できる。

## 3. 実装方針

### 3.1 サーバー

- `OrcaConnectionConfigStore` を施設スコープ化する。
  - 施設キーごとの `OrcaConnectionConfigRecord` を保持。
  - 保存フォーマットに `records` マップを導入。
  - 旧単一レコードJSONは読込時にデフォルトキーへ移行して互換維持。
- `RestOrcaTransport` を施設別キャッシュ化する。
  - 施設IDは request コンテキスト（trace attribute / MDC remoteUser）から解決。
  - 施設不明時はデフォルト設定へフォールバック。
- `AdminOrcaConnectionResource` は actor の施設IDを解決して対象レコードを読書きする。
  - レスポンスへ `facilityId` を明示して運用可観測性を上げる。

### 3.2 Webクライアント

- 接続設定APIの型に `facilityId` を追加し、表示・テストの整合を取る。
- 現行導線（管理画面での接続確認・保存・テスト）は維持し、破壊的変更を避ける。

### 3.3 ドキュメント

- `docs/DEVELOPMENT_STATUS.md` に RUN_ID 付きで実施内容・検証結果を追記する。

## 4. 役割分担（サブエージェント）

- Worker-A（サーバー実装責任）
  - 所有ファイル: `server-modernized/src/main/java/open/dolphin/orca/config/*`, `.../transport/RestOrcaTransport.java`, `.../rest/AdminOrcaConnectionResource.java`
- Worker-B（クライアント/テスト責任）
  - 所有ファイル: `web-client/src/features/administration/orcaConnectionApi.ts`, `.../orcaConnectionApi.test.ts`, 必要なら `AdministrationPage.tsx`
- Reviewer（監査責任）
  - 変更差分の仕様適合性、後方互換、リスク、未検証点を監査

## 5. リスクと対策

- リスク: 旧JSON保存形式の読込失敗。
  - 対策: 旧形式検知ロジックを実装し、フォールバックを残す。
- リスク: 施設ID不明時に意図しない設定を使用。
  - 対策: 施設解決順を固定し、監査ログに `facilityId` を残す。
- リスク: AppScoped Transport のキャッシュ不整合。
  - 対策: 施設単位 reload API を用意し、更新時に対象施設キャッシュを再読込。

## 6. 検証計画

1. `web-client` の接続設定APIテストを実行。
2. `server-modernized` をコンパイルし、主要クラスの破壊がないことを確認。
3. 差分レビューで「施設別解決の経路」と「後方互換」の実装有無を再確認。

