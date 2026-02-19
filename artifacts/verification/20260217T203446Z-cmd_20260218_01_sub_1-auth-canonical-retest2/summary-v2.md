# cmd_20260217_01_sub_8 主要カテゴリ操作再々検証（ashigaru7）

- RUN_ID: 20260217T203446Z-cmd_20260218_01_sub_1-auth-canonical-retest2
- Base URL: http://localhost:5173
- 実施時刻: 2026-02-17T20:35:51.143Z

## 主要カテゴリ操作（実画面）
|カテゴリ|HTTP|totalCount|選択|項目反映|判定|traceId|証跡|
|---|---:|---:|---:|---:|---|---|---|
|prescription|401|-|no|yes|fail|6a185227-4a35-4a25-8c59-ec427a80baf5|screenshots/prescription.png|
|injection|-|-|no|no|fail|-|screenshots/injection_error.png|
|procedure|-|-|no|no|fail|-|screenshots/procedure_error.png|
|test|-|-|no|no|fail|-|screenshots/test_error.png|
|charge|-|-|no|no|fail|-|screenshots/charge_error.png|

## 前回失敗キーワード再試験（API）
|path|status|traceId|
|---|---:|---|
|/orca/master/generic-class?keyword=アム&page=1&size=50|401|c4572c7a-c08b-4f24-b150-9803685e8cde|
|/orca/master/material?keyword=ガーゼ|401|2ca20b42-33bd-466e-9310-42c053e384f5|
|/orca/master/youhou?keyword=朝食|401|6e8f6ad6-c47c-4278-b261-5450a654401f|
|/orca/master/kensa-sort?keyword=血液|401|070ca80d-1d61-4c25-be17-91a96f03fd7d|
|/orca/master/etensu?keyword=腹&category=2|401|9a746e9b-785c-4ffc-a09f-a62cdd14411f|

## 備考
- `summary-v2.json` に master応答ログ全件とカテゴリ別詳細を保存。