# P2-07 common/converter 群削除（RUN_ID: 20260311T061511Z）

## 実施概要
- `common/src/main/java/open/dolphin/converter/**`（73ファイル）を `server-modernized/src/main/java/open/dolphin/converter/**` へ移設し、`common` モジュールから converter 群を除去した。
- これにより `common` は旧 XML/plist 契約用 converter 群を持たない構成になり、`P2-07` の完了条件（common converter 配下の整理）を満たした。

## 変更ファイル（代表）
- 削除（common）: `common/src/main/java/open/dolphin/converter/**`
- 追加（server-modernized）: `server-modernized/src/main/java/open/dolphin/converter/**`

## 検証
- `mvn -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile` : PASS

## 補足（次工程）
- `P3-04` / `P3-05` で API DTO + mapper 層へ明示移行し、`server-modernized` 内に残る旧 converter 実装を段階的に縮退する。
- `P3-07` で `ConverterModelReferences` などの参照維持補助を最終整理する。
