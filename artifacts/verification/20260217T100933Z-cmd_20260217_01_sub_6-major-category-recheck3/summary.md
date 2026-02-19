# cmd_20260217_01_sub_6 主要カテゴリ操作再検証（ashigaru7）

- RUN_ID: 20260217T100933Z-cmd_20260217_01_sub_6-major-category-recheck3
- Base URL: http://localhost:5173
- 実施時刻: 2026-02-17T10:11:36.299Z

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
|/orca/master/generic-class?keyword=アム&page=1&size=50|401|5c42c1c7-69c4-4b2a-9819-7c246ddf2faa|
|/orca/master/material?keyword=ガーゼ|401|1969b50c-a4d2-4e78-aec4-5b5967265f24|
|/orca/master/youhou?keyword=朝食|401|35e91810-756c-4083-b6f7-b14733332f0c|
|/orca/master/kensa-sort?keyword=血液|401|289b3336-02da-4f02-936a-4a9ef940423d|
|/orca/master/etensu?keyword=腹&category=2|401|f8241f13-e449-48a8-b960-5199fa5b8478|