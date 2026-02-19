# cmd_20260217_01_sub_1 再現まとめ（ashigaru5）

## 再現条件
- 環境: `WEB_CLIENT_MODE=npm`（localhost:5173 → server-modernized 9080）
- facility/user: `1.3.6.1.4.1.9414.72.103:doctor1`
- チャート到達確認: `charts-visit-01415.png`

## 失敗 API（全て 503）
- `GET /orca/master/generic-class?keyword=アム&page=1&size=50`
  - traceId: `8c31c0d4-c475-4ad2-9792-70a8428d3afa`
  - response code: `MASTER_GENERIC_CLASS_UNAVAILABLE`
- `GET /orca/master/material?keyword=ガーゼ`
  - traceId: `edcac36b-6da3-4516-98f6-272f1ca43787`
  - response code: `MASTER_MATERIAL_UNAVAILABLE`
- `GET /orca/master/youhou?keyword=朝食`
  - traceId: `9b117d38-ba18-4931-85b7-de0121869ec7`
  - response code: `MASTER_YOUHOU_UNAVAILABLE`
- `GET /orca/master/kensa-sort?keyword=血液`
  - traceId: `f70599b9-4112-42c5-9470-b88be13178ee`
  - response code: `MASTER_KENSA_SORT_UNAVAILABLE`
- `GET /orca/master/etensu?keyword=腹&category=2`
  - traceId: `b0637370-4b32-41a7-852b-fdbeb56e879b`
  - response code: `ETENSU_UNAVAILABLE`

## state transition
- Reception / Charts 上の `dataSourceTransition` は `server`（snapshot/fallback ではない）
- したがって、UI 側の fallback ではなく server 側 master API 自体が unavailable

## ログ所見
- `server-master-error-extract.log` に `/orca/master/*` 503 を連続記録
- Etensu は DAO 側で明示エラー:
  - `Failed to load ORCA ETENSU master: org.postgresql.util.PSQLException: ERROR: syntax error at end of input`
  - stack: `EtensuDao.fetchTotalCount(EtensuDao.java:101)`
- generic/material/youhou/kensa-sort は `ORCA_MASTER_FETCH outcome=FAILURE` + `REST_ERROR_RESPONSE` が発火

## 証跡
- `status.tsv`
- `*.headers.txt`
- `*.body.json`
- `server-modernized-since-5m.log`
- `server-master-error-extract.log`
- `charts-visit-01415.png`
