# Legacy Cutover Allowlist

- RUN_ID: `20260309T145604Z`
- Source of truth: `web-client/src/**` の production code を優先し、`__tests__`, `mocks`, `artifacts`, `dist`, `node_modules`, `target` は除外した。
- 方針: 今の web-client が実際に呼ぶ path と、現行 ORCA 連携で必要な path のみを残す。古く見える prefix でも current contract なら保持する。

## Keep

| Keep path | Production source file(s) | Remove alias / old resource | Keep reason |
| --- | --- | --- | --- |
| `/api/session/login`, `/api/session/login/factor2`, `/api/session/me`, `/api/logout` | `web-client/src/LoginScreen.tsx`, `web-client/src/AppRouter.tsx` | なし | 現行ログイン、2FA、セッション復元、ログアウトの契約。 |
| `/user/{userId}` | `web-client/src/features/charts/stampApi.ts` | 旧 touch 系 user DTO には寄せない | stamp 編集で入力者取得に使用。 |
| `/chart-events` | `web-client/src/libs/sse/chartEventStream.ts`, `web-client/src/features/shared/ChartEventStreamBridge.tsx` | `/chartEvent/*` | 現行 SSE ストリーム契約。旧 long-poll/comet resource は不要。 |
| `/karte/document`, `/karte/attachment/{id}`, `/karte/image/{id}`, `/patients/{patientId}/images` | `web-client/src/features/images/api.ts`, `web-client/src/features/images/components/ImageDockedPanel.tsx` | なし | 現行画像/添付 UI 契約。 |
| `/karte/pid/{patientId},{fromDate}`, `/karte/revisions*`, `/karte/freedocument*`, `/karte/safety/{karteId}`, `/karte/rpHistory/list/{karteId}` | `web-client/src/features/charts/letterApi.ts`, `web-client/src/features/charts/revisions/*`, `web-client/src/features/charts/patientFreeDocumentApi.ts`, `web-client/src/features/charts/karteExtrasApi.ts` | なし | 現行 Charts/Revision/Free document 契約。 |
| `/orca/appointments/list`, `/orca/visits/list`, `/api/orca/queue`, `/orca/pusheventgetv2` | `web-client/src/features/reception/api.ts`, `web-client/src/features/outpatient/orcaQueueApi.ts`, `web-client/src/features/charts/ChartsActionBar.tsx`, `web-client/src/features/reception/pages/ReceptionPage.tsx` | なし | 現行受付/送信キュー/再送 UI。 |
| `/orca21/medicalmodv2/outpatient` | `web-client/src/features/charts/api.ts`, `web-client/src/features/outpatient/OutpatientMockPage.tsx` | なし | 現行 Charts/Outpatient 一覧の基底契約。 |
| `/api21/medicalmodv2`, `/api21/medicalmodv23` | `web-client/src/features/charts/orcaClaimApi.ts`, `web-client/src/features/charts/orcaMedicalModApi.ts`, `web-client/src/features/charts/ChartsActionBar.tsx` | `/orca/medicalmodv2` への rename はしない | 現行 ORCA claim write 契約。古い prefix だが current。 |
| `/orca/order/bundles`, `/orca/prescription-orders` | `web-client/src/features/charts/orderBundleApi.ts`, `web-client/src/features/charts/prescriptionOrderApi.ts` | なし | 現行オーダー編集 UI 契約。 |
| `/orca/disease`, `/orca/diseasegetv2`, `/orca22/diseasev3`, `/orca/medicalgetv2`, `/orca/tmedicalgetv2`, `/orca/medicationgetv2`, `/orca/contraindicationcheckv2` | `web-client/src/features/charts/diseaseApi.ts`, `web-client/src/features/charts/orcaDiseaseGetApi.ts`, `web-client/src/features/charts/orcaMedicalGetApi.ts`, `web-client/src/features/charts/orcaMedicationGetApi.ts` | なし | 現行 Charts の ORCA 原本/病名/薬剤照会契約。 |
| `/api01rv2/subjectiveslstv2`, `/orca25/subjectivesv2`, `/orca/chart/subjectives` | `web-client/src/features/charts/soap/subjectivesApi.ts`, `web-client/src/features/charts/soap/subjectiveChartApi.ts`, `web-client/src/features/administration/orcaInternalWrapperApi.ts` | なし | SOAP 主訴一覧/登録の現契約。 |
| `/orca/patients/local-search*`, `/orca/patients/name-search`, `/orca/patients/import`, `/orca12/patientmodv2/outpatient`, `/orca/patientgetv2`, `/orca/patientlst7v2`, `/orca06/patientmemomodv2`, `/orca/insuranceinf1v2` | `web-client/src/features/patients/api.ts`, `web-client/src/features/reception/patientSearchApi.ts`, `web-client/src/features/outpatient/orcaPatientImportApi.ts`, `web-client/src/features/patients/patientOriginalApi.ts`, `web-client/src/features/patients/patientMemoApi.ts`, `web-client/src/features/charts/PatientInfoEditDialog.tsx` | `/patient`, `/pvt2` | 現行 patient search/update/original reference 契約。 |
| `/orca/master/address`, `/orca/master/hokenja`, `/orca/master/drug`, `/orca/master/generic-class`, `/orca/master/generic-price`, `/orca/master/youhou`, `/orca/master/material`, `/orca/master/kensa-sort`, `/orca/master/etensu`, `/orca/master/comment`, `/orca/master/bodypart`, `/orca/master/reference/status` | `web-client/src/features/patients/orcaAddressApi.ts`, `web-client/src/features/patients/orcaHokenjaApi.ts`, `web-client/src/features/charts/orderMasterSearchApi.ts`, `web-client/src/features/charts/orcaGenericPriceApi.ts`, `web-client/src/features/charts/masterReferenceStatusApi.ts` | `/api/orca/master/*` | 現行 master search 契約。web-client は `/orca/master/*` を使用している。 |
| `/api/orca101/manageusersv2`, `/api/orca51/masterlastupdatev3`, `/api/orca102/medicatonmodv2`, `/api/orca21/medicalsetv2`, `/orca/acceptlstv2`, `/orca/system01lstv2`, `/orca/insprogetv2`, `/orca/systeminfv2`, `/orca/system01dailyv2` | `web-client/src/features/administration/api.ts`, `web-client/src/features/administration/orcaXmlProxyApi.ts`, `web-client/src/features/reception/pages/ReceptionPage.tsx` | なし | 現行 Administration/Reception の ORCA XML proxy・追加 API 契約。 |
| `/orca/prescriptionv2`, `/orca/medicinenotebookv2`, `/orca/karteno1v2`, `/orca/karteno3v2`, `/orca/invoicereceiptv2`, `/orca/statementv2`, `/blobapi/{dataId}` | `web-client/src/features/charts/orcaReportApi.ts`, `web-client/src/features/charts/pages/ChartsDocumentPrintPage.tsx` | なし | 現行帳票取得と PDF blob 契約。 |
| `/api/admin/config`, `/api/admin/delivery`, `/api/admin/access/users`, `/api/admin/master-updates/*`, `/api/admin/orca/connection*`, `/api/admin/orca/users`, `/api/admin/orca/sync`, `/api/admin/users/{ehrUserId}/orca-link` | `web-client/src/features/administration/api.ts`, `web-client/src/features/administration/accessManagementApi.ts`, `web-client/src/features/administration/masterUpdateApi.ts`, `web-client/src/features/administration/orcaConnectionApi.ts`, `web-client/src/features/administration/orcaUserAdminApi.ts` | `/schedule` | 現行管理画面契約。master update schedule は `/api/admin/master-updates/schedule` に一本化。 |
| `/orca/medical-sets`, `/orca/birth-delivery`, `/orca/medical/records`, `/orca/patient/mutation` | `web-client/src/features/administration/orcaInternalWrapperApi.ts`, `web-client/src/features/administration/AdministrationPage.tsx` | 旧 stub 切替は削除するが path 自体は保持 | 現行管理画面から直接呼ばれる内製ラッパー契約。 |

