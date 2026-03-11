# P3-03 JPA Entity 分離（common -> persistence）

- 実施日: 2026-03-11
- RUN_ID: 20260311T070119Z
- WBS: `P3-03`

## 実施内容
- `common/src/main/java/open/dolphin/infomodel/**` を `persistence/src/main/java/open/dolphin/infomodel/**` へ移設。
- `common/src/test/java/open/dolphin/infomodel/**` を `persistence/src/test/java/open/dolphin/infomodel/**` へ移設。
- `opendolphin-persistence` モジュールを新規作成し、`infomodel` を永続化モジュールへ集約。
- `pom.server-modernized.xml` と `pom.xml` に `persistence` モジュールを追加。
- `pom.server-modernized.xml` と `pom.xml` の dependencyManagement に `opendolphin-persistence` を追加。
- `common/pom.xml` に `opendolphin-persistence` 依存を追加（`common` は `infomodel` 実装を直接持たない）。

## 影響
- `open.dolphin.infomodel.*` の実装配置は `persistence` モジュールに一意化された。
- `common` から `infomodel` 実体が除去され、`shared jar` 直下の配置を解消。
- 既存参照の FQCN は維持（`open.dolphin.infomodel.*`）したため、呼び出し側の import 変更は不要。

## 検証
- `mvn -f pom.server-modernized.xml -pl persistence,common,server-modernized -am -DskipTests test-compile`
  - 結果: PASS（BUILD SUCCESS）

## 補足
- 本タスクでは entity 分離を優先し、API層の entity 露出除去は後続 `P3-04` / `P3-05` で実施する。
