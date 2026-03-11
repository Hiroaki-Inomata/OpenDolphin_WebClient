# P2-04 Touch/ASP 削除（RUN_ID: 20260311T050147Z）

## 概要
- `server-modernized` から Touch 系実装（`open/dolphin/touch/**`）を削除した。
- あわせて Touch 専用の単体テスト群（`server-modernized/src/test/java/open/dolphin/touch/**`）を削除した。

## 影響一覧
- 削除対象（本体）: `server-modernized/src/main/java/open/dolphin/touch/**`（76ファイル）
- 削除対象（テスト）: `server-modernized/src/test/java/open/dolphin/touch/**`（6ファイル）
- 参照確認: `open.dolphin.touch` の外部参照は `WebXmlEndpointExposureTest` の文字列検証のみ（型参照なし）。

## 代替先
- 旧 Touch/ASP 入口（`/touch`）は廃止し、現行 API は `docs/modernization/api-v1-design.md` の `/api/v1/**` を利用する。
- 入口整理の全体判定は `docs/modernization/remove-matrix.md` を参照する。

## 検証
- `mvn -f server-modernized/pom.xml -DskipTests test-compile`
- `mvn -f server-modernized/pom.xml -Dtest=WebXmlEndpointExposureTest -Dsurefire.failIfNoSpecifiedTests=false test`
