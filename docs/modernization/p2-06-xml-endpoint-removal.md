# P2-06 XML専用エンドポイント削除（RUN_ID: 20260311T060146Z）

## 実施概要
- XML 専用公開入口を削除し、公開契約を JSON に統一した。
- `web-client` からの旧 XML POST は既存の `/api/v1/orca/bridge` 経路で継続処理する。

## 削除したXML専用入口
- `open.dolphin.rest.OrcaAcceptanceListResource`
- `open.dolphin.rest.OrcaSystemManagementResource`
- `open.dolphin.rest.OrcaReportResource`
- `open.dolphin.rest.OrcaDiseaseApiResource`
- `open.dolphin.rest.OrcaMedicalApiResource`
- `open.dolphin.rest.OrcaAdditionalApiResource`
- `open.dolphin.rest.KarteResource#getImages` (`GET /karte/iamges/{param}`)

## 削除した補助実装
- `open.dolphin.rest.legacy.LegacyImageXmlWriter`
- `open.dolphin.rest.support.LegacyImageResponseMapper`

## 維持した入口（JSON契約）
- `OrcaPatientApiResource` の `patientgetv2` GET 系のみ維持。
- `patientgetv2` は `format=json` のみ許可（非JSON指定は `400`）。

## 代替先
- ORCA XML2 依存の既存web-client呼び出しは `web-client/src/libs/http/httpClient.ts` の自動転送で
  `POST /api/v1/orca/bridge` に集約。
- 患者/カルテ/添付/管理の業務APIは `docs/modernization/api-v1-design.md` で定義した `/api/v1/**` 系へ順次移行する。

## 影響反映
- `server-modernized/src/main/webapp/WEB-INF/web.xml` から削除済みResource登録を除去。
- `WebXmlEndpointExposureTest` に削除済みResourceの非露出検証を追加。
- CIの性格確認テストセットから削除済みテストを外し、`WebXmlEndpointExposureTest` を追加。

## 検証
- `mvn -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile`
- `mvn -f pom.server-modernized.xml -pl server-modernized -am -Dtest=OrcaPatientApiResourceRunIdTest,WebXmlEndpointExposureTest -Dsurefire.failIfNoSpecifiedTests=false test`
