# P4-03 患者更新・管理系 Resource 分割（RUN_ID: 20260311T110043Z）

## 目的
`PatientModV2OutpatientResource` / `AdminAccessResource` / `AdminOrcaUserResource` の責務集中を緩和し、機能別に endpoint を分離する。

## 実施内容
- `PatientModV2OutpatientResource` から mock 経路 endpoint を分離。
  - 追加: `server-modernized/src/main/java/open/dolphin/rest/PatientModV2OutpatientMockResource.java`
  - 維持パス: `POST /orca12/patientmodv2/outpatient/mock`
- `AdminAccessResource` からパスワードリセット endpoint を分離。
  - 追加: `server-modernized/src/main/java/open/dolphin/rest/AdminAccessPasswordResetResource.java`
  - 維持パス: `POST /api/admin/access/users/{userPk}/password-reset`
- `AdminOrcaUserResource` から EHR-ORCA リンク endpoint を分離。
  - 追加: `server-modernized/src/main/java/open/dolphin/rest/AdminOrcaUserLinkResource.java`
  - 維持パス: `PUT/DELETE /api/admin/users/{ehrUserId}/orca-link`
- `WEB-INF/web.xml` の `resteasy.resources` に上記3 Resource を追加。

## 分割方針
- 既存 endpoint の処理ロジックは親 Resource に残し、新規 Resource は endpoint 定義と委譲に限定。
- 既存 API 契約とレスポンス形式を維持し、挙動差分を最小化。

## 検証
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=PatientModV2OutpatientResourceIdempotencyTest,AdminAccessResourceTest,AdminOrcaUserResourceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 結果: PASS（14 tests）

## 影響範囲
- `server-modernized/src/main/java/open/dolphin/rest/PatientModV2OutpatientResource.java`
- `server-modernized/src/main/java/open/dolphin/rest/AdminAccessResource.java`
- `server-modernized/src/main/java/open/dolphin/rest/AdminOrcaUserResource.java`
- `server-modernized/src/main/webapp/WEB-INF/web.xml`
- 追加3 Resource（上記）
