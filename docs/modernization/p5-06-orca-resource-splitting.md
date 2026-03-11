# P5-06 ORCA Resource 機能別分割

- 実施日: 2026-03-12
- RUN_ID: 20260311T150117Z
- 対象: `open.orca.rest.OrcaResource`, `WEB-INF/web.xml`

## 実施内容
- `OrcaResource` から患者系・受付系 endpoint の JAX-RS 注釈を除去。
  - `disease/name`, `disease/import`, `disease/active`
  - `facilitycode`, `deptinfo`
- 新規 Resource を追加し、上記 endpoint を機能別に分離。
  - `open.orca.rest.OrcaPatientDiseaseResource`
  - `open.orca.rest.OrcaFacilityResource`
- endpoint 実装ロジックは `OrcaResource` の既存メソッドへ委譲し、契約（path/query）を維持。
- `web.xml` の `resteasy.resources` へ新規 Resource 2件を追加。
- `WebXmlEndpointExposureTest` に新規 Resource の公開確認を追加。

## 変更ファイル
- `server-modernized/src/main/java/open/orca/rest/OrcaResource.java`
- `server-modernized/src/main/java/open/orca/rest/OrcaPatientDiseaseResource.java`
- `server-modernized/src/main/java/open/orca/rest/OrcaFacilityResource.java`
- `server-modernized/src/main/webapp/WEB-INF/web.xml`
- `server-modernized/src/test/java/open/dolphin/rest/WebXmlEndpointExposureTest.java`

## 検証
- `mvn -f pom.server-modernized.xml -pl server-modernized -am -Dtest=WebXmlEndpointExposureTest,OrcaMasterResourceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - FAIL（sandbox 制約: `~/.m2` への追記不可）
- `mvn -o -f server-modernized/pom.xml -Dtest=WebXmlEndpointExposureTest,OrcaMasterResourceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - FAIL（offline + 未キャッシュ依存）

## 補足
- 本run環境では外部取得とローカル Maven キャッシュ更新が制限されるため、実行可能な範囲はコード差分と静的整合性確認まで。
