# P3-02 新 module 雛形作成（RUN_ID: 20260311T061511Z）

## 実施概要
- `pom.server-modernized.xml` に `domain` / `api-contract` module を追加した。
- 各 module の `pom.xml` を新規作成し、Java 17 で最小コンパイルできる構成を用意した。
- 各 module に marker class を配置し、空 module のビルド確認を可能にした。

## 追加ファイル
- `domain/pom.xml`
- `domain/src/main/java/open/dolphin/domain/DomainMarker.java`
- `api-contract/pom.xml`
- `api-contract/src/main/java/open/dolphin/apicontract/ApiContractMarker.java`

## 変更ファイル
- `pom.server-modernized.xml`（module 定義 / dependencyManagement へ新 module を追加）

## 検証
- `mvn -f pom.server-modernized.xml -pl domain,api-contract,server-modernized -am -DskipTests test-compile` : PASS
