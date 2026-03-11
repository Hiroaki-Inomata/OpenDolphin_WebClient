# P6-05 永続化アクセスの repository/query 層統一（第一段）

- 実施日: 2026-03-12
- RUN_ID: 20260311T200758Z
- 対象WBS: `P6-05`

## 目的
- `KarteServiceBean` に混在していた患者・カルテ・ユーザー読取クエリの入口を、用途別 query service へ集約する。
- read 経路の調整箇所を `session` 実装から分離し、今後の native query / DAO 再編（P6-06）に備える。

## 実装内容
- 追加: `server-modernized/src/main/java/open/dolphin/persistence/query/PatientQueryService.java`
  - `findSingleKarteByFacilityAndPatientId(...)`
  - `findSingleKarteByPatientPk(...)`
- 追加: `server-modernized/src/main/java/open/dolphin/persistence/query/UserQueryService.java`
  - `findByCompositeUserId(...)`
- 追加: `server-modernized/src/main/java/open/dolphin/persistence/query/KarteDocumentQueryService.java`
  - `findDocumentsByIds(...)`
  - `findModulesByDocumentIds(...)`
- 変更: `server-modernized/src/main/java/open/dolphin/session/KarteServiceBean.java`
  - 上記 query service を経由する内部入口 (`patientQueries/userQueries/karteDocumentQueries`) を追加。
  - カルテ取得（患者軸）・ユーザー属性取得・文書/モジュール取得を query service 経由へ置換。

## 検証
- 実行コマンド:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=KarteServiceBeanGetKarteTest,KarteServiceBeanDocPkTest,OrcaOrderBundleResourceTest,OrcaSubjectiveResourceTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 結果: PASS（26 tests）

## 残課題（P6-06 以降）
- `SystemServiceBean` / `UserServiceBean` / `PVTServiceBean` などに残る native query / raw JDBC の棚卸しと置換は `P6-06` で継続。
