# P6-07 persistence.xml 手書き class list 最小化

- 実施日: 2026-03-12
- RUN_ID: 20260311T210122Z
- 対象WBS: `P6-07`

## 目的
- `persistence.xml` の手書き class list を現行モジュール構成に合わせて最小化し、
  class登録漏れ/削除漏れの運用事故を防ぐ。

## 変更内容
- 変更: `server-modernized/src/main/resources/META-INF/persistence.xml`
  - `<class>...</class>` で列挙していた 33 エントリを削除。
  - `exclude-unlisted-classes=false` と `hibernate.archive.autodetection=class,hbm` により
    アノテーションベースの自動検出へ一本化。

## 判断
- これまでの手書き列挙は、entity配置の変更時に更新漏れが発生しやすく、
  `P3` 以降のモジュール再編と相性が悪い。
- 現行は persistence / server-modernized 複数モジュールに entity が分散するため、
  手書き一覧より自動検出の方が保守コストと事故率の両面で有利。

## 検証
- PASS: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile`
- PASS: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=AdminAccessResourceTest,AdminOrcaUserResourceTest -Dsurefire.failIfNoSpecifiedTests=false test`（11 tests）
- FAIL(環境制約): `... -Dtest=FreshSchemaBaselineTest,AdminAccessResourceTest,AdminOrcaUserResourceTest ...`
  - `FreshSchemaBaselineTest` が embedded postgres 起動時に `java.net.SocketException: Operation not permitted`（sandbox のソケット bind 制約）

## 次段（P6-08 への引き継ぎ）
- migration 追加時は `persistence.xml` の class list更新を不要とし、entity 追加は annotation のみで反映する。
