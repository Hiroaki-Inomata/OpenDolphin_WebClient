# P10-06 本番切替実施ブロッカー

## 事象
- タスク `P10-06`（本番切替を実施する）は継続して未完了。
- 最新 RUN: `20260312T190035Z`

## 実施した試行（RUN_ID: 20260312T190035Z）
1. Docker daemon 応答性の再確認（socket/API 直接）
- `curl --max-time 8 --unix-socket /Users/Hayato/.docker/run/docker.sock http://localhost/_ping`
- `curl --max-time 8 --unix-socket /Users/Hayato/.docker/run/docker.sock http://localhost/version`
- 結果: **いずれも RC=28 timeout**（0 bytes 応答）

2. 切替導線の fail-fast 動作確認（production env）
- `server-modernized.production.env.sample` から `/tmp/server-modernized.production.20260312T190035Z.env` を生成し、RUN専用の `COMPOSE_PROJECT_NAME` と port を付与して実行。
- `DOCKER_PING_TIMEOUT_SECONDS=2 ops/modernized-server/scripts/start-validation-env.sh /tmp/server-modernized.production.20260312T190035Z.env`
- 結果: `docker daemon is not responding ... aborting before compose up` で **RC=1**（期待どおりハング前停止）。

## 実施した試行（RUN_ID: 20260312T180123Z）
1. Docker daemon 応答性の再確認（socket/API 直接）
- `curl --max-time 8 --unix-socket /Users/Hayato/.docker/run/docker.sock http://localhost/_ping`
- `curl --max-time 8 --unix-socket /Users/Hayato/.docker/run/docker.sock http://localhost/version`
- 結果: **いずれも RC=28 timeout**（0 bytes 応答）

2. Docker CLI 経由の再確認（default context を含む）
- `docker info`（12秒 watchdog）
- `docker --context default info`（12秒 watchdog）
- 結果: **いずれも RC=1**。Client 情報は取得できるが Server は `ERROR: ... context canceled`。

3. 切替導線の fail-fast 動作確認（production env）
- `server-modernized.production.env.sample` / `custom.properties.production.sample` を `/tmp` 配下へ複製し、port 重複回避設定を与えた一時 env で実行。
- `DOCKER_PING_TIMEOUT_SECONDS=2 COMPOSE_PROJECT_NAME=opendolphin_prodcutover_20260312t180123z ops/modernized-server/scripts/start-validation-env.sh /tmp/server-modernized.production.20260312T180123Z.env`
- 結果: `docker daemon is not responding ... aborting before compose up` で **RC=1**（期待どおりハング前停止）。

## 実施した試行（RUN_ID: 20260312T170046Z）
1. Docker daemon 応答性の再確認（socket/API 直接）
- `curl --max-time 8 --unix-socket /Users/Hayato/.docker/run/docker.sock http://localhost/_ping`
- `curl --max-time 8 --unix-socket /Users/Hayato/.docker/run/docker.sock http://localhost/version`
- 結果: **いずれも RC=28 timeout**（0 bytes 応答）

2. Docker CLI 経由の再確認（default context を含む）
- `docker info`（12秒 watchdog）
- `docker --context default info`（12秒 watchdog）
- 結果: **いずれも RC=124（watchdog kill）**。Client 情報は取得できるが Server は `ERROR: ... context canceled`。

3. ソケット経路の再確認
- `/var/run/docker.sock` は `/Users/Hayato/.docker/run/docker.sock` への symlink。
- `curl --max-time 5 --unix-socket /var/run/docker.sock http://localhost/_ping`
- 結果: **RC=28 timeout**（経路差なし）

4. 切替導線の fail-fast 動作確認（production env）
- `DOCKER_PING_TIMEOUT_SECONDS=2 COMPOSE_PROJECT_NAME=opendolphin_prodcutover_20260312t170046z ops/modernized-server/scripts/start-validation-env.sh ops/modernized-server/config/server-modernized.production.env`
- 結果: `docker daemon is not responding ... aborting before compose up` で **RC=1**（期待どおりハング前停止）。

5. production env 配置状況
- sample から `server-modernized.production.env` と `custom.properties.production.local` を作成して導線実行を試行したが、daemon 応答不可のため `compose up` 到達前に停止。

## 実施した試行（RUN_ID: 20260312T160044Z）
1. Docker daemon 応答性の再確認（socket/API 直接）
- `curl --max-time 8 --unix-socket /Users/Hayato/.docker/run/docker.sock http://localhost/_ping`
- `curl --max-time 8 --unix-socket /Users/Hayato/.docker/run/docker.sock http://localhost/version`
- 結果: **いずれも RC=28 timeout**（0 bytes 応答）

