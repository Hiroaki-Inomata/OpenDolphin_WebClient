# H-clinical (RUN_ID=20260221T032658Z)

## 変更概要
- `PVTBuilder` の保険パースで `insuranceClass` 要素欠落時に NPE となる null チェック誤りを修正。
- `PVTServiceBean` で `pvtDate` を `yyyy-MM-dd` / `yyyy-MM-ddTHH:mm:ss` から正規化し、`T` 前提の `substring/indexOf` 例外を排除。
- `PVTServiceBean` の保険更新を「全削除→全再登録」から、GUID/保険メタ情報での突合による「更新/追加」方式へ変更（受信が部分集合でも既存を維持）。
- `OrcaDiseaseResource` / `OrcaOrderBundleResource` の create/update で `startDate` を厳密検証し、不正時は 400 (`invalid_request`) を返すように変更（`now` 代入を廃止）。
- `OrcaMedicalModV2Resource` の外来サマリ取得で、対象日を基準に 30 日範囲の下限/上限を明示化し、当日データ混入を抑止。

## 追加・更新テスト
- `PVTBuilderTest`: 保険要素欠落時に落ちないことを確認。
- `PVTServiceBeanClinicalTest`: `pvtDate` 正規化、保険マージ時の既存維持を確認。
- `OrcaDiseaseResourceTest`: `startDate` 不正の 400 を確認。
- `OrcaOrderBundleResourceTest`: `startDate` 不正の 400 を確認。
- `OrcaMedicalModV2ResourceTest`: 過去日指定時に当日データが混在しないことを確認。

## 検証
- `mvn -q test` (server-modernized) PASS
