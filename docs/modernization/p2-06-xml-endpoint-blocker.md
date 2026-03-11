# P2-06 XML専用エンドポイント削除 ブロッカー整理（RUN_ID: 20260311T053558Z）

## 判定
- `P2-06` の先行ブロッカーは **解消済み**。
- 理由: `web-client` の ORCA XML POST 呼び出しを `httpFetch` で `/api/v1/orca/bridge` へ集約転送する実装を追加し、旧 XML 入口への直接依存を外した。

## 実施内容（ブロッカー解除）
1. `server-modernized` に `POST /api/v1/orca/bridge` を追加（`OrcaBridgeResource`）。
2. `web-client/src/libs/http/httpClient.ts` で、同一オリジン・XML POST・ORCA系 path を自動判定し、JSON ブリッジ呼び出しへ変換。
3. `patientOriginalApi` の既定フォーマットを JSON 化し、`patientgetv2` は既定で JSON 契約を利用。
4. `httpClient.test.ts` に回帰テストを追加し、XML POST はブリッジ、非XML POST は直送を固定。

## 主要依存（抜粋）
- 患者/メモ: `/orca/patientgetv2`, `/orca/patientlst7v2`
- カルテ関連 ORCA: `/orca/diseasegetv2`, `/orca/medicalgetv2`, `/orca/tmedicalgetv2`, `/orca/medicationgetv2`, `/orca/contraindicationcheckv2`
- 管理/受付: `/orca/acceptlstv2`, `/orca/system01lstv2`, `/api/orca101/manageusersv2`, `/orca/insprogetv2`
- レポート: `/orca/prescriptionv2`, `/orca/medicinenotebookv2`, `/orca/karteno1v2`, `/orca/karteno3v2`, `/orca/invoicereceiptv2`, `/orca/statementv2`
- 入力補助: `/api01rv2/subjectiveslstv2`（`web-client` 側コメントでも旧経路フォールバックを明記）

## 検証結果
- `npm -C web-client run typecheck` PASS
- `npm -C web-client run test -- src/libs/http/httpClient.test.ts` PASS（31 tests）
- `mvn -f server-modernized/pom.xml -DskipTests test-compile` PASS

## 次アクション（P2-06 本体）
1. `P2-10` の API map に、`/api/v1/orca/bridge` から最終 JSON API への移行先を追記。
2. 機能単位で bridge 依存を新 `/api/v1/**` 契約へ置換。
3. 置換完了後、XML 専用 resource と `api01rv2` alias 群を削除して `P2-06` を完了。
