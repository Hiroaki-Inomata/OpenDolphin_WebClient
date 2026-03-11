# P3-04 API DTO 分離（entity 依存の除去）

- 実施日: 2026-03-11
- RUN_ID: 20260311T070119Z
- WBS: `P3-04`

## 実施内容
- `server-modernized/src/main/java/open/dolphin/rest/dto/**` を `api-contract/src/main/java/open/dolphin/rest/dto/**` へ移設。
- `server-modernized` に `opendolphin-api-contract` 依存を追加。
- DTO モジュール (`api-contract`) に Jackson annotations 依存を追加。
- `CurrentUserResponse` から `UserModel` 依存を除去し、entity→DTO 変換は `server-modernized` 側 `CurrentUserResponseMapper` へ移設。
- `SafetySummaryResponse` から `AllergyModel` 依存を除去し、`AllergySummaryResponse`（契約DTO）へ置換。
- `LegacyKarteListResponse` から `DocumentModel` / `ModuleModel` / `PatientFreeDocumentModel` 依存を除去し、DTOマップ済みデータ受け取りへ変更。
- `KarteResource` / `OrcaResource` / `KarteServiceBean` / `SessionAuthResource` / `UserResource` を DTO 変換責務へ追従。

## 影響
- API 契約 DTO が `api-contract` モジュールへ集約され、DTO 層は entity 型へ直接依存しない構成となった。
- entity→DTO 変換責務は resource/service/support 層へ移され、後続 `P3-05` の resource 直返し除去に接続可能になった。

## 検証
- `mvn -f pom.server-modernized.xml -pl api-contract,server-modernized -am -DskipTests test-compile`
  - 結果: PASS（BUILD SUCCESS）
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=UserResourceTest,SessionAuthResourceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 結果: PASS（23 tests）

## 補足
- JDK25 既定では Mockito inline attach 環境差異により対象テストが失敗し得るため、本検証は既定方針どおり JDK21 + byte-buddy-agent fallback で実施。
