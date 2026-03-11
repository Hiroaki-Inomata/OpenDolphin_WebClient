# P3-08 モジュール再編後ビルド構成

- 実施日: 2026-03-11
- RUN_ID: 20260311T080109Z
- WBS: `P3-08`

## 目的
- module 再編（`domain`/`api-contract`/`persistence`/`common`/`reporting`/`server-modernized`）後も、ローカルと CI が同一手順でビルド/テストを実行できる状態を固定する。

## 依存順（reactor）
1. `domain`
2. `api-contract`
3. `persistence`
4. `common`
5. `reporting`
6. `server-modernized`

`pom.server-modernized.xml` の `<modules>` は上記順を維持し、循環依存を避ける。

## 共通実行スクリプト
- 追加: `scripts/server-modernized/reactor-build.sh`
- モード:
  - `compile`: `test-compile` まで実行（reactor順 + test source compile）
  - `tests <pattern>`: 指定テスト群を `server-modernized` で実行（`-am` 付き）
- 追加引数:
  - `MAVEN_EXTRA_ARGS` 環境変数で `-DargLine=...` などを注入可能。

## 標準手順
- ローカル compile:
  - `./scripts/server-modernized/reactor-build.sh compile`
- ローカル性格確認テスト:
  - `./scripts/server-modernized/reactor-build.sh tests "UserResourceTest,SystemResourceTest"`
- JDK21 + byte-buddy fallback:
  - `MAVEN_EXTRA_ARGS="-DargLine=-javaagent:$HOME/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar" ./scripts/server-modernized/reactor-build.sh tests "<TESTS>"`

## CI 同期
- `.github/workflows/server-modernized-characterization.yml` の PR/夜間ジョブは、直接 `mvn` 実行を廃止し `reactor-build.sh tests` を呼ぶ。
- これにより「ローカルで再現したコマンド」と「CIで実行されるコマンド」を一致させる。
