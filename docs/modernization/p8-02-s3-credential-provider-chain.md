# P8-02 S3 認証の固定資格情報廃止

- RUN_ID: 20260312T010127Z
- 日付: 2026-03-12
- 対象: `server-modernized`

## 目的

S3 連携で access key / secret key の固定設定依存をやめ、実行環境の認証基盤（環境変数、IAM ロール、IRSA、シークレットマネージャ連携）へ寄せる。

## 実装内容

1. S3 設定モデルから固定資格情報を削除
- `AttachmentStorageSettings.S3Settings`
  - `accessKey` / `secretKey` を削除。

2. 添付・画像の S3 クライアント生成を統一
- `AttachmentStorageManager#createClient`
- `ImageStorageManager#createClient`
  - `StaticCredentialsProvider(AwsBasicCredentials)` を廃止し、`DefaultCredentialsProvider` を使用。

3. 設定ローダーの必須項目を整理
- `AttachmentStorageConfigLoader`
  - `ATTACHMENT_S3_ACCESS_KEY` / `ATTACHMENT_S3_SECRET_KEY` の必須チェックを削除。
  - バケット、リージョン、endpoint、SSE など運用必須項目のみ保持。

4. サンプル設定を更新
- `server-modernized/config/attachment-storage.sample.yaml`
  - 固定資格情報を記載しない方針を明記。

## 検証

- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile`
  - PASS
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=AttachmentStorageManagerTest,PatientImagesResourceTest,RuntimeConfigurationSupportTest,SmsGatewayConfigTest,OrcaTransportSettingsExternalConfigTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - PASS（26 tests）

## 次のタスク

- WBS 先頭未着手は `P8-04`（各種 Store のローカル JSON 永続化を DB 化）。
