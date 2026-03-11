# P4-08 API文書・テスト同期（RUN_ID: 20260311T120154Z）

## 目的
- P4-01〜P4-07 で分割・整理した Resource の公開契約について、文書とテストの不一致を解消する。
- 「変更した resource ごとにレビュー項目を定型化する」を運用可能な形で固定する。

## 文書更新
- `docs/modernization/api-map.md` に「P4 Resource 分割後の確認ポイント（P4-08）」節を追加。
- 対象 Resource と確認観点を表形式で明文化。

## テスト更新
- `WebXmlEndpointExposureTest` を更新し、以下の split Resource が `web.xml` に公開登録されていることを常時検証。
  - `KarteDocumentWriteResource`
  - `PatientModV2OutpatientMockResource`
  - `AdminAccessPasswordResetResource`
  - `AdminOrcaUserLinkResource`

## レビュー定型（P4系）
1. Resource ごとの公開 `@Path` と `web.xml` 登録が一致している。
2. 認可/監査（401/403/監査項目）を既存契約から後退させていない。
3. DTO/レスポンス形式を既存 API 契約から逸脱させていない。
4. テスト対象を `WebXmlEndpointExposureTest` と該当 Resource 契約テストに反映している。

## 検証
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=WebXmlEndpointExposureTest,KarteResourceDocumentContractTest,AdminAccessResourceTest,AdminOrcaUserResourceTest -Dsurefire.failIfNoSpecifiedTests=false test`
