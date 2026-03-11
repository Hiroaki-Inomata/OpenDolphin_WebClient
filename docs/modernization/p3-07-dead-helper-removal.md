# P3-07 ダミー参照・死蔵補助コード整理

- 実施日: 2026-03-11
- RUN_ID: 20260311T080109Z
- WBS: `P3-07`

## 実施内容
- `server-modernized/src/main/java/open/dolphin/converter/ConverterModelReferences.java` を削除。
- 以下 converter で `ConverterModelReferences` 依存を廃止し、各クラス内の最小参照生成（`id` のみ保持）へ置換。
  - `AppointmentModelConverter`
  - `AttachmentModelConverter`
  - `ObservationModelConverter`
  - `PatientMemoModelConverter`
  - `SchemaModelConverter`
  - `LetterModuleConverter`
  - `RegisteredDiagnosisModelConverter`

## 意図
- 「参照維持専用のダミー集約クラス」を除去し、不要な補助依存をなくして converter ごとの責務を明確化。
- 変換時に必要な最小参照（`KarteBean#id`, `UserModel#id`）は維持し、JSON 契約の互換を崩さない。

## 検証
- `mvn -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile` : PASS
