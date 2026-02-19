# cmd_20260217_01_sub_6 主要カテゴリ操作再検証（ashigaru7）

- RUN_ID: 20260217T101251Z-cmd_20260217_01_sub_6-major-category-recheck4
- Base URL: http://localhost:5173
- 実施時刻: 2026-02-17T10:14:57.877Z

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
|/orca/master/generic-class?keyword=アム&page=1&size=50|401|2c3d32df-4b92-419b-931b-4c8b390d615b|
|/orca/master/material?keyword=ガーゼ|401|ab494886-330e-4d33-b7e6-75fd65c4509c|
|/orca/master/youhou?keyword=朝食|401|c5713c66-929b-4d8d-8fde-70e9f0b20f5d|
|/orca/master/kensa-sort?keyword=血液|401|9656e720-c7b2-41e8-85da-a06033bc53c7|
|/orca/master/etensu?keyword=腹&category=2|401|0ee9ed0c-4a34-4c39-ac11-1908677d25b6|