# P2-05 LegacyTouch 抽象層削除（RUN_ID: 20260311T050453Z）

## 実施内容
- `server-modernized/src/main/java/open/dolphin/shared/legacytouch/LegacyTouchAbstractResource.java` を削除した。

## 参照確認
- `rg -n "LegacyTouchAbstractResource|open.dolphin.shared.legacytouch"` により、削除対象の外部参照がないことを確認した。

## 影響
- Touch 側土台クラスの残置がなくなり、旧 Touch 実装の再導入経路を遮断した。
- 次工程 `P2-06`（XML専用エンドポイント削除）に向け、旧互換層の独立削除を完了した。

## 検証
- `mvn -f server-modernized/pom.xml -DskipTests test-compile`
- `mvn -f server-modernized/pom.xml -Dtest=WebXmlEndpointExposureTest -Dsurefire.failIfNoSpecifiedTests=false test`
