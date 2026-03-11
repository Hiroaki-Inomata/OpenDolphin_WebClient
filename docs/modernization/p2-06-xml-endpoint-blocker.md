# P2-06 XML専用エンドポイント削除 ブロッカー整理（RUN_ID: 20260311T050653Z）

## 判定
- `P2-06` は **現時点でブロッカー**。
- 理由: `web-client` 側の本番利用機能が XML 専用 ORCA 入口を直接呼び出しており、サーバ側で先に削除すると業務機能が停止する。

## 試行内容
1. サーバ側の XML 専用入口（`@Consumes(application/xml)` / `@Produces(application/xml)` / `api01rv2` 系）を棚卸し。
2. `web-client/src` で XML リクエスト（`Content-Type: application/xml` / `Accept: application/xml`）と旧 ORCA path 利用箇所を棚卸し。
3. 既存 JSON 代替の有無を確認（`/api/v1` と `/orca/**` の JSON wrapper）。

## 主要依存（抜粋）
- 患者/メモ: `/orca/patientgetv2`, `/orca/patientlst7v2`
- カルテ関連 ORCA: `/orca/diseasegetv2`, `/orca/medicalgetv2`, `/orca/tmedicalgetv2`, `/orca/medicationgetv2`, `/orca/contraindicationcheckv2`
- 管理/受付: `/orca/acceptlstv2`, `/orca/system01lstv2`, `/api/orca101/manageusersv2`, `/orca/insprogetv2`
- レポート: `/orca/prescriptionv2`, `/orca/medicinenotebookv2`, `/orca/karteno1v2`, `/orca/karteno3v2`, `/orca/invoicereceiptv2`, `/orca/statementv2`
- 入力補助: `/api01rv2/subjectiveslstv2`（`web-client` 側コメントでも旧経路フォールバックを明記）

## なぜ自力解決不能か
- 依存は複数機能（患者/受付/カルテ/管理/帳票）に跨り、単純な server 側削除ではなく、
  - `web-client` の API 呼び出し変更
  - 代替 JSON 契約の確定
  - 代替実装の統合テスト
  が同時に必要。
- 1タスク（`P2-06`）としての削除だけを先行すると、`P1` で固定した業務フローの回帰を起こす。

## 解除条件（次アクション）
1. `P2-10` で API map を先行確定し、XML入口ごとの JSON 代替 path を固定。
2. `web-client` の XML 利用箇所を機能単位で JSON へ置換（患者/カルテ/管理/帳票）。
3. 置換完了後に `P2-06` を再開し、XML 専用 resource と `api01rv2` alias 群を削除。
