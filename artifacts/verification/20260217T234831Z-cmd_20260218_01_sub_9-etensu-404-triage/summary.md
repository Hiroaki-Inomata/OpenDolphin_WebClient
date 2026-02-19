# cmd_20260218_01_sub_9 etensu 404 切り分け

## 結論
- 判定: **(a) 想定仕様**（不具合ではない）
- `/orca/master/etensu?keyword=腹&category=2` の 404 は、経路未実装ではなく **検索結果0件時の仕様 (`TENSU_NOT_FOUND`)**。
- よって `cmd_20260218_01` の受入判定（401混在解消）からは、この 404 を除外すべき。

## 1) API仕様観点
- `docs/server-modernization/MODERNIZED_REST_API_INVENTORY.md` に ORCA-08 の仕様として
  - `/orca/tensu/etensu?...` で **404 は `TENSU_NOT_FOUND`** と明記（line 260）。

## 2) 実装観点
- `server-modernized/src/main/java/open/orca/rest/OrcaMasterResource.java`
  - `apiRoute = "/orca/master/etensu"`（line 684）
  - DB結果0件時に `Status.NOT_FOUND` + `TENSU_NOT_FOUND` を返却（line 773-779）
  - fallback経路でも0件時は同様に 404 `TENSU_NOT_FOUND`（line 738-744）
- `server-modernized/src/main/java/open/orca/rest/OrcaResource.java`
  - `/orca/tensu/etensu` は `/orca/master/etensu` へ 301 リダイレクト（line 665-683）
- `web-client/src/features/charts/orderMasterSearchApi.ts`
  - `etensu` は `/orca/master/etensu` を使用（line 93）

## 3) 入力値/実測観点（traceId証跡）
- 404ケース:
  - `GET /orca/master/etensu?keyword=腹&category=2` → 404
  - traceId: `d29ad692-3125-4f90-822a-831f6f64a3af`
  - body: `{"code":"TENSU_NOT_FOUND", ... "errorCategory":"not_found"}`
- 404ケース（keywordなし）:
  - `GET /orca/master/etensu?category=2` → 404
  - traceId: `75b69458-307a-40a3-80b2-da094bc5cfe8`
- 200ケース（同一エンドポイントの健全性確認）:
  - `GET /orca/master/etensu?page=1&size=1` → 200
  - traceId: `9e5fc208-b7ba-49be-bc4a-8ab3d3519816`
  - body先頭: `totalCount=5000`（データ自体は存在）

## 受入判定への反映提案
- `cmd_20260218_01` の「主要カテゴリ操作時のマスタ取得成功」判定では、
  - 認証/経路/実装不備由来の 4xx/5xx と、
  - `TENSU_NOT_FOUND`（検索条件に対する業務的0件）
  を分離すること。
- 本件 404 (`keyword=腹&category=2`) は後者のため、認証修正の不達成根拠にはしない。

## 証跡ファイル
- `status_trace.tsv`
- `req1.body.json` / `req1.headers.txt`（404 + traceId）
- `req2.*`（`/orca/tensu/etensu`→301→404）
- `req5.*`（`category=2` 単独でも404）
- `req6.*`（同 endpoint の 200確認）
