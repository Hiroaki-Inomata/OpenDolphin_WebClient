# P8-05 user.home / build 依存除去

- 更新日: 2026-03-12
- RUN_ID: 20260312T040136Z
- 対象: `server-modernized`

## 背景
- `PushEventDeduplicator` が既定で `~/.opendolphin/orca/pushevent-cache.json` を使用しており、`user.home` 依存だった。
- `RuntimeConfigurationSupport` の設定ディレクトリ既定値が `/opt/jboss/config` 固定で、実行環境依存が強かった。

## 変更内容
1. PushEvent cache の既定保存先を `jboss.server.data.dir` 配下へ変更。
- `server-modernized/src/main/java/open/dolphin/orca/support/PushEventDeduplicator.java`
- `ORCA_PUSH_EVENT_CACHE_PATH` 未指定時:
  - `-Djboss.server.data.dir` 指定あり: `${jboss.server.data.dir}/orca/pushevent-cache.json`
  - 指定なし: `${cwd}/runtime-state/orca/pushevent-cache.json`

2. 設定ディレクトリ解決を固定パスからランタイム解決へ変更。
- `server-modernized/src/main/java/open/dolphin/runtime/RuntimeConfigurationSupport.java`
- 優先順:
  - `-Dopendolphin.config.dir`
  - `OPENDOLPHIN_CONFIG_DIR`
  - `-Djboss.server.config.dir`
  - `-Djboss.home.dir/standalone/configuration`
  - `-Djboss.server.data.dir/config`
  - `${cwd}/config`

3. テスト追加。
- `server-modernized/src/test/java/open/dolphin/orca/support/PushEventDeduplicatorTest.java`
- `server-modernized/src/test/java/open/dolphin/runtime/RuntimeConfigurationSupportTest.java`

## 検証
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile` PASS
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -Dtest=PushEventDeduplicatorTest,RuntimeConfigurationSupportTest -Dsurefire.failIfNoSpecifiedTests=false test` PASS

## 運用補足
- push event cache の保存先を明示したい場合は、従来どおり `ORCA_PUSH_EVENT_CACHE_PATH` を設定する。
