# cmd_20260217_01_sub_8 主要カテゴリ操作再々検証（ashigaru7）

- RUN_ID: 20260217T203217Z-cmd_20260218_01_sub_1-auth-canonical-retest
- Base URL: http://localhost:5173
- 実施時刻: 2026-02-17T20:33:22.432Z

## 主要カテゴリ操作（実画面）
|カテゴリ|HTTP|totalCount|選択|項目反映|判定|traceId|証跡|
|---|---:|---:|---:|---:|---|---|---|
|prescription|401|-|no|yes|fail|97426859-b365-486e-9872-25492729ac88|screenshots/prescription.png|
|injection|-|-|no|no|fail|-|screenshots/injection_error.png|
|procedure|-|-|no|no|fail|-|screenshots/procedure_error.png|
|test|-|-|no|no|fail|-|screenshots/test_error.png|
|charge|-|-|no|no|fail|-|screenshots/charge_error.png|

## 前回失敗キーワード再試験（API）
|path|status|traceId|
|---|---:|---|
|/orca/master/generic-class?keyword=アム&page=1&size=50|401|3ba07f1c-1f2f-4ee7-9aa0-e2ff6eb0777b|
|/orca/master/material?keyword=ガーゼ|401|c309c66c-0e2e-4320-a92d-e2808bc9dd22|
|/orca/master/youhou?keyword=朝食|401|7ff14462-7b1b-4992-8a15-5db957bde84b|
|/orca/master/kensa-sort?keyword=血液|401|9da5bc5a-a95d-4306-be83-73f3fb1dee77|
|/orca/master/etensu?keyword=腹&category=2|401|6981687e-c0da-4bf3-9d64-546b0a9a7be5|

## 備考
- `summary-v2.json` に master応答ログ全件とカテゴリ別詳細を保存。