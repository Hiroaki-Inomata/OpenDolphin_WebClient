# P7-02 MessageSender JMS 消費責務の整理（RUN_ID: 20260311T210122Z）

## 目的
- JMS受信後の処理を「同期で必須な処理」と「後続へ回す処理」に明確分離する。
- `MessageSender` は入口のみ、`SessionMessageHandler` は処理段階を明示する構成へ揃える。

## 処理段階（今回の確定）
- Stage 1（同期）: JMS `Message` から envelope を読み取り、型を分類する。
- Stage 2（同期）: `PVT_XML` は即時に parse + `PVTServiceBean#addPvt` を実行する。
- Stage 3（後続）: `AUDIT_EVENT` は managed executor へ委譲し、JMS消費スレッドを占有しない。

## 実装内容
- 対象: `server-modernized/src/main/java/open/dolphin/session/SessionMessageHandler.java`
- 変更:
  - `readEnvelope` を新設し、Stage 1 を明示。
  - `dispatchAuditEvent` を新設し、`AUDIT_EVENT` を `ManagedExecutorService` へ deferred 実行。
  - executor が拒否した場合は inline 実行へ fallback する防御を追加。
  - クラスJavadocに Stage 1/2/3 の責務を明記。

## 互換性
- `MessageSender` は従来どおり CDI handler 委譲のみを維持。
- `PVT_XML` の業務処理順序（parse→facility補完→`addPvt`）は変更なし。

## 検証コマンドと結果
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile`
  - PASS
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=MessageSenderTest,SessionMessageHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - PASS（7 tests）
