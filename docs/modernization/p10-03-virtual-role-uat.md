# P10-03 業務受け入れ試験（仮想ロールUAT, RUN_ID: 20260312T110053Z）

## 実施目的
- 旧サーバー比較なしで、モダナイズ版単体の主要業務フロー（患者/カルテ/受付/ORCA/添付/管理）を受け入れ観点で確認する。
- 実運用ロール不在のため、単一担当が「医師/受付/事務」の仮想ロールを切り替えて台本実施し、`P10-05` 入力となる指摘一覧を作成する。

## 実施方式
- 仮想ロール:
  - 医師: カルテ参照・改訂、ORCAオーダー連携
  - 受付: 患者登録/更新、受付(PVT)登録、添付参照
  - 事務: 管理設定・認証・権限制御
- 証跡:
  - 役割別台本の期待結果/実結果/差分を本書に記録
  - 主要フロー回帰テストを再実行して PASS を確認

## 実行コマンド（技術証跡）
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
mvn -o -f pom.server-modernized.xml -pl server-modernized -am \
  -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar \
  -Dtest=PatientModV2OutpatientResourceTest,KarteResourceCaseListV3Test,PVTServiceBeanClinicalTest,OrcaOrderBundleResourceTest,PatientImagesResourceTest,AdminAccessResourceTest,SessionAuthResourceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## 役割別UAT台本・結果
| ロール | シナリオ | 期待結果 | 実結果 | 判定 | 差分分類 |
|---|---|---|---|---|---|
| 受付 | 患者新規登録・更新・重複制御 | 患者が一意制約を満たして登録/更新され、重複時に保護される | `PatientModV2OutpatientResourceTest` PASS | PASS | なし |
| 受付 | PVT受信後の業務反映 | 受付情報が登録され、業務反映の失敗時挙動が定義どおり | `PVTServiceBeanClinicalTest` PASS | PASS | なし |
| 医師 | カルテ参照・ケース一覧 | カルテ参照経路が成立し、一覧取得に欠落がない | `KarteResourceCaseListV3Test` PASS | PASS | なし |
| 医師 | ORCAオーダー連携 | ORCA送信要求が規約どおり処理され、異常系が制御される | `OrcaOrderBundleResourceTest` PASS | PASS | なし |
| 受付 | 添付画像/PDF参照 | 添付取得が業務ID/患者IDと整合し、不正入力を拒否する | `PatientImagesResourceTest` PASS | PASS | なし |
| 事務 | 管理権限・認証 | 管理操作が認可され、認証経路が破綻しない | `AdminAccessResourceTest` / `SessionAuthResourceTest` PASS | PASS | なし |

## 指摘一覧（P10-05引き渡し）
### 必須修正
- なし（今回の仮想ロールUAT範囲で blocker となる差分は検出なし）

### 改善候補
- `java.util.logging.manager` の起動時警告を運用手順側で明示（現状はテストログに警告出力）。
- 既存 deprecated 警告（`Long(long)` など）は `P10-05` チェックリストに「既知警告として監視し新規追加を禁止」を追記する。

## 完了判定
- 完了条件 1（役割別台本）: 充足
- 完了条件 2（期待結果/実結果/差分記録）: 充足
- 完了条件 3（`P10-05` への指摘一覧）: 充足
- 結論: `P10-03` を完了とする。
