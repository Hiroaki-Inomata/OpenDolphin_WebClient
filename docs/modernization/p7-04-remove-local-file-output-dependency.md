# P7-04 ローカルファイル出力依存の除去（RUN_ID: 20260311T220125Z）

## 目的
- `PVTServiceBean` 内のノードローカルファイル出力（`custom.properties` + CSV書き出し）依存を除去する。
- PVT登録処理を DB 更新とイベント通知に限定し、実行環境ローカルパス前提をなくす。

## 変更内容
- `server-modernized/src/main/java/open/dolphin/session/PVTServiceBean.java`
  - `custom.properties` のローカル読込処理を削除。
  - `csv.output` 条件分岐、CSV line 生成、`csv.dir` 配下ファイル書き込み、権限変更処理を削除。
  - 関連の定数・補助メソッド・import を整理。

## 影響
- PVT登録時にローカルファイル（`.inp` / `csv.file.ext`）は生成されない。
- 今後の出力要件は DB / オブジェクトストレージ / 集中ログに集約する前提に統一。

## 検証
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile`
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -Dtest=PVTServiceBeanClinicalTest,PVTServiceBeanPaginationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - FAIL（Mockito inline self-attach 失敗）
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=PVTServiceBeanClinicalTest,PVTServiceBeanPaginationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - PASS（8 tests）
