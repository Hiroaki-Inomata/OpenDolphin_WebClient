# P10-03 UAT ブロッカー記録

- 日付: 2026-03-12
- RUN_ID: 20260312T100053Z
- タスク: P10-03（未完了）

## 運用方針更新（次回ワーカー向け）
- 決定日: 2026-03-12
- 方針: 実運用ロール参加が揃わない場合でも、**仮想ロール（医師/受付/事務）を明示したUAT台本を作成し、同一担当がロールを切り替えて通し実施する方式**で `P10-03` を進めてよい。
- 追加前提: 旧サーバー比較は必須条件にしない。**モダナイズ版単体で主要業務フローが正常に通ること**を完了判定の中心とする。
- 完了判定の最小条件:
  1. 役割別シナリオ（医師/受付/事務）を文書化する。
  2. 各シナリオで期待結果/実結果/差分（必須修正・改善候補）を記録する。
  3. `P10-05` へ引き渡せる指摘一覧を作成する。

## 再試行（20260312T100053Z）
- UAT 完了条件を再評価し、現行環境で role-based 手順を即時実行可能かを確認。
- 結論:
  - 実運用ロール（医師/受付/事務）による現場観点の受け入れ実査が依然として未実施。
  - 既存の技術回帰テスト PASS は維持されているが、完了条件の UAT 充足とはみなせない。
- 判定: 本RUNでも `P10-03` は未完了のまま保持。

## 前回試行（20260312T090057Z）
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
1. 検証環境（P10-01 + P10-02 済み）で、P1 最重要フローを役割別（医師/受付/事務）台本に落とし込む。
2. 実運用ロールが揃わない場合は仮想ロールで代替実施し、期待結果/実結果/差分を記録する。
3. 指摘を `必須修正` / `改善候補` に分類し、P10-05 へ入力する。
