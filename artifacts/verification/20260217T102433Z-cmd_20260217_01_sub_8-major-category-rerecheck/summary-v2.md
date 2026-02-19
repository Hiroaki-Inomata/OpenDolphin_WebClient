# cmd_20260217_01_sub_8 主要カテゴリ操作再々検証（ashigaru7）

- RUN_ID: 20260217T102433Z-cmd_20260217_01_sub_8-major-category-rerecheck
- Base URL: http://localhost:5173
- 実施時刻: 2026-02-17T10:32:20.080Z

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
|/orca/master/generic-class?keyword=アム&page=1&size=50|401|ed450049-3c77-4756-b819-de0d4161762d|
|/orca/master/material?keyword=ガーゼ|401|0693c7d2-7655-4664-a8f5-8727f8b44b25|
|/orca/master/youhou?keyword=朝食|401|50391b62-1c83-45d8-a62f-f35d8ffb8fc3|
|/orca/master/kensa-sort?keyword=血液|401|568ab2e2-61e8-4bc6-953b-02f6aa7ffe03|
|/orca/master/etensu?keyword=腹&category=2|401|80e7d311-6198-4901-9865-acc6a98abb53|

## 備考
- `summary-v2.json` に master応答ログ全件とカテゴリ別詳細を保存。