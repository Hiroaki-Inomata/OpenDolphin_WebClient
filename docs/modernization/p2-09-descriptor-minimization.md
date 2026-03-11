# P2-09 descriptor 最小化（RUN_ID: 20260311T061511Z）

## 実施概要
- `WEB-INF/web.xml` の servlet 個別 `resteasy.scan=false` を削除し、既存のグローバル context-param (`resteasy.scan=false`) に一本化した。
- `WEB-INF/jboss-deployment-structure.xml` から、現行構成で不要な `com.fasterxml.jackson.jaxrs.jackson-jaxrs-json-provider` の exclusion を削除した。
- `META-INF/ejb-jar.xml` と `WEB-INF/jboss-web.xml` は最小構成を維持していることを確認し、変更なしとした。

## 変更ファイル
- `server-modernized/src/main/webapp/WEB-INF/web.xml`
- `server-modernized/src/main/webapp/WEB-INF/jboss-deployment-structure.xml`

## 検証
- `mvn -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile` : PASS
- `mvn -f pom.server-modernized.xml -pl server-modernized -am -Dtest=WebXmlEndpointExposureTest -Dsurefire.failIfNoSpecifiedTests=false test` : PASS
