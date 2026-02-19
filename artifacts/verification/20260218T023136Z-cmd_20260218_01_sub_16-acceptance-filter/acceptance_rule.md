# cmd_20260218_01_sub_16 受入除外ルール（短文化）

## 目的
最終受入で「401認証不整合」と無関係な404を誤検知しない。

## 判定ルール（短文化）
- `GET /orca/master/etensu`（または `/orca/tensu/etensu` リダイレクト経由）が `404` の場合、以下を満たせば **受入失敗から除外** する。
  - レスポンス `code == TENSU_NOT_FOUND`
  - レスポンス `errorCategory == not_found`
  - 同エンドポイントの健全性確認（例: `page=1&size=1`）が `200`
- 上記に当てはまらない `401` / `403` / `5xx` / `404(非TENSU_NOT_FOUND)` は、認証・実装不整合として **受入失敗** 扱い。

## 適用範囲
- 対象: `cmd_20260218_01` の「主要カテゴリ操作時のマスタ取得成功」判定。
- 非対象: ORCA-08検索仕様としての業務的0件（`TENSU_NOT_FOUND`）。

## 根拠
- 仕様: `docs/server-modernization/MODERNIZED_REST_API_INVENTORY.md:260`
- 実装: `server-modernized/src/main/java/open/orca/rest/OrcaMasterResource.java:739`, `server-modernized/src/main/java/open/orca/rest/OrcaMasterResource.java:774`
- リダイレクト経路: `server-modernized/src/main/java/open/orca/rest/OrcaResource.java:681`
- クライアント呼び出し: `web-client/src/features/charts/orderMasterSearchApi.ts:93`
- 証跡: `evidence.tsv`
