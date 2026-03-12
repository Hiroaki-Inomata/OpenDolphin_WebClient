# P9-02 メトリクス生成の単純化

- 日付: 2026-03-12
- RUN_ID: 20260312T050136Z
- タスク: P9-02

## 目的
JNDI fallback や registry 掃除の複雑な経路を減らし、運用で監視するメトリクスを `request 数` / `error 数` / `外部連携遅延` の3分類へ整理する。

## 実装内容

### 1. MeterRegistry 生成経路の単純化
- 対象: `server-modernized/src/main/java/open/dolphin/metrics/MeterRegistryProducer.java`
- 変更:
  - OTLP 無効化フラグ、global registry の定期 sweeper、registry close/remove の回避処理を削除。
  - `JNDI lookup -> 見つかれば採用 / 見つからなければ Metrics.globalRegistry` の1経路に統一。
- 期待効果:
  - 起動時・実行時の副作用を減らし、registry 由来の調査点を最小化。

### 2. ORCA 外部連携メトリクスの追加
- 対象: `server-modernized/src/main/java/open/dolphin/orca/transport/OrcaHttpClient.java`
- 追加メトリクス:
  - `opendolphin_orca_external_request_total`
  - `opendolphin_orca_external_error_total`
  - `opendolphin_orca_external_latency`
- タグ:
  - 共通: `method`, `path`, `status`
  - エラー時のみ: `category`（`network` / `interrupted` など）
- 記録タイミング:
  - HTTP応答受信時（成功/失敗ステータス問わず request/latency 記録）
  - IOException / InterruptedException 発生時（error 記録）

### 3. 既存分類との整合
- request/error:
  - `RequestMetricsFilter` による API request/error/auth 指標を維持。
- worker:
  - `PvtService` の worker runtime gauge 指標は維持。
- 外部連携遅延:
  - 本タスクで ORCA HTTP クライアント遅延を追加し、3分類を満たす構成に整理。

## テスト
- 追加:
  - `server-modernized/src/test/java/open/dolphin/metrics/MeterRegistryProducerTest.java`
  - `server-modernized/src/test/java/open/dolphin/orca/transport/OrcaHttpClientRequestTest.java` に外部メトリクス検証を追加
- 実行:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile`
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -Dtest=MeterRegistryProducerTest,RequestMetricsFilterTest,OrcaHttpClientRequestTest,OrcaHttpClientResilienceTest -Dsurefire.failIfNoSpecifiedTests=false test` （Mockito inline attach 制約で失敗）
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=MeterRegistryProducerTest,RequestMetricsFilterTest,OrcaHttpClientRequestTest,OrcaHttpClientResilienceTest -Dsurefire.failIfNoSpecifiedTests=false test`（PASS）
