# P9-07 運用用 health/readiness と手順書

- 更新日: 2026-03-12
- RUN_ID: 20260312T060136Z
- 対象: `server-modernized`

## 実施内容
1. 運用向け health/readiness エンドポイントを追加。
- `GET /resources/health`: liveness（プロセス生存）を返す。
- `GET /resources/health/readiness`: 以下の準備状態を集約して返す。
  - DB 接続確認（`select 1`）
  - ORCA 接続設定確認（`auditSummary`）
  - 添付ストレージ設定確認（`AttachmentStorageMode`）
  - PVT ワーカー/キュー状態確認（`PvtService.workerHealthBody()`）

2. `web.xml` の公開リソースへ `OperationsHealthResource` を追加。

3. テストを追加。
- `OperationsHealthResourceTest`
- `WebXmlEndpointExposureTest` の公開リソース確認を更新

## 運用手順（Runbook）
### 1. 始業前確認
1. liveness を確認:
- `curl -sS http://localhost:9080/openDolphin/resources/health | jq .`
2. readiness を確認:
- `curl -sS http://localhost:9080/openDolphin/resources/health/readiness | jq .`
3. `status=UP` を確認し、`checks.database/orca/attachmentStorage/pvtQueue` がすべて `UP` であることを確認する。

### 2. 障害一次切り分け
1. `database=DOWN`
- DB 接続先設定・DB 起動状態を確認し、`server-modernized` ログの DB 例外を確認する。
2. `orca=DOWN`
- 管理設定の ORCA 接続情報（URL/認証）を確認し、`auditSummary` の値を確認する。
3. `attachmentStorage=DOWN`
- `attachment-storage` 設定（mode/S3 設定/環境変数）を確認する。
4. `pvtQueue=DOWN`
- `GET /resources/health/worker/pvt` を追加確認し、`reasons` と worker 指標を確認する。

## 検証
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile` PASS
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=OperationsHealthResourceTest,PvtWorkerHealthResourceTest,WebXmlEndpointExposureTest -Dsurefire.failIfNoSpecifiedTests=false test` PASS
