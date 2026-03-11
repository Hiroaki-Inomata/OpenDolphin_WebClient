# P5-08 stub を使った adapter 統合試験整備（RUN_ID: 20260311T160115Z）

## 実施概要
- `OrcaPatientAdapter` の標準実装 `DefaultOrcaPatientAdapter` を追加。
- ORCA 本番環境に依存しないよう、`StubOrcaTransport` を使った adapter 統合試験を新規追加。
- 代表エラー系として `patientmodv2` の `Api_Result != 0000` を再現する試験を追加。

## 追加/変更ファイル
- 追加: `server-modernized/src/main/java/open/dolphin/orca/adapter/DefaultOrcaPatientAdapter.java`
- 追加: `server-modernized/src/test/java/open/dolphin/orca/adapter/DefaultOrcaPatientAdapterStubIntegrationTest.java`

## adapter と stub 対応表
| adapter ユースケース | ORCA endpoint | 利用 stub |
|---|---|---|
| `searchPatients` | `PATIENT_NAME_SEARCH` (`/api01rv2/patientlst3v2`) | `orca/stub/10_patientlst3v2_response.sample.xml` |
| `upsertPatient` | `PATIENT_MOD` (`/orca12/patientmodv2`) | `orca/stub/53_patientmodv2_response.sample.xml` |
| `registerReception` | `ACCEPTANCE_MUTATION` (`/orca11/acceptmodv2`) | `orca/stub/04_acceptmodv2_response.sample.xml` |

## 代表エラー系
- `upsertPatient` 実行時に `Api_Result=E999` を返す transport を差し込み、`OrcaGatewayException` 送出を確認。
- これにより、adapter 単位で「外部失敗を業務層へ明示する」契約を固定。

## 未使用 stub（P5-08 範囲）
- P5-08 では adapter の患者検索・患者更新・受付登録のみを対象化したため、上記3件以外の stub ファイルは未使用。
- 網羅的な endpoint 単位の契約試験は `P5-09`（性能・障害試験）と後続タスクで拡張する。
