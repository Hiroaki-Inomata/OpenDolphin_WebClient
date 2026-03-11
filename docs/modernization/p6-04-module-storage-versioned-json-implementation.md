# P6-04 Module 保存形式実装（versioned JSON 先行）

- 実施日: 2026-03-12
- RUN_ID: 20260311T200758Z
- 対象WBS: `P6-04`

## 実装概要
- `ModuleJsonConverter` に `medOrder` / `progressCourse` 向けの versioned envelope を追加した。
- 保存時は次の JSON 構造で `bean_json` に格納する。
  - `schemaVersion`（固定 `1`）
  - `moduleType`（`medOrder` または `progressCourse`）
  - `payloadJson`（既存 typed JSON）
  - `payloadHash`（`payloadJson` の SHA-256）
- 取得時は envelope を優先して復元し、非 envelope データは従来 typed JSON として復元する。

## 変更ファイル
- `persistence/src/main/java/open/dolphin/infomodel/ModuleJsonConverter.java`
  - `encode(ModuleModel)` / `decodeRaw(String)` を追加。
  - `decode(ModuleModel)` を envelope 対応に変更。
- `persistence/src/main/java/open/dolphin/infomodel/ModelUtils.java`
  - `encodeModule(ModuleModel)` / `decodeModuleJson(String)` を追加。
- `server-modernized/src/main/java/open/dolphin/session/KarteServiceBean.java`
- `server-modernized/src/main/java/open/dolphin/session/KarteDocumentWriteService.java`
- `server-modernized/src/main/java/open/dolphin/rest/orca/OrcaOrderBundleResource.java`
- `server-modernized/src/main/java/open/dolphin/rest/orca/OrcaSubjectiveResource.java`
- `server-modernized/src/main/java/open/orca/rest/OrcaResource.java`
  - module 書込経路を `ModelUtils.encodeModule(...)` に統一。

## 検証
- 実行コマンド:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl persistence,server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=ModuleJsonConverterTest,OrcaOrderBundleResourceTest,OrcaSubjectiveResourceTest,KarteServiceBeanDocPkTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 結果: PASS（24 tests）

## 後続（P6-08 / P6-09）
- 本タスクは「保存形式の実装（コード経路）」を先行完了とし、DB schema の新設・移行は `P6-08` / `P6-09` で実施する。
- `P6-08` で `d_module_payload` を導入する際は、今回の envelope 形式を migration 入力仕様として利用する。
