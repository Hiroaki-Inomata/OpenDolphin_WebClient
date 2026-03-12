# P9-04 JUnit5 基盤統一

- 日付: 2026-03-12
- RUN_ID: 20260312T050136Z
- タスク: P9-04

## 目的
`common` と `server-modernized` のテスト基盤差分（JUnit4/JUnit5混在）をなくし、Jupiter ベースへ統一する。

## 実施内容

### 1. common module のテスト依存を JUnit5 化
- 対象: `common/pom.xml`
- 変更:
  - `junit:junit:4.13.2` を削除。
  - `org.junit.jupiter:junit-jupiter-api` / `junit-jupiter-engine` を追加。
  - `maven-surefire-plugin` を `3.1.2` へ固定（offline キャッシュ済み provider を利用）。

### 2. common 側テスト2件を Jupiter へ移行
- 対象:
  - `common/src/test/java/open/dolphin/common/OrcaApiEncodingTest.java`
  - `common/src/test/java/open/dolphin/common/OrcaAnalyzeTest.java`
- 変更:
  - `org.junit.Test` -> `org.junit.jupiter.api.Test`
  - `org.junit.Assert.assertEquals` -> `org.junit.jupiter.api.Assertions.assertEquals`

## 移行済みテスト一覧（common）
- `OrcaApiEncodingTest`
- `OrcaAnalyzeTest`

## 検証
- 実行:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl common -am -Dtest=OrcaApiEncodingTest,OrcaAnalyzeTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 結果: PASS（2 tests）
