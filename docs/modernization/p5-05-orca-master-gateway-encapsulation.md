# P5-05 ORCA専用DAOのgateway内包化

- 作成日: 2026-03-11
- RUN_ID: 20260311T140116Z
- 対象WBS: `P5-05`（ORCA 専用 DAO を gateway の内側へ閉じ込める）

## 目的
`OrcaMasterResource` が `OrcaMasterDao` / `EtensuDao` を直接保持・呼び出していた構成を解消し、ORCA master 検索のDBアクセス責務を gateway 境界へ集約する。

## 実施内容
1. `OrcaMasterGateway` を追加
- 追加: `server-modernized/src/main/java/open/orca/rest/OrcaMasterGateway.java`
- ORCA master の検索用途（generic/drug/comment/bodypart/youhou/material/kensaSort/etensu）を gateway 契約として定義。

2. DAO実装を gateway 内へ移動
- 追加: `server-modernized/src/main/java/open/orca/rest/OrcaMasterDaoGateway.java`
- `OrcaMasterDao` と `EtensuDao` の保持と呼び出しをこの実装へ集約。

3. Resource のDAO直接依存を除去
- 変更: `server-modernized/src/main/java/open/orca/rest/OrcaMasterResource.java`
- `OrcaMasterResource` は `OrcaMasterGateway` のみを保持し、検索呼び出しを gateway 経由へ置換。
- 既存テスト互換のため `OrcaMasterResource(EtensuDao, OrcaMasterDao)` は残し、内部で `OrcaMasterDaoGateway` を組み立てる構成へ変更。

## 影響
- API契約（URI/レスポンス）は変更なし。
- ORCA master 検索における DAO 直接依存が `OrcaMasterResource` から消え、今後のDAO分割は gateway 実装内で完結できる。

## 検証
- `mvn -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile`
- `mvn -f pom.server-modernized.xml -pl server-modernized -am -Dtest=OrcaMasterResourceTest -Dsurefire.failIfNoSpecifiedTests=false test`
