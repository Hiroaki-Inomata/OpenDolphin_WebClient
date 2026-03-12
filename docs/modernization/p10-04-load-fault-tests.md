# P10-04 負荷試験・障害試験（RUN_ID: 20260312T100053Z）

## 実施目的
- 本番切替前に、主要経路（カルテ保存/患者検索/ORCA連携/添付保存/worker）の負荷・障害時挙動を、再現可能な自動試験として確認する。

## 実施コマンド
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
mvn -o -f pom.server-modernized.xml -pl server-modernized -am \
  -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar \
  -Dtest=KarteServiceBeanBatchWriteTest,PatientServiceBeanSearchLoadFaultTest,OrcaHttpClientResilienceTest,AttachmentStorageManagerTest,PvtSocketWorkerPipelineTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## 対象経路と結果
| 経路 | テスト | 観点 | 結果 |
|---|---|---|---|
| カルテ保存 | `KarteServiceBeanBatchWriteTest` | バルク削除・バッチ境界（flush/clear） | PASS（3 tests） |
| 患者検索 | `PatientServiceBeanSearchLoadFaultTest` | 600回連続検索時の応答性/NoResult時のfail-safe | PASS（2 tests） |
| ORCA連携 | `OrcaHttpClientResilienceTest` | 5xx再試行/deadline timeout/並列呼出/設定不備fail-fast | PASS（4 tests） |
| 添付保存 | `AttachmentStorageManagerTest` | S3 upload/download/streaming/不正入力時例外 | PASS（10 tests） |
| worker | `PvtSocketWorkerPipelineTest` | 重複抑止/再試行/毒メッセージ退避 | PASS（3 tests） |

## 実測メモ
- 総実行: `real 3.83s`（22 tests, Failures 0 / Errors 0）。
- `PatientServiceBeanSearchLoadFaultTest`: 600回連続 `getPatientById` が 2秒閾値内で完了（テストで閾値検証）。
- `OrcaHttpClientResilienceTest`: 並列GETの `durationMs` は 122ms / 130ms（直列化しないことを確認）。

## 運用設定（安全側）
- `orca.api.total-timeout-ms` は再試行待機総和を上回る値を維持する（短すぎると deadline で早期失敗）。
- `pvt.listen.retry.max` と `pvt.listen.retry.backoffMillis` は poison 化までの猶予を業務要件に合わせて調整する。
- 添付系は stream upload/download 経路を標準とし、巨大バイナリのメモリ保持を避ける。
