# P5-04 HTTP 呼び出しと再試行方針の再設計（RUN_ID: 20260311T130114Z）

## 目的
- ORCA HTTP 呼び出しのタイムアウト・再試行を外部設定化し、挙動を運用で調整可能にする。
- 失敗時の分類を例外メッセージへ明示し、調査時の再現性を上げる。
- 再試行待機から `Thread.sleep` 依存を排除する。

## 実施内容
- 対象: `server-modernized/src/main/java/open/dolphin/orca/transport/OrcaHttpClient.java`
- 追加した設定キー（`環境変数 > JVMシステムプロパティ`）:
  - connect timeout: `ORCA_API_CONNECT_TIMEOUT_MS` / `orca.api.connect-timeout-ms`
  - read timeout: `ORCA_API_READ_TIMEOUT_MS` / `orca.api.read-timeout-ms`
  - total timeout: `ORCA_API_TOTAL_TIMEOUT_MS` / `orca.api.total-timeout-ms`
  - network retry max/backoff: `ORCA_API_RETRY_NETWORK_MAX` / `orca.api.retry.network.max`, `ORCA_API_RETRY_NETWORK_BACKOFF_MS` / `orca.api.retry.network.backoff-ms`
  - transient retry max/backoff: `ORCA_API_RETRY_TRANSIENT_MAX` / `orca.api.retry.transient.max`, `ORCA_API_RETRY_TRANSIENT_BACKOFF_MS` / `orca.api.retry.transient.backoff-ms`
- 再試行待機を `LockSupport.parkNanos(...)` ベースに変更し、`Thread.sleep` 呼び出しを除去。
- 失敗分類を `FailureCategory` として追加し、`OrcaGatewayException` のメッセージ先頭へカテゴリコードを付与:
  - `invalid_url`, `network`, `http_status`, `empty_body`, `deadline`, `interrupted`

## 効果
- ORCA 接続遅延や障害時の挙動（タイムアウト/再試行回数）が設定で調整できる。
- 例外文言から失敗種別を即時判定しやすくなり、運用切り分けが短縮される。

## 検証コマンド
```bash
mvn -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile

JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
mvn -f pom.server-modernized.xml -pl server-modernized -am \
  -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar \
  -Dtest=AdminOrcaConnectionResourceTest,OrcaTransportSettingsSecurityPolicyTest,OrcaTransportSettingsExternalConfigTest,OrcaPatientApiResourceRunIdTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
