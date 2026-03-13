# memory

- RUN_ID: 20260313T045422Z
- 実行開始: 2026-03-13
- ブランチ: `master`（新規 branch 作成は `.git/refs/heads/*.lock` の permission error で失敗）
- 対象: validation 環境の `session/login` 401 解消

## 実施内容
- 稼働中 container `opendolphin-server-modernized-20260312t234207z` のログを再確認し、`session/login` 401 の実根因を切り分けた。
- `UserServiceBean.authenticateWithPolicy()` 実行時に `UnknownEntityException: Could not resolve root entity 'UserModel'` が発生していることを確認した。
- `server-modernized/src/main/resources/META-INF/persistence.xml` に `opendolphin-persistence` / `DocumentIntegrityEntity` の Entity 一覧を明示登録する形へ戻した。
- `server-modernized/src/test/java/open/dolphin/PersistenceXmlEntityRegistrationTest.java` を追加し、`@Entity` 付きクラス集合と `persistence.xml` の `<class>` 登録が一致することを検証する回帰テストを整備した。
- `mvn ... test` / `mvn ... package` 後、生成した `server-modernized/target/opendolphin-server.war` を running container へ `docker cp` で差し替え、`.dodeploy` で再配備した。
- 再配備後に doctor アカウントで login 成功、および認証後 `health` / `health/readiness` 200 を確認した。
- `docs/DEVELOPMENT_STATUS.md` と `docs/modernization/p10-06-cutover-execution-blocker.md` を更新した。

## 判断理由
- 401 は credential 不明や ORCA 認証連動ではなく、認証前段の JPQL 自体が persistence unit 不備で失敗していた。
- テスト環境では依存クラスパス上で通るが、WildFly 実ランタイムでは dependency JAR 内 Entity の自動検出が不安定だったため、明示登録へ戻す方が実害に対して確実だった。
- `exclude-unlisted-classes` は explicit registration 前提で `true` を維持し、列挙漏れは回帰テストで検知する方針にした。

## 実行した検証
- `mvn -f pom.server-modernized.xml -pl server-modernized -am -Dtest=PersistenceXmlEntityRegistrationTest,UserServiceBeanPasswordTest,SessionAuthResourceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 結果: PASS
- `mvn -f pom.server-modernized.xml -pl server-modernized -am -DskipTests package`
  - 結果: PASS
- `docker logs opendolphin-server-modernized-20260312t234207z`
  - 結果: 修正前の `UnknownEntityException: Could not resolve root entity 'UserModel'` を確認し、修正後は `POST /api/session/login status=200` を確認
- `curl http://localhost:29080/openDolphin/` → CSRF token 取得
  - 結果: PASS
- `POST http://localhost:29080/openDolphin/resources/api/session/login`
  - 条件: cookie + `X-CSRF-Token` + `Origin`
  - 結果: PASS（200）
- `GET http://localhost:29080/openDolphin/resources/health`
  - 条件: login 後 cookie
  - 結果: PASS（200）
- `GET http://localhost:29080/openDolphin/resources/health/readiness`
  - 条件: login 後 cookie
  - 結果: PASS（200）

## 所要時間の目安
- 調査・修正・再配備・疎通確認・記録: 約 35 分

## 次に着手すべきタスク
- `P10-06` の残りの切替確認を継続する。
  - 認証ブロッカーは解消したため、次は本番切替観点で残る authenticated endpoint / 主要フローの validation を進める。
- Git 書き込み制約の有無を再確認する。
  - 今回は branch 作成が `.lock` 作成権限エラーで失敗した。コミット可否も必要なら早めに切り分ける。
