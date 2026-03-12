# P8-03 設定読み込み一本化（YAML / properties / JSON）

- RUN_ID: 20260312T010127Z
- 日付: 2026-03-12
- 対象: `server-modernized`

## 目的

設定値の解決順序を `環境変数 > JVMシステムプロパティ > JSON保存値 > YAML値 > legacy custom.properties` に統一し、`custom.properties` 直接読込のばらつきを削減する。

## 実装内容

1. 共通ローダーを追加
- `open.dolphin.runtime.RuntimeConfigurationSupport`
  - `resolveConfigDirectory()` / `resolveConfigPath(...)`
  - `resolveLegacyCustomPropertiesPath()` / `loadLegacyCustomProperties()`
  - `resolveUnifiedSetting(...)`

2. YAML 系設定の統一
- `AttachmentStorageConfigLoader`
  - `attachment-storage.yaml` の既定位置を `opendolphin.config.dir` ベースへ統一。
  - `MODERNIZED_STORAGE_MODE`・`ATTACHMENT_S3_*` を共通解決ロジックで読込。

3. properties 系設定の統一
- `SmsGatewayConfig`
  - `PLIVO_*` / `plivo.*` / legacy `custom.properties` を共通解決ロジックで読込。
- `ORCAConnection`
  - `custom.properties` の直接ファイル読み込みを廃止し、`loadLegacyCustomProperties()` に統一。

4. 検証テスト追加
- `RuntimeConfigurationSupportTest`
- `SmsGatewayConfigTest`

## 設定キー整理（代表）

| 用途 | 環境変数 | JVMシステムプロパティ | JSON/YAML | legacy properties |
|---|---|---|---|---|
| 添付設定ファイルパス | `ATTACHMENT_STORAGE_CONFIG_PATH` | `attachment.storage.config.path` | `config/attachment-storage.yaml` | - |
| 添付S3バケット | `ATTACHMENT_S3_BUCKET` | `attachment.s3.bucket` | `storage.s3.bucket` | - |
| 添付S3リージョン | `ATTACHMENT_S3_REGION` | `attachment.s3.region` | `storage.s3.region` | - |
| Plivo認証ID | `PLIVO_AUTH_ID` | `plivo.auth.id` | - | `plivo.auth.id` |
| Plivo認証Token | `PLIVO_AUTH_TOKEN` | `plivo.auth.token` | - | `plivo.auth.token` |
| ORCA legacy 設定読込パス | - | `opendolphin.custom.properties.path` | - | `custom.properties` |

## 検証

- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile`
  - PASS
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -Dtest=RuntimeConfigurationSupportTest,SmsGatewayConfigTest,OrcaTransportSettingsExternalConfigTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - PASS（6 tests）

## 次のタスク

- WBS 先頭未着手は `P8-02`（S3 認証を固定資格情報から外す）。
