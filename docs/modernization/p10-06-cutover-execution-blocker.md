# P10-06 本番切替実施ブロッカー

## 事象
- タスク `P10-06`（本番切替を実施する）は継続して未完了。
- 最新 RUN: `20260312T130107Z`

## 実施した試行（RUN_ID: 20260312T130107Z）
1. Weld 起動失敗のコード修正
- `server-modernized/src/main/java/open/dolphin/rest/jackson/LegacyObjectMapperProducer.java` の producer method scope を `@ApplicationScoped` から `@Dependent` へ変更。
- `mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile` は **PASS**。

2. validation build 導線の修正
- `ops/modernized-server/docker/Dockerfile` に `domain` / `api-contract` / `persistence` のコピーを追加し、`pom.server-modernized.xml` はリポジトリ同梱版をそのまま使用するよう変更。
- これにより、従来の build 失敗:
  - `Child module /workspace/persistence ... does not exist`
  - `Could not find artifact opendolphin:opendolphin-api-contract:jar:2.7.1`
  を解消。

3. `docker compose ... build server-modernized-dev` 再試行
- `DOCKER_AUTH_CONFIG` を明示し build を複数回再実行。
- `gcloud.auth.docker-helper` の権限警告は出るが build 自体は継続し、前述のモジュール欠落エラーは再発しないことを確認。
- ただし依存解決フェーズが長時間化し、今回実行時間内に image build 完了まで到達できず。

4. 本番 env 前提
- `ops/modernized-server/config/server-modernized.production.env` は引き続き未配置。

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
- Weld 起動失敗の直接要因と Dockerfile のモジュール欠落は修正済みだが、最新 image build 完了および再起動後の `health/readiness` 実測まで未到達。

## 次回着手条件
1. サンプルから本番用 env を作成する。
   - `cp ops/modernized-server/config/server-modernized.production.env.sample ops/modernized-server/config/server-modernized.production.env`
   - 実運用値（DB/S3/FIDO2/秘密情報）を反映してから実行する。
2. 今回の修正を含む image を最後まで build し、validation compose を `--build` で再起動して `health/readiness` を実測する。
3. `P10-05` チェックリストに沿って本番切替を実施し、切替記録（当日ログ、疎通結果、引継ぎメモ）を本書へ追記する。

## 後続ワーカー向けメモ（Weld修正は適用済み）
- 症状: `ObjectMapper` を `@ApplicationScoped` producer で提供しており、Weld が client proxy を作れず `WELD-001480` / `WELD-001410` を発生。
- 対象: `server-modernized/src/main/java/open/dolphin/rest/jackson/LegacyObjectMapperProducer.java`
- 実施済み変更:
  - producer method のスコープを `@ApplicationScoped` から `@Dependent` へ変更済み。
  - producer class 側の `@ApplicationScoped` は維持。
- 期待効果:
  - proxy 不要の依存注入となり、`KarteResource` / `KarteRevisionResource` / `ResteasyObjectMapperResolver` の起動時注入エラーを解消できる見込み。
- 最低検証:
  - `docker compose --env-file ops/modernized-server/config/server-modernized.production.env -f docker-compose.modernized.dev.yml -f docker-compose.modernized.validation.yml up -d --build`
  - `curl http://localhost:<MODERNIZED_APP_HTTP_PORT>/openDolphin/resources/health`
  - `curl http://localhost:<MODERNIZED_APP_HTTP_PORT>/openDolphin/resources/health/readiness`
