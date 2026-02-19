# cmd_20260217_01_sub_6 主要カテゴリ操作再検証（ashigaru7）

- RUN_ID: undefined
- Base URL: http://localhost:5173
- 実施時刻: 2026-02-17T10:07:10.574Z

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
|/orca/master/generic-class?keyword=アム&page=1&size=50|200|-|
|/orca/master/material?keyword=ガーゼ|200|-|
|/orca/master/youhou?keyword=朝食|200|-|
|/orca/master/kensa-sort?keyword=血液|200|-|
|/orca/master/etensu?keyword=腹&category=2|404|e6e79f38-8dfe-483c-8433-67bbb404c714|

## 備考
- `summary.json` に master応答ログ全件を保存。