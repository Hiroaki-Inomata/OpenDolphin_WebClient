# P4-05 エラー応答形式と request id 統一（RUN_ID: 20260311T110043Z）

## 目的
全 API でエラー応答の追跡キー（`traceId` / `requestId` / `runId`）と基本項目を揃え、運用時のログ相関を統一する。

## 実施内容
- 変更: `AbstractResource#buildErrorBody`
  - `requestId` を標準付与（`LogFilter.REQUEST_ID_ATTRIBUTE` → `X-Request-Id` → `traceId` の順に解決）
  - `runId` を標準付与（`LogFilter.RUN_ID_ATTRIBUTE` → `X-Run-Id`）
  - `timestamp` を標準付与
- 変更: `OrcaGatewayExceptionMapper`
  - 独自エラーボディ生成を廃止し、`AbstractResource.restError(...)` へ統一
  - ORCA例外も共通 JSON エラー形式（`error`/`status`/`errorCategory`/`traceId`/`requestId`/`runId`）へ寄せた

## 追加テスト
- `server-modernized/src/test/java/open/dolphin/rest/AbstractResourceErrorResponseTest.java`
  - requestId/runId の解決優先順位
  - requestId の traceId fallback
- `server-modernized/src/test/java/open/dolphin/rest/OrcaGatewayExceptionMapperTest.java`
  - ORCA例外の共通エラー形式化

## 検証
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=AbstractResourceErrorResponseTest,OrcaGatewayExceptionMapperTest,WebXmlEndpointExposureTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 結果: PASS（4 tests）