2. Docker CLI 経由の再確認（context 別）
- `docker info`（12秒 watchdog）
- `docker --context desktop-linux info`（12秒 watchdog）
- `docker --context default info`（12秒 watchdog）
- 結果: **いずれも RC=124（watchdog kill）**。Client 情報は取得できるが Server は `ERROR: ... context canceled`。

3. 切替導線の fail-fast 動作確認
- `DOCKER_PING_TIMEOUT_SECONDS=2 COMPOSE_PROJECT_NAME=opendolphin_prodcutover_20260312t160044z ops/modernized-server/scripts/start-validation-env.sh ops/modernized-server/config/server-modernized.validation.env.sample`
- 結果: `docker daemon is not responding ... aborting before compose up` で **RC=1**（期待どおりハング前停止）。

4. production env 配置状況
- `ops/modernized-server/config/server-modernized.production.env` は依然未配置（sample のみ）。

## 実施した試行（RUN_ID: 20260312T150045Z）
1. Docker daemon 応答性の再確認
- `curl --max-time 8 --unix-socket /Users/Hayato/.docker/run/docker.sock http://localhost/_ping`
- `curl --max-time 8 --unix-socket /Users/Hayato/.docker/run/docker.sock http://localhost/version`
- 結果: **いずれも RC=28 timeout**（0 bytes 応答）

2. `P10-06` 実行導線のハング防止
- `ops/modernized-server/scripts/start-validation-env.sh` に Docker socket preflight を追加。
- 追加内容:
  - `DOCKER_SOCKET_PATH`（default: `$HOME/.docker/run/docker.sock`）が socket であることを確認。
  - `curl --max-time "${DOCKER_PING_TIMEOUT_SECONDS:-8}" --unix-socket ... /_ping` で daemon 応答を確認。
  - 応答不可時は `compose up` 実行前に明示エラーで停止。
- 目的: `starting containers...` 以降の無応答ハングを回避し、切替可否を即時判定できるようにする。

3. 本番 env 再確認
- `ops/modernized-server/config/server-modernized.production.env` は依然未配置（sample のみ）。

## 実施した試行（RUN_ID: 20260312T140055Z）
1. 本番 env を sample から作成
- `cp ops/modernized-server/config/server-modernized.production.env.sample ops/modernized-server/config/server-modernized.production.env`
- `cp ops/modernized-server/config/custom.properties.production.sample ops/modernized-server/config/custom.properties.production.local`
- `MODERNIZED_CUSTOM_PROPERTIES_FILE` を `custom.properties.production.local` に切替。

2. compose 定義検証
- `docker compose --env-file ops/modernized-server/config/server-modernized.production.env -f docker-compose.modernized.dev.yml -f docker-compose.modernized.validation.yml config`
- 結果: **PASS**

3. 本番切替導線の実行
- `COMPOSE_PROJECT_NAME=opendolphin_prodcutover_20260312t140055z ops/modernized-server/scripts/start-validation-env.sh ops/modernized-server/config/server-modernized.production.env`
- 結果: `compose config check...` / `starting containers...` までは進むが、以後コマンド応答が返らずハング。

4. Docker daemon 応答性の切り分け（watchdog付き）
- `docker info`（20秒 watchdog）: **RC=1**、`Server: ERROR ... context canceled`
- `docker compose ... ps`（20秒 watchdog）: **RC=130**、応答なし（timeout kill）
- `curl --max-time 10 --unix-socket /Users/Hayato/.docker/run/docker.sock http://localhost/_ping`: **RC=28**（timeout）

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
- `server-modernized.production.env` が未配置。
- Docker daemon が socket 経由で応答しない（`/_ping`/`/version` が timeout、`docker info` は server 側 `context canceled`）ため `up/build/ps` が進行不能。
- 上記により、`health/readiness` 実測と主要業務疎通まで到達できない。

## 次回着手条件
1. Docker daemon の応答性を回復する（`curl --unix-socket /Users/Hayato/.docker/run/docker.sock http://localhost/_ping` が `OK` を返す状態）。
2. `server-modernized.production.env` に実運用値（DB/S3/FIDO2/秘密情報）を反映する。
3. `docker compose ... up -d --build --force-recreate` を完了させ、`/openDolphin/resources/health` と `/health/readiness` を実測する。
4. `P10-05` チェックリストに沿って本番切替を実施し、切替記録（当日ログ、疎通結果、引継ぎメモ）を本書へ追記する。

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
