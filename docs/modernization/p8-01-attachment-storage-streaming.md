# P8-01 AttachmentStorageManager ストリーミング化

- 実施日: 2026-03-12
- RUN_ID: 20260312T000147Z
- WBS: `P8-01`

## 変更概要
- `AttachmentStorageManager` にストリーム経路を追加し、S3 ダウンロードを `writeBinaryTo(...)` で逐次転送できるようにした。
- `AttachmentStorageManager#populateBinary` は互換 API として残し、内部では `writeBinaryTo(...)` で取得した内容を `byte[]` に詰め直す構成へ変更した。
- S3 アップロードに `uploadToS3OutsideTransaction(AttachmentModel, InputStream, long)` を追加し、入力ストリームを直接受け取れるようにした。
- `PatientImagesResource#download` を `StreamingOutput` 応答へ切り替え、添付ダウンロードで全量 `byte[]` を事前展開しないようにした。
- `PatientImageServiceBean#getImageForDownload` の download 前 `populateBinary` 呼び出しを外し、resource 側のストリーム転送へ責務を寄せた。

## 変更ファイル
- `server-modernized/src/main/java/open/dolphin/storage/attachment/AttachmentStorageManager.java`
- `server-modernized/src/main/java/open/dolphin/rest/PatientImagesResource.java`
- `server-modernized/src/main/java/open/dolphin/session/PatientImageServiceBean.java`
- `server-modernized/src/test/java/open/dolphin/storage/attachment/AttachmentStorageManagerTest.java`
- `server-modernized/src/test/java/open/dolphin/rest/PatientImagesResourceTest.java`

## 検証
1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile`
   - PASS
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=AttachmentStorageManagerTest,PatientImagesResourceTest -Dsurefire.failIfNoSpecifiedTests=false test`
   - PASS (20 tests)
