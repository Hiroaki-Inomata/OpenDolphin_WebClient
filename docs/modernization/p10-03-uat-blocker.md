# P10-03 UAT ブロッカー記録

- 日付: 2026-03-12
- RUN_ID: 20260312T090057Z
- タスク: P10-03（未完了）

## 実施した試行
- UAT 前提の技術事前確認として、患者・カルテ・受付(PVT)・ORCA・添付・管理/認証に対応する回帰テストを実行。
- 実行コマンド:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
mvn -o -f pom.server-modernized.xml -pl server-modernized -am \
  -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar \
  -Dtest=PatientModV2OutpatientResourceTest,KarteResourceCaseListV3Test,PVTServiceBeanClinicalTest,OrcaOrderBundleResourceTest,PatientImagesResourceTest,AdminAccessResourceTest,SessionAuthResourceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
- 結果: PASS（42 tests, Failures 0 / Errors 0）

## 未解消理由
- P10-03 の完了条件は「医師・受付・事務の現場観点で主要業務が通ること」であり、API/ユニットテスト PASS だけでは UAT 完了判定にならない。
- 本 RUN では実運用ロール（医師/受付/事務）によるシナリオ実施と指摘収集を実行できないため、タスクを完了扱いにできない。

## 次回着手条件
1. 検証環境（P10-01 + P10-02 済み）に対して UAT 実施者を確保する。
2. P1 で固定した最重要フローを台本化して、役割別（医師/受付/事務）に実査する。
3. 指摘を `必須修正` / `改善候補` に分類し、P10-05 へ入力する。

