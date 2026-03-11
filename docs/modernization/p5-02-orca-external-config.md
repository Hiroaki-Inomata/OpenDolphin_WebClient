# P5-02 接続設定と認証情報の外部設定化（RUN_ID: 20260311T130114Z）

## 目的
- ORCA 接続設定の読み込み元を「外部設定」に統一し、`custom.properties` 依存を除去する。
- 接続先/資格情報/リトライ設定の優先順位を固定する。

## 変更内容
- `OrcaTransportSettings` の設定ロードを更新。
  - 廃止: `custom.properties` / `ORCAConnection` プロパティ読み込みフォールバック。
  - 新規: `環境変数 > JVM システムプロパティ` の順で解決。
  - 対応キー:
    - `ORCA_BASE_URL` / `orca.base-url`
    - `ORCA_API_HOST` / `orca.api.host`
    - `ORCA_API_PORT` / `orca.api.port`
    - `ORCA_API_SCHEME` / `orca.api.scheme`
    - `ORCA_API_USER` / `orca.api.user`
    - `ORCA_API_PASSWORD` / `orca.api.password`
    - `ORCA_API_PATH_PREFIX` / `orca.api.path-prefix`
    - `ORCA_API_WEBORCA` / `orca.api.weborca`
    - `ORCA_MODE` / `orca.mode`
    - `ORCA_API_RETRY_MAX` / `orca.api.retry.max`
    - `ORCA_API_RETRY_BACKOFF_MS` / `orca.api.retry.backoff-ms`
- `OrcaConnectionConfigStore` の初期値ロードを更新。
  - `defaultFromEnvironment()` と `resolveUseWeborca()` で同じく `環境変数 > JVM システムプロパティ` を採用。

## テスト
- 追加: `OrcaTransportSettingsExternalConfigTest`
  - 外部システムプロパティのみで起動可能であることを検証。
  - `jboss.home.dir/custom.properties` が存在しても無視されることを検証。
- 追加: `OrcaConnectionConfigStoreTest#initReadsExternalSystemPropertiesWhenEnvIsMissing`
  - store 初期化時にシステムプロパティから ORCA 接続情報を復元できることを検証。

## 検証コマンド
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
mvn -f pom.server-modernized.xml -pl server-modernized -am \
  -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar \
  -Dtest=OrcaConnectionConfigStoreTest,OrcaTransportSettingsSecurityPolicyTest,OrcaTransportSettingsExternalConfigTest,AdminOrcaConnectionResourceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## 備考
- `P8-03` で予定している全体設定ローダー統一の前段として、ORCA 接続のローカルファイル依存を先に除去した。
