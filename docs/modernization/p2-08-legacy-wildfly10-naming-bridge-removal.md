# P2-08 legacy-wildfly10 + naming bridge 削除（RUN_ID: 20260311T061511Z）

## 実施概要
- `common/pom.xml` から `legacy-wildfly10` プロファイルを削除し、旧 WildFly10 互換の `maven-shade-plugin` relocation 設定を廃止した。
- `server-modernized` の JNDI 利用箇所を `javax.naming` へ統一し、独自 `jakarta.naming` ブリッジ実装を削除した。

## 変更詳細
- 削除: `common/pom.xml` の `legacy-wildfly10` profile（`legacy-persistence` / `legacy-relocations` を含む）
- 変更:
  - `server-modernized/src/main/java/open/dolphin/metrics/MeterRegistryProducer.java`
  - `server-modernized/src/main/java/open/orca/rest/ORCAConnection.java`
  - 上記2ファイルの import を `javax.naming.InitialContext` / `javax.naming.NamingException` へ変更
- 削除:
  - `server-modernized/src/main/java/jakarta/naming/InitialContext.java`
  - `server-modernized/src/main/java/jakarta/naming/NamingException.java`

## 検証
- `mvn -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile` : PASS

## 補足
- `ops/legacy-server/**` と `scripts/start_legacy_modernized.sh` にある legacy server (`server/`) 向け `-Plegacy-wildfly10` は、Legacy サーバー運用経路のため今回対象外（`server/` 非改修ルールに従い不変更）。
