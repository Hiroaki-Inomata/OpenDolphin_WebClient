# P7-06 ワーカー監視項目とヘルスチェック

- 更新日: 2026-03-12
- RUN_ID: 20260311T230123Z
- 対象: `server-modernized` PVT 受信ワーカー（`PvtSocketWorker` / `PvtService`）

## 1. 実装概要
- `PvtSocketWorker` に実行時スナップショット（`RuntimeSnapshot`）を追加し、以下を常時取得可能にした。
  - 受信件数 / ACK件数 / 失敗件数 / 重複ACK件数
  - 再試行回数（attempt超過分）
  - 毒メッセージ累計件数・キュー滞留件数
  - 最終受信時刻 / 最終成功時刻 / 最終失敗時刻 / 最終失敗理由
  - 最大処理時間 / 累積処理時間 / 連続失敗数 / 処理中件数
- `PvtService` に監視向け集約APIを追加した。
  - `workerSnapshot()`
  - `workerHealthBody()`
  - `workerThresholds()`
- `PvtWorkerHealthResource` (`GET /resources/health/worker/pvt`) を追加し、JSONで状態を返すようにした。
- Micrometer Gauge を `PvtService` から登録し、PVTワーカー主要指標を metrics に露出した。

## 2. 監視項目（metrics）
- `opendolphin_pvt_worker_running`
- `opendolphin_pvt_worker_received_total`
- `opendolphin_pvt_worker_ack_total`
- `opendolphin_pvt_worker_failed_total`
- `opendolphin_pvt_worker_retry_attempt_total`
- `opendolphin_pvt_worker_poison_total`
- `opendolphin_pvt_worker_poison_queue_depth`
- `opendolphin_pvt_worker_last_success_epoch_seconds`
- `opendolphin_pvt_worker_last_failure_epoch_seconds`
- `opendolphin_pvt_worker_max_processing_millis`

## 3. health endpoint 契約
- URL: `GET /resources/health/worker/pvt`
- 返却例: `status`, `reasons`, `metrics`, `thresholds`, `checkedAt`
- ステータス判定:
  - `UP`: 実行中で劣化条件なし
  - `DEGRADED`: 以下のいずれか
    - 最終成功時刻が閾値超過で古い
    - 連続失敗数が閾値以上
    - 処理遅延（max processing）が閾値超過
    - poison queue が空でない
  - `DOWN`: worker 有効なのに未起動
  - `DISABLED`: `useAsPVTServer=false` で worker 未使用

## 4. 閾値（初期値）
- `staleSuccessSeconds`: 180秒（`-Dpvt.worker.health.stale-success-seconds` で上書き可）
- `failureStreak`: `max(2, pvt.listen.retry.max)`
- `maxProcessingMillis`: 30000ms（`-Dpvt.worker.health.max-processing-millis` で上書き可）

## 5. 検証
- `mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile`
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=PvtSocketWorkerPipelineTest,PvtWorkerHealthResourceTest,WebXmlEndpointExposureTest -Dsurefire.failIfNoSpecifiedTests=false test`
