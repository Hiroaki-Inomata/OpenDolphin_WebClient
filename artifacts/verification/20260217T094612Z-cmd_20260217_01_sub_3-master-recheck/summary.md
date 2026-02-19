# cmd_20260217_01_sub_3 再検証まとめ（ashigaru7）

## 結論
- `WEB_CLIENT_MODE=npm` 相当の `localhost:5173` 経由で主要カテゴリ代表の master API を再検証したが、5/5 で 503。
- 「マスタ取得が成功し操作可能」は未達。

## 実施条件
- Base URL: `http://localhost:5173`
- 認証ヘッダ: `userName/password`（先行再現手順と同一）
- 実行時刻: `2026-02-17T09:46:12Z`（RUN_ID 参照）

## 再現手順（代表）
1. `GET /orca/master/generic-class?keyword=アム&page=1&size=50`
2. `GET /orca/master/material?keyword=ガーゼ`
3. `GET /orca/master/youhou?keyword=朝食`
4. `GET /orca/master/kensa-sort?keyword=血液`
5. `GET /orca/master/etensu?keyword=腹&category=2`

## 結果（主要カテゴリ代表）
- 処方（generic-class）: 503 / `MASTER_GENERIC_CLASS_UNAVAILABLE`
  - traceId: `08c24d68-18f4-4801-aeda-bed25cd04cb3`
- 処置（material）: 503 / `MASTER_MATERIAL_UNAVAILABLE`
  - traceId: `9e27bd8d-af6c-4854-bbfe-dcc1049bf43a`
- 注射/用法系（youhou）: 503 / `MASTER_YOUHOU_UNAVAILABLE`
  - traceId: `b7ecc957-8d2f-4978-8355-69275b935e35`
- 検査（kensa-sort）: 503 / `MASTER_KENSA_SORT_UNAVAILABLE`
  - traceId: `48567bc0-2797-49de-947c-a5a55d806b64`
- 算定（etensu category=2）: 503 / `ETENSU_UNAVAILABLE`
  - traceId: `f4a640bf-3bf4-45bd-a6ce-0de0798d508c`

## 証跡
- `status.tsv`
- `trace_codes.tsv`
- `generic_class.{headers.txt,body.json}`
- `material.{headers.txt,body.json}`
- `youhou.{headers.txt,body.json}`
- `kensa_sort.{headers.txt,body.json}`
- `etensu_category2.{headers.txt,body.json}`
