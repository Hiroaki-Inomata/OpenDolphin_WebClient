# P1-06 ORCA 連携テスト判定表

- RUN_ID: 20260310T215509Z
- 対象: P1-06（ORCA 連携の性格確認テスト）
- 方針: 今回残す ORCA 機能を業務単位で固定し、正常系 + 代表失敗系を自動テストで判定する。

## 今回残す ORCA 機能
| 機能 | 正常系テスト | 代表失敗系テスト | 備考 |
|---|---|---|---|
| 患者検索（patientgetv2） | `OrcaPatientApiResourceRunIdTest#getPatient_propagatesRunIdFromHeader` | `OrcaPatientApiResourceRunIdTest#getPatient_rejectsMissingIdAndRecordsFailureAudit` | `X-Run-Id` 透過と監査記録を固定 |
| 患者更新（/orca/patient/mutation update） | `OrcaPatientResourceIdempotencyTest#updateReturnsSuccessWhenPatientExists` | `OrcaPatientResourceIdempotencyTest#updateReturnsNotFoundWhenPatientMissing` | 施設スコープでの更新可否を固定 |
| 受付連携（acceptlstv2） | `OrcaAcceptanceListResourceTest#postAcceptList_returnsStubAndAudit` | `OrcaAcceptanceListResourceTest#postAcceptList_rejectsJsonPayloadAndRecordsFailureAudit` | XML2 制約と監査失敗記録を固定 |
| オーダー連携（/orca/order/**） | `OrcaOrderBundleResourceTest#getBundlesReturnsEnteredByNameAndRole` | `OrcaOrderBundleResourceTest#getRecommendationsRejectsMissingPatientId` | オーダー取得/推薦の業務判定を固定 |
| 会計・診療情報（medical） | `OrcaMedicalModV2ResourceTest#postOutpatientMedical_returnsTelemetryAndAudit` | `OrcaMedicalResourceTest#returns404AndApiResultWhenKarteMissing` | 応答データ + エラー契約を固定 |

## 使用した stub / fixture
- `open.dolphin.orca.transport.StubOrcaTransport`
- `server-modernized/src/test/resources/fixtures/p1-03/orca-patientlst1v2-response.xml`

## 実行コマンド（P1-06）
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
  mvn -o -f pom.server-modernized.xml \
  -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar \
  -Dtest=OrcaPatientApiResourceRunIdTest,OrcaPatientResourceIdempotencyTest,OrcaAcceptanceListResourceTest,OrcaOrderBundleResourceTest,OrcaMedicalResourceTest,OrcaMedicalModV2ResourceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
