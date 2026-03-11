# P4-04 入力検証・認可・監査の横断部品化（RUN_ID: 20260311T110043Z）

## 目的
Admin系 Resource に散在していた認可・監査処理を横断部品へ集約し、Resource 本体の責務を業務処理中心へ寄せる。

## 実施内容
- 追加: `server-modernized/src/main/java/open/dolphin/rest/AdminResourceSupport.java`
  - `requireAdminActor(...)` を共通化
  - `recordAudit(...)` を共通化
- 変更: `AdminAccessResource`
  - `requireAdminActor` を共通サポート呼び出しに切替
  - `recordAudit` を共通サポート呼び出しに切替
  - 認可失敗時の監査記録（401/403）は既存互換で維持
- 変更: `AdminOrcaUserResource`
  - `requireAdminActor` を共通サポート呼び出しに切替
  - `recordAudit` を共通サポート呼び出しに切替

## 期待効果
- 認可ロジックの重複実装を排除し、判定仕様の一貫性を維持しやすくした。
- 監査イベント生成の共通化により、監査フィールド（trace/request/run/timestamp）の揺れを抑制した。

## 検証
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=AdminAccessResourceTest,AdminOrcaUserResourceTest,WebXmlEndpointExposureTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 結果: PASS（12 tests）
