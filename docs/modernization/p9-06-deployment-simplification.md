# P9-06 配備方式の簡素化

- 日付: 2026-03-12
- RUN_ID: 20260312T050136Z
- タスク: P9-06

## 方針決定
- server-modernized の配備方式は **WAR on WildFly** を唯一の標準方式とする。
- 実行手順は `setup-modernized-env.sh`（開発）と `start_wildfly_headless.sh`（最小起動）を中心に統一する。
- Legacy 比較用スクリプトは検証用途に限定し、通常の配備導線には含めない。

## 実施内容

### 1. 起動既定値の統一
- 対象: `setup-modernized-env.sh`
- 変更:
  - `WEB_CLIENT_MODE` の既定値を `docker` から `npm` へ変更。
- 意図:
  - 開発時の標準起動を「server-modernized(WildFly) + web-client(npm)」へ固定し、日常運用の分岐を減らす。

### 2. 配備方針の明文化
- 対象: 本ドキュメント
- 内容:
  - 採用方式（WAR + WildFly）
  - 標準導線（setup/headless）
  - 非標準導線（legacy比較系）の位置づけを明記。

## 標準手順（開発環境）
1. `WEB_CLIENT_MODE=npm ./setup-modernized-env.sh`
2. サーバー疎通確認: `http://localhost:9080/openDolphin/resources`
3. Web クライアント疎通確認: `http://localhost:5173`

## 検証
- `bash -n setup-modernized-env.sh` PASS
