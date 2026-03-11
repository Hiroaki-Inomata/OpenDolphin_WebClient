# P4-01 KarteResource 責務分割（RUN_ID: 20260311T090121Z）

## 実施概要
- 対象: `server-modernized` の `KarteResource`。
- 目的: 1クラスに集中していた文書更新系責務（作成/更新/削除/監査）を読取中心の Resource から分離し、変更影響の局所化を進める。

## 変更内容
1. 新規 Resource 追加
- `server-modernized/src/main/java/open/dolphin/rest/KarteDocumentWriteResource.java`
- `@Path("/karte")` 配下で以下の write/revision 系エンドポイントを担当。
  - `POST /document`
  - `PUT /document`
  - `POST /document/pvt/{params}`
  - `PUT /document/{id}`
  - `DELETE /document/{id}`

2. 既存 Resource から責務を除去
- `server-modernized/src/main/java/open/dolphin/rest/KarteResource.java`
- 上記 document write/revision 系メソッドおよび関連監査ヘルパーを削除。
- `KarteResource` は read 系中心（取得・一覧・検索・参照）に縮小。

3. テスト更新
- `server-modernized/src/test/java/open/dolphin/rest/KarteResourceDocumentContractTest.java`
- `@InjectMocks` とリフレクション検証対象を `KarteDocumentWriteResource` へ変更し、`text/plain` 契約の維持を確認。

## 互換性
- 公開パスは既存の `/karte/document*` を維持。
- 応答契約（`text/plain` の PK 返却）を維持。

## 検証
- `mvn -f pom.server-modernized.xml -pl server-modernized -am -Dtest=KarteResourceAuthorizationTest,KarteResourceDocumentContractTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - JDK25 既定では Mockito inline attach 制約により FAIL（既知事象）。
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=KarteResourceAuthorizationTest,KarteResourceDocumentContractTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - PASS（17 tests）。

## 残課題（次タスクへの接続）
- `P4-02` で `KarteServiceBean` を use case 単位へ分割し、Resource 分割で分離した write/read 境界に合わせて service 層の責務も整理する。
