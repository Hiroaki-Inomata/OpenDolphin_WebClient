# memory

- RUN_ID: 20260313T054324Z
- 実行開始: 2026-03-13
- ブランチ: `work/server-modernization-20260313T054324Z`
- 対象: `P10-06` 完了化と切替記録更新

## 実施内容
- validation 環境 `opendolphin_prodcutover_20260312t234207z` を継続利用し、`P10-06` 完了条件を満たすための runtime 不整合を順次修正した。
- `PVTResource` を `getSerializeMapper().copy()` 基準へ変更し、JSON -> `PatientVisitModel` 復元で `LocalDateTime` を壊さないよう補正した。
- `KarteServiceBean` の HQL property 名を Hibernate 6 の entity field (`creator`, `karte`) に合わせ、`populateKarteDetails` / `ChartEventServiceBean.initializePvtList()` の query parameter を `LocalDateTime` bind へ統一した。
- `DocumentIntegrityService` で新規 `DocumentIntegrityEntity` を全フィールド設定後に `persist` するよう修正し、`/orca/order/bundles` の `seal_version` null rollback を解消した。
- `PatientImageServiceBean` の upload 時に digest を保存するよう補正し、患者画像/PDF 保存経路の完全性を揃えた。
- validation / production env sample に `DOCUMENT_INTEGRITY_*` と `OPENDOLPHIN_PATIENT_IMAGES_ENABLED=true` を追記した。
- `docs/server-modernization/planning/server_modernization_wbs_detailed.md`、`docs/modernization/p10-05-cutover-checklist-modernized.md`、`docs/modernization/p10-06-cutover-execution-blocker.md`、`docs/DEVELOPMENT_STATUS.md` を更新し、`P10-06` 完了と `P10-07` への引継ぎ観点を記録した。

## 判断理由
- `P10-06` の未解消点は環境起動ではなく runtime の型不整合と integrity row 生成順序に収束していたため、局所修正で主要業務疎通を回復する方針を採った。
- readiness は `pvtQueue.status=UP` を維持しており、`workerStatus=DISABLED` は blocker ではなく運用 watch item と判断した。
- `P10-07` は 3 日集中監視タスクであり、この RUN の timebox で完了条件に届かないため、新規着手は行わず次回先頭未着手として残すのが妥当と判断した。

## 実行した検証
- `mvn -f pom.server-modernized.xml -pl server-modernized -Dtest=PVTResourceLimitTest,KarteServiceBeanGetKarteTest,KarteServiceBeanGetDocumentsBulkFetchTest,ChartEventServiceBeanInitializationTest,DocumentIntegrityServiceTest,PatientImageServiceBeanTest test`
  - 結果: PASS（17 tests）
- `POST http://localhost:29080/openDolphin/resources/api/session/login`
  - 条件: cookie + `X-CSRF-TOKEN` + `Origin` + `facilityId=1.3.6.1.4.1.9414.72.103` + `userId=doctor1`
  - 結果: PASS（200）
- `GET http://localhost:29080/openDolphin/resources/health/readiness`
  - 条件: login 後 cookie
  - 結果: PASS（200, `database/orca/attachmentStorage/pvtQueue`=UP）
- `POST http://localhost:29080/openDolphin/resources/orca/patient/mutation`
  - 条件: `operation=create/update`
  - 結果: PASS（200 / `登録完了`, `更新完了`）
- 既存 smoke（同一 RUN 内）:
  - `GET /resources/karte/documents/9102003`, `GET /resources/karte/attachment/9105001`, `GET /resources/karte/image/9104001`, `POST /resources/orca/medical/records`, `POST /resources/pvt`, `POST /resources/orca/order/bundles`, `GET /resources/api/admin/access/users`
  - 結果: PASS（admin API は admin session=200 / unauthenticated=401）

## 所要時間の目安
- 調査・修正・テスト・再疎通・記録: 約 55 分

## 次に着手すべきタスク
- `P10-07` 切替後の集中監視と是正を行う。
  - day1/day2/day3 の監視記録テンプレートを作成し、`pvtQueue.workerStatus=DISABLED`、`otel-collector` name 解決警告、患者画像一覧 API の実運用観察を watch item として追跡する。
