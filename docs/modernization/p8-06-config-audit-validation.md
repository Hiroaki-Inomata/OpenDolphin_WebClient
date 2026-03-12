# P8-06 設定変更の監査と入力検証

- 更新日: 2026-03-12
- RUN_ID: 20260312T040136Z
- 対象: `server-modernized`

## 目的
- 管理APIでの設定変更操作について、成功/失敗を監査へ残す。
- 危険な誤設定や不正入力を保存前に遮断する。

## 実装内容
1. `AdminConfigResource` に設定更新時の入力検証を追加。
- `orcaEndpoint` は `http/https` URL かつ host 必須。
- `chartsMasterSource` は `auto/orca/local` のみ許容。
- `deliveryMode` は `manual/auto` のみ許容。
- `environment` 長さ（32文字）・`note` 長さ（2000文字）を制限。

2. `AdminConfigResource` に監査記録を追加。
- `PUT /api/admin/config` 成功/失敗の両方で `SessionAuditDispatcher` へ記録。
- `runId`, `actor`, `facilityId`, `changedKeys`, `status` を `details` に格納。

3. `AdminOrcaConnectionResource` の失敗監査を強化。
- multipart 解析失敗/入力不備時に `ADMIN_ORCA_CONNECTION_SAVE` を `FAILURE` で記録。
- Store update の `IllegalArgumentException` / `IllegalStateException` も監査へ反映。

4. テスト追加/更新。
- 追加: `server-modernized/src/test/java/open/dolphin/rest/AdminConfigResourceTest.java`
- 既存: `AdminOrcaConnectionResourceTest` と合わせて回帰確認。

## 検証
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -Dtest=AdminConfigResourceTest,AdminOrcaConnectionResourceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - FAIL（JDK21 + Mockito inline attach 制約）
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=AdminConfigResourceTest,AdminOrcaConnectionResourceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - PASS（8 tests）
