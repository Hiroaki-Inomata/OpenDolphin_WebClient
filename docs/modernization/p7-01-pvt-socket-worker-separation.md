# P7-01 PvtService 生ソケット受信のワーカー分離（RUN_ID: 20260311T210122Z）

## 目的
- `PvtService` が直接 `ServerSocket` を保持していた構成を解消し、受信責務と業務処理責務を分離する。
- 受信ループ障害と業務処理障害の切り分けを容易にする。

## 実装概要
- 新規 worker を追加し、ソケット受信・接続処理を移管。
  - `server-modernized/src/main/java/open/dolphin/worker/pvt/PvtSocketWorker.java`
- `PvtService` はブートストラップ役へ縮退。
  - 役割: 設定読込、worker起動/停止、受信済み payload の domain 変換（`parseAndSend`）。
  - 非役割: `ServerSocket#accept`、接続スレッドプール管理、ACK/NAK 応答。

## 変更詳細
- `PvtSocketWorker`
  - bind/accept/read/ACK-NAK を一元実装。
  - `PayloadHandler` コールバックで業務処理を外部委譲。
  - `pvt-accept-*` / `pvt-connection-*` の命名スレッドを worker 内で生成。
- `PvtService`
  - `implements Runnable` と inner `Connection` を削除。
  - `@PostConstruct` で worker を生成して起動。
  - `@PreDestroy` で worker を停止。
  - `parseAndSend` は従来どおり `PVTBuilder` と `PVTServiceBean#addPvt` を利用し、業務処理互換を維持。

## 検証コマンドと結果
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile`
  - PASS
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=MessageSenderTest,SessionMessageHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - PASS（7 tests）

## 運用メモ
- `useAsPVTServer=false` の場合は従来どおり listener 非起動。
- worker は `ManagedThreadFactory` が利用可能ならそれを優先し、未提供時は `Executors.defaultThreadFactory()` を fallback とする。
