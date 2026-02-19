# cmd_20260217_01_sub_8 主要カテゴリ操作再々検証（ashigaru7）

- RUN_ID: 20260218T140430Z-cmd_20260218_01_sub_21
- Base URL: http://localhost:5173
- 実施時刻: 2026-02-18T05:04:24.939Z

## 主要カテゴリ操作（実画面）
|カテゴリ|HTTP|totalCount|選択|項目反映|判定|traceId|証跡|
|---|---:|---:|---:|---:|---|---|---|
|prescription|200|0|no|yes|partial|-|screenshots/prescription.png|
|injection|-|-|no|no|fail|-|screenshots/injection_error.png|
|procedure|-|-|no|no|fail|-|screenshots/procedure_error.png|
|test|-|-|no|no|fail|-|screenshots/test_error.png|
|charge|-|-|no|no|fail|-|screenshots/charge_error.png|

## 前回失敗キーワード再試験（API）
|path|status|traceId|
|---|---:|---|
|/orca/master/generic-class?keyword=アム&page=1&size=50|401|8634040f-9ee2-4336-87ff-986bb45c80d5|
|/orca/master/material?keyword=ガーゼ|401|1c8c420b-0e84-4edb-835c-0e11298cffc0|
|/orca/master/youhou?keyword=朝食|401|7cd79afb-cefa-41f1-b0c0-597e46a49cbf|
|/orca/master/kensa-sort?keyword=血液|401|9f595662-f473-43a7-ae78-38b35033a62f|
|/orca/master/etensu?keyword=腹&category=2|401|b5d2cecd-80e2-4dbe-98d3-fb5ac85d5ed4|

## 備考
- `summary-v2.json` に master応答ログ全件とカテゴリ別詳細を保存。