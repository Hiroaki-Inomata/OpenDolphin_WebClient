# P9-05 静的解析必須ゲート

- 日付: 2026-03-12
- RUN_ID: 20260312T050136Z
- タスク: P9-05

## 目的
SpotBugs / Checkstyle / PMD を PR 時の必須チェックに組み込み、静的解析を「実行されるだけ」から「ゲートとして判定される」状態へ移行する。

## 実施内容

### 1. 解析プロファイルに強制フラグを導入
- 対象:
  - `server-modernized/pom.xml`
  - `common/pom.xml`
- 変更:
  - `static.analysis.enforce` プロパティを追加（既定 `false`）。
  - `spotbugs/checkstyle/pmd` の `failOn*` を `static.analysis.enforce` へ接続。
- 目的:
  - ローカルでは既定非強制（既存負債調査を継続可能）。
  - CI では `-Dstatic.analysis.enforce=true` を指定し、必須ゲートとして動作。

### 2. 解析設定パスの安定化
- 対象:
  - `server-modernized/pom.xml`
  - `common/pom.xml`
- 変更:
  - `static.analysis.config.dir` / `static.analysis.output.dir` を `${maven.multiModuleProjectDirectory}` 基準へ統一。
- 効果:
  - モジュール実行位置依存を減らし、CI とローカルで同一出力先を利用。

### 3. CI ワークフローを追加
- 対象: `.github/workflows/server-modernized-static-analysis-gate.yml`
- 仕様:
  - trigger: `pull_request`（`server-modernized/**`, `common/**`, `pom.server-modernized.xml` 変更時） + `workflow_dispatch`
  - 実行コマンド:
    - `mvn -B -ntp -f pom.server-modernized.xml -Pstatic-analysis -Dstatic.analysis.enforce=true -DskipTests -pl common,server-modernized -am verify`
  - 解析レポートを artifact へアップロード。

## 品質基準（この時点の運用）
- 新規/変更 PR は static analysis gate を通過すること。
- 既存コード負債は `config/static-analysis/*` の除外/ルールで管理し、必要時のみ明示更新する。

## 検証
- 実行:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl common -am -Pstatic-analysis -Dstatic.analysis.enforce=false -DskipTests verify`
- 結果: PASS（SpotBugs/Checkstyle/PMD 実行・レポート出力確認）
