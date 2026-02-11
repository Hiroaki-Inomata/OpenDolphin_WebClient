# Live E2E Fullflow (20260211T052022Z)
- baseUrl: http://localhost:5173
- facilityId: 1.3.6.1.4.1.9414.10.1
- patientId: 00001
- receptionApiResult: Api_Result: 16
- sendToast: ORCA送信を完了

runId=20260211T052029Z / traceId=d5143760-c6a6-476f-81f8-f84196b6c8cd / Api_Result=80 / Api_Result_Message=既に同日の診療データが登録されています

閉じる
- invoice: n/a
- dataId: n/a
- failedSteps: 0
- http500: 0

## Steps
- [PASS] ログイン (2303ms)
- [PASS] 患者0001検索と選択 (3919ms)
- [PASS] 受付登録（患者0001） (1526ms)
- [PASS] カルテを開く (3028ms)
- [PASS] 患者サマリ保存 (295ms)
- [PASS] 病名入力（追加） (6930ms)
- [PASS] SOAP全欄記載と保存（Free入力確認） (3246ms)
- [PASS] 症状詳記入力と登録 (726ms)
- [PASS] 処方オーダー保存 (5681ms)
- [PASS] 注射オーダー保存 (5847ms)
- [PASS] 処置オーダー保存 (7413ms)
- [PASS] 検査オーダー保存 (7633ms)
- [PASS] 算定オーダー保存 (12607ms)
- [PASS] 文書作成と保存 (8524ms)
- [PASS] 診療終了 (2138ms)
- [PASS] ORCA送信 (6759ms)
- [PASS] 会計反映確認（Reception） (65241ms)

## Key API Status
- freedocument: 200
- safety: 400
- rpHistory: 200
- accept: 200
- medicalmodv2: 200, 200
- medicalmodv23: 200
