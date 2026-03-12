# P9-01 ログ形式の統一

- 更新日: 2026-03-12
- RUN_ID: 20260312T040136Z
- 対象: `server-modernized`

## 実施内容
1. `LogFilter` の access log を単一フォーマットへ統一。
- 形式: `access method=... uri=... status=... elapsedMs=... traceId=... requestId=... runId=... userId=... facilityId=... remoteAddr=...`
- 正常系は `INFO`、`4xx/5xx` は `WARNING` で出力。

2. MDC 共通項目を追加。
- `userId`
- `facilityId`

3. principal (`FACILITY:USER`) から `facilityId` / `userId` を抽出して MDC に設定し、処理終了時に復元する。

## 期待効果
- access log / エラー時ログで request 相関のキーが揃う。
- 施設・ユーザー単位の追跡がログ基盤上で容易になる。

## 検証
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile` PASS
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -Dtest=LogFilterTest,AbstractResourceErrorResponseTest -Dsurefire.failIfNoSpecifiedTests=false test` PASS
