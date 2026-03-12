# P10-01 検証環境を本番に近い形で立てる

- 日付: 2026-03-12
- RUN_ID: 20260312T070152Z
- タスク: P10-01

## 実施内容
1. 本番近似の compose オーバーレイを追加。
- 追加: `docker-compose.modernized.validation.yml`
- 役割:
  - 検証用のコンテナ名/ポートを分離（既存 dev 環境と競合させない）
  - DB 接続を `sslmode=require` 既定へ変更
  - ORCA/添付/PHR/認証秘密情報を `:?` で必須化
  - OTLP を有効化し、運用監視に近い計測経路で起動

2. 検証環境用の env サンプルを追加。
- 追加: `ops/modernized-server/config/server-modernized.validation.env.sample`
- 役割:
  - 本番相当の必須キーを一覧化
  - リポジトリに秘密情報を置かず、ローカル env で注入する運用へ固定

3. 起動スクリプトを追加。
- 追加: `ops/modernized-server/scripts/start-validation-env.sh`
- 役割:
  - env ファイル存在チェック
  - compose 構文検証 (`docker compose config`)
  - 検証環境起動 (`up -d --build --force-recreate`)

## 構築手順（秘密情報を除いた手順）
1. サンプル env をコピー:
```bash
cp ops/modernized-server/config/server-modernized.validation.env.sample \
   ops/modernized-server/config/server-modernized.validation.env
```
2. `server-modernized.validation.env` の `replace_me_*` を実値へ置換。
3. 検証環境を起動:
```bash
ops/modernized-server/scripts/start-validation-env.sh
```
4. 起動後確認:
```bash
curl -sS http://localhost:19080/openDolphin/resources/health | jq .
curl -sS http://localhost:19080/openDolphin/resources/health/readiness | jq .
```

## 接続情報管理方針
- `server-modernized.validation.env` は Git 管理しない（ローカル保持）。
- ORCA 接続情報と各種秘密情報は env 由来で注入する。
- 共有が必要な値はマスク済みメタ情報（接続先URL、利用用途、更新日）のみ記録し、平文パスワードは記録しない。

## 検証
- `bash -n ops/modernized-server/scripts/start-validation-env.sh` PASS
- `docker compose --env-file ops/modernized-server/config/server-modernized.validation.env.sample -f docker-compose.modernized.dev.yml -f docker-compose.modernized.validation.yml config` PASS