## Remove

| Remove path / class | Replacement / reason |
| --- | --- |
| `/patient` (`PatientResource`) | 現行 web-client から参照なし。患者系 current contract は `/orca/patients/*`, `/orca12/patientmodv2/outpatient`, `/orca/patientgetv2`。 |
| `/lab` (`NLabResource`) | 現行 web-client から参照なし。 |
| `/reporting/karte` (`ReportingResource`) | 現行 web-client から参照なし。帳票 current contract は ORCA report + `/blobapi/*`。 |
| `/chartEvent/*` (`ChartEventResource`) | 現行契約は `/chart-events`。 |
| `/pvt2` (`PVTResource2`) | 現行 web-client から参照なし。 |
| `/schedule` (`ScheduleResource`) | 管理系 schedule は `/api/admin/master-updates/schedule` を使用。 |
| `/serverinfo/*` (`ServerInfoResource`) | 現行 web-client から参照なし。 |
| `/api/orca/master/*` (`OrcaMasterApiAliasResource`) | web-client は `/orca/master/*` のみ使用。二重 alias は不要。 |
| legacy REST console (`web-client/src/features/debug/legacyRestApi.ts` 他) | current contract の判定基準に使わず、導線ごと削除。 |

## Notes

- `web-client/src/features/debug/OrcaApiConsolePage.tsx` の ORCA API console は env-gated debug page だが、現行コードとして ship されているため ORCA path の実使用確認には補助情報として参照した。
- `msw` / `mock` suffix path は allowlist の主対象に含めない。保持対象は server-modernized が提供する current contract のみ。
