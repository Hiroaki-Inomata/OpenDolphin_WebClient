# P10-06 本番切替実施ブロッカー

## 事象
- タスク `P10-06`（本番切替を実施する）は継続して未完了。
- 最新 RUN: `20260312T120057Z`

## 実施した試行（RUN_ID: 20260312T120057Z）
1. 本番切替に必要な環境設定ファイル確認
- `ops/modernized-server/config/server-modernized.production.env` の存在確認
- 結果: **ファイルなし**（production接続情報未配置）

2. buildx 依存を避けた検証起動の再試行
- `docker-compose.modernized.validation.yml` の `container_name` を env 上書き可能に変更
- `docker-compose.modernized.dev.yml` の `custom.properties` マウント先を env 変数化（`MODERNIZED_CUSTOM_PROPERTIES_FILE`）
- 検証 sample を追加: `ops/modernized-server/config/custom.properties.validation.sample`
- `docker compose ... up -d --no-build --force-recreate` を RUN_ID 固有の container 名/port で実行
- 結果: **起動自体は成功**（DB/MinIO healthy, server container 起動）

3. 稼働確認（health/readiness）の実行
- `curl http://localhost:29080/openDolphin/resources/dolphin`
- `curl http://localhost:29080/openDolphin/resources/health`
- `curl http://localhost:29080/openDolphin/resources/health/readiness`
- 結果: **すべて 404**
- `docker logs opendolphin-server-modernized-validation-20260312T120057Z` で確認した主因:
  - `WELD-001480` / `WELD-001410`（`LegacyObjectMapperProducer` の `@ApplicationScoped` proxy 不可）
  - `opendolphin-server.war` が `started (with errors)` で health endpoint 未公開

## 既知履歴（RUN_ID: 20260312T110053Z）
- `start-validation-env.sh` 実行時に Docker buildx activity 更新で `operation not permitted` が発生。

## 未解消理由
- 本番用 env（`server-modernized.production.env`）と本番接続情報が未提供のため、`P10-06` の本番切替実行条件を満たせない。
- 代替の検証起動は可能になったが、現行 `server-modernized-dev` イメージは Weld 起動エラーで業務/API ヘルス確認を完了できない。

## 次回着手条件
1. サンプルから本番用 env を作成する。
   - `cp ops/modernized-server/config/server-modernized.production.env.sample ops/modernized-server/config/server-modernized.production.env`
   - 実運用値（DB/S3/FIDO2/秘密情報）を反映してから実行する。
2. `LegacyObjectMapperProducer` の CDI スコープ/注入設定を修正した最新イメージで再起動し、`/openDolphin/resources/health` と `.../readiness` を通す。
3. `P10-05` チェックリストに沿って本番切替を実施し、切替記録（当日ログ、疎通結果、引継ぎメモ）を本書へ追記する。
