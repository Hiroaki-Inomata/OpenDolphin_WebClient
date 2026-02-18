# ORCA API契約統一マッピング（2026-02-18）

- RUN_ID: `20260218T133538Z`
- 目的: `web-client` から `/api01rv2` 依存を排除し、`server-modernized` の `/orca/*` 契約へ統一する。
- 方針: 通信経路（web-client -> server-modernized -> ORCA）は維持し、公開パスのみ `/orca/*` に統一する。

## 旧->新 API マッピング

| 旧パス | 新パス |
| --- | --- |
| `/api01rv2/patientgetv2` | `/orca/patientgetv2` |
| `/api01rv2/patientlst7v2` | `/orca/patientlst7v2` |
| `/api01rv2/diseasegetv2` | `/orca/diseasegetv2` |
| `/api01rv2/medicalgetv2` | `/orca/medicalgetv2` |
| `/api01rv2/tmedicalgetv2` | `/orca/tmedicalgetv2` |
| `/api01rv2/incomeinfv2` | `/orca/incomeinfv2` |
| `/api01rv2/contraindicationcheckv2` | `/orca/contraindicationcheckv2` |
| `/api01rv2/medicationgetv2` | `/orca/medicationgetv2` |
| `/api01rv2/subjectiveslstv2` | `/orca/subjectiveslstv2` |
| `/api01rv2/systeminfv2` | `/orca/systeminfv2` |
| `/api01rv2/system01dailyv2` | `/orca/system01dailyv2` |
| `/api01rv2/insuranceinf1v2` | `/orca/insuranceinf1v2` |
| `/api01rv2/pusheventgetv2` | `/orca/pusheventgetv2` |
| `/api01rv2/acceptlstv2` | `/orca/acceptlstv2` |
| `/api01rv2/system01lstv2` | `/orca/system01lstv2` |
| `/api01rv2/insprogetv2` | `/orca/insprogetv2` |
| `/api01rv2/prescriptionv2` | `/orca/prescriptionv2` |
| `/api01rv2/medicinenotebookv2` | `/orca/medicinenotebookv2` |
| `/api01rv2/karteno1v2` | `/orca/karteno1v2` |
| `/api01rv2/karteno3v2` | `/orca/karteno3v2` |
| `/api01rv2/invoicereceiptv2` | `/orca/invoicereceiptv2` |
| `/api01rv2/statementv2` | `/orca/statementv2` |

## 実装メモ

- `server-modernized` には上記新パスの受け口を追加し、既存の `respond*` ロジックを再利用。
- `web-client` の業務コード・デバッグUI・MSW・QAスクリプトを新パスへ更新。
- CIガード `scripts/check-no-api01rv2.sh` を追加し、`web-client` に `/api01rv2` が残る場合に失敗させる。
