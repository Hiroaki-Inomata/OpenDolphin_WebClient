# P4-07 EJB前提削減 / CDI優先化（RUN_ID: 20260311T120154Z）

## 目的
- EJB固有アノテーションに業務ロジックが閉じる状態を減らし、CDI中心で実装可能な境界へ寄せる。
- 新規実装は CDI service を優先し、EJBはコンテナ境界（MDB等）に限定する。

## 現状棚卸し（server-modernized）
- EJB固有コンポーネント
  - `MessageSender`（`@MessageDriven`）
  - `META-INF/ejb-jar.xml`（resource-adapter 設定）
- 上記以外の新規サービスは `@ApplicationScoped` + `@Transactional` を基準に実装済み。

## 実施内容
### 1) MDBを薄い入口へ縮退
- `MessageSender` から envelope解析・PVT変換・facility解決ロジックを切り離し、受信入口の委譲専用クラスへ変更。
- 役割を「JMS受信 -> CDI handler呼び出し」に限定。

### 2) CDIハンドラへ業務処理を移管
- `SessionMessageHandler`（`@ApplicationScoped`）を新規作成。
- 既存のJMS payload処理ロジックを移設し、`PVTServiceBean` への業務委譲を CDI 管理下で実行。

### 3) テスト分離
- `MessageSenderTest`: MDB入口の委譲責務のみ検証。
- `SessionMessageHandlerTest`: envelope解析・不正payload拒否の既存振る舞いを検証。

## 完了条件との対応
- 「新規コードが軽い依存で書ける」: JMSコンテナ依存を `MessageSender` に限定し、処理本体は CDI bean で単体検証可能。
- 「設計方針/置換済み service 一覧」: 本書で固定。

## 置換済み service 一覧
- `SessionMessageHandler`（新規, CDI）
- `MessageSender`（MDB入口, ロジックは委譲）

## 検証
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=MessageSenderTest,SessionMessageHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`
