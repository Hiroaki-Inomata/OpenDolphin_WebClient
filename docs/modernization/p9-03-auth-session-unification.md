# P9-03 認証方式のセッション統一

- 日付: 2026-03-12
- RUN_ID: 20260312T050136Z
- タスク: P9-03

## 方針決定
- 認証方式は **SessionAuthResource が設定する HttpSession 属性 (`AUTH_ACTOR_ID`) を唯一の正規経路** とする。
- `LogFilter` での `SecurityContext` principal fallback は廃止し、container principal 依存を除去する。
- 権限制御は既存の `request.getRemoteUser()`（`facility:user` composite 前提）を維持し、CSRF は既存 `CsrfProtectionFilter` の運用を継続する。

## 実装内容

### 1. 認証経路の一本化
- 対象: `server-modernized/src/main/java/open/dolphin/rest/LogFilter.java`
- 変更:
  - `SecurityContext` 注入と `resolvePrincipalUser()` を削除。
  - 認証ユーザー解決は `resolveSessionUser()`（セッション属性）に限定。
  - 未認証時は従来どおり `401 unauthorized` と監査ログを記録。

### 2. テスト更新
- 対象: `server-modernized/src/test/java/open/dolphin/rest/LogFilterTest.java`
- 変更:
  - SecurityContext モック依存ケースを HttpSession (`AUTH_ACTOR_ID`) 前提に更新。
  - principal 優先/非優先の曖昧ケースを削除し、セッションベースの remote user 伝播を検証。

## 検証
- 実行:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=LogFilterTest,SessionAuthResourceTest,LogoutResourceTest,CsrfProtectionFilterTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 結果: PASS（33 tests）

## 権限表現（今回の確定）
- 認証済み判定: `HttpSession` に `AUTH_ACTOR_ID` があり、`facility:user` composite 形式であること。
- 管理者判定: `requireAdmin(...)` / `AdminResourceSupport` により `UserServiceBean#isAdmin` で評価。
- 匿名許可: `/api/session/login` / `/api/session/login/factor2` / `/api/logout` のみ。
