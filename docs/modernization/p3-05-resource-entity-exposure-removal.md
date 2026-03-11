# P3-05 REST 層 entity 直返し/直受け 解消記録

- 実施日: 2026-03-11
- RUN_ID: 20260311T080109Z
- WBS: `P3-05`

## 変更概要
- `UserResource` の `readJson(..., UserModel.class)` を `UserMutationRequest` DTO + mapper 経由へ置換。
- `SystemResource` の `addFacilityAdmin` 入力を `UserMutationRequest` DTO + mapper 経由へ置換。
- `SystemResource#getActivities` の返却型を `List<ActivityModel>` から `List<ActivitySummaryResponse>` へ置換。
- `UserMutationRequestMapper` / `ActivitySummaryResponseMapper` を追加し、resource 層から entity の直接公開・直接受理を除去。

## 追加ファイル
- `api-contract/src/main/java/open/dolphin/rest/dto/UserMutationRequest.java`
- `api-contract/src/main/java/open/dolphin/rest/dto/ActivitySummaryResponse.java`
- `server-modernized/src/main/java/open/dolphin/rest/support/UserMutationRequestMapper.java`
- `server-modernized/src/main/java/open/dolphin/rest/support/ActivitySummaryResponseMapper.java`

## 変更ファイル
- `server-modernized/src/main/java/open/dolphin/rest/UserResource.java`
- `server-modernized/src/main/java/open/dolphin/rest/SystemResource.java`
- `server-modernized/src/test/java/open/dolphin/rest/SystemResourceTest.java`

## 検証
- `mvn -f pom.server-modernized.xml -pl api-contract,server-modernized -am -DskipTests test-compile` : PASS
- `mvn -f pom.server-modernized.xml -pl server-modernized -am -Dtest=UserResourceTest,SystemResourceTest -Dsurefire.failIfNoSpecifiedTests=false test` : FAIL（JDK25 Mockito inline attach 既知問題）
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=UserResourceTest,SystemResourceTest -Dsurefire.failIfNoSpecifiedTests=false test` : PASS（30 tests）
