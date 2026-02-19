# cmd_20260217_01_sub_8 主要カテゴリ操作再々検証（ashigaru7）

- RUN_ID: 20260217T202728Z-cmd_20260217_01_sub_9-auth-fix-retest
- Base URL: http://localhost:5173
- 実施時刻: 2026-02-17T20:28:31.496Z

## 主要カテゴリ操作（実画面）
|カテゴリ|HTTP|totalCount|選択|項目反映|判定|traceId|証跡|
|---|---:|---:|---:|---:|---|---|---|
|prescription|401|-|no|yes|fail|68da5dbc-decb-4e10-a95f-b502b8cda010|screenshots/prescription.png|
|injection|-|-|no|no|fail|-|screenshots/injection_error.png|
|procedure|-|-|no|no|fail|-|screenshots/procedure_error.png|
|test|-|-|no|no|fail|-|screenshots/test_error.png|
|charge|-|-|no|no|fail|-|screenshots/charge_error.png|

## 前回失敗キーワード再試験（API）
|path|status|traceId|
|---|---:|---|
|/orca/master/generic-class?keyword=アム&page=1&size=50|401|c52d5848-9e95-43f5-a84d-e2ddc3085c93|
|/orca/master/material?keyword=ガーゼ|401|622ce556-dd45-4544-9a4a-4d88f2102411|
|/orca/master/youhou?keyword=朝食|401|6e67d454-e51b-4e01-8d09-9c68bf5b7dfd|
|/orca/master/kensa-sort?keyword=血液|401|ba274cef-cfdc-40bf-a2f2-921e61269302|
|/orca/master/etensu?keyword=腹&category=2|401|d69f239a-bd09-4fae-a94a-3d77dde7172b|

## 備考
- `summary-v2.json` に master応答ログ全件とカテゴリ別詳細を保存。