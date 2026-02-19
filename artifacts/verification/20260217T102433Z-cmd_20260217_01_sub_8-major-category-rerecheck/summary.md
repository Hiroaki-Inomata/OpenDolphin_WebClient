# cmd_20260217_01_sub_8 主要カテゴリ操作再検証（ashigaru7）

- RUN_ID: 20260217T102433Z-cmd_20260217_01_sub_8-major-category-rerecheck
- Base URL: http://localhost:5173
- 実施時刻: 2026-02-17T10:26:13.062Z

## 主要カテゴリ操作（実画面）
|カテゴリ|HTTP|選択|項目反映|判定|traceId|証跡|
|---|---:|---:|---:|---|---|---|
|prescription|-|no|no|fail|-|screenshots/prescription_error.png|
|injection|-|no|no|fail|-|screenshots/injection_error.png|
|procedure|-|no|no|fail|-|screenshots/procedure_error.png|
|test|-|no|no|fail|-|screenshots/test_error.png|
|charge|-|no|no|fail|-|screenshots/charge_error.png|

## 前回失敗キーワード再試験（API）
|path|status|traceId|
|---|---:|---|
|/orca/master/generic-class?keyword=アム&page=1&size=50|401|7a89e45a-ceca-402e-b13e-2f227d2d1934|
|/orca/master/material?keyword=ガーゼ|401|2edb7ecb-3501-4cda-a303-0573f34f726f|
|/orca/master/youhou?keyword=朝食|401|2ce34d15-1cb2-4064-9767-5d137c4304ab|
|/orca/master/kensa-sort?keyword=血液|401|8da53bb6-30f7-4538-8375-b9e2762e9ca3|
|/orca/master/etensu?keyword=腹&category=2|401|1102951d-a3a1-43f3-93b0-93336205d480|

## 備考
- `summary.json` に master応答ログ全件を保存。