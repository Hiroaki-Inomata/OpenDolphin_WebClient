# memory

- RUN_ID: 20260312T234207Z
- 実行開始: 2026-03-13
- ブランチ: work/server-modernization-20260312T234207Z
- 対象WBS: `P10-06`（本番切替を実施する）

## 実施内容
- WBS 先頭未着手タスクとして `P10-06` を継続選定。
- Docker daemon 復旧を確認し、RUN 専用 compose project `opendolphin_prodcutover_20260312t234207z` で validation 環境を再構成。
- Flyway/runtime migration の `search_path` 補正、`AuditTrailService` の native SQL 化、`InitialAccountMaker` の `search_path` 設定、`AuditEvent` の persistence 登録、`LoginAttemptPolicyService` の `REQUIRES_NEW` 化、seed password の PBKDF2 化を実装。
- host build の WAR を RUN 専用 server container へ直接配備し、起動ログと login 導線を反復確認。
- WBS / `docs/DEVELOPMENT_STATUS.md` / `docs/modernization/p10-06-cutover-execution-blocker.md` を更新。

## 判断理由
- `P10-07` は `P10-06` 依存のため、先頭未着手としても着手不可。
- Docker daemon 不通は解消したため、今回の主眼は起動後の authenticated 疎通まで前進させることに置いた。
- `GET /openDolphin/` と WAR 起動自体は安定し、health/auth 周辺の 500 系は除去できたが、session login が 401 のまま残るため `P10-06` 完了条件には届かない。

## 実行した検証
- `mvn -f pom.server-modernized.xml -pl server-modernized -am -Dtest=AuditTrailServiceTest,InitialAccountMakerTest,UserServiceBeanPasswordTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 結果: PASS
- `mvn -f pom.server-modernized.xml -pl server-modernized -am -DskipTests package`
  - 結果: PASS
- `GET http://localhost:29080/openDolphin/`
  - 結果: 200、CSRF token と `JSESSIONID` を取得
- `POST http://localhost:29080/openDolphin/resources/api/session/login`
  - 条件: cookie + `X-CSRF-Token` + `Origin`
  - 結果: 403/500 は解消したが、sysad/doctor とも最終的に 401 unauthorized
- `docker logs opendolphin-server-modernized-20260312t234207z`
  - 結果: startup error は解消、login 実行時の authenticated 到達は未確認

## 所要時間の目安
- この実行内での `P10-06` 継続試行（実装・再配備・切り分け・記録含む）: 約 30 分

## 次に着手すべきタスク
- `P10-06`（同一タスク継続）
  - 次回は session login 401 の根因を優先して解消する。`SessionAuthResource` から `UserServiceBean.authenticateWithPolicy()` へ到達している前提で、EJB/JPA 実ランタイムでどの failure branch に落ちるかを直接観測できる手段を確立すること。
- `P10-07` は `P10-06` 完了まで着手不可。
