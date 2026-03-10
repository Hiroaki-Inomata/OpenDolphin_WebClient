# P1-08 添付画像/PDF 性格確認テスト判定表

- RUN_ID: 20260310T220429Z
- 対象: P1-08（添付画像・PDF の保存/取得挙動固定）
- 判定軸: 患者との紐付け、保存時の完全性（digest/uri）、取得時の内容不変（byte一致）

## 最小ケース
| ケース | 正常系テスト | 代表失敗系テスト |
|---|---|---|
| 画像保存 | `PatientImagesResourceTest#upload_acceptsValidPngAndNormalizesPayload` | `PatientImagesResourceTest#upload_rejectsBrokenImageEvenWithMagicHeader` |
| 画像取得 | `PatientImagesResourceTest#download_returnsNoStoreHeaders` | `PatientImagesResourceTest#download_returnsNotFoundWhenAttachmentDoesNotExist` |
| PDF保存（外部ストレージ） | `AttachmentStorageManagerTest#uploadToS3OutsideTransaction_handlesPdfPayload` | `AttachmentStorageManagerTest#populateBinary_rejectsAttachmentWithoutBytesAndUri` |
| PDF取得（外部ストレージ） | `AttachmentStorageManagerTest#populateBinary_downloadsPdfBytesWithoutMutation` | `PatientImagesResourceTest#download_returnsNotFoundWhenAttachmentDoesNotExist` |

## 追加した固定ルール
- PDFバイナリ保存時も `uri` と `digest(SHA-256)` を設定し、`contentBytes` を解放する。
- PDF取得時は保存バイト列が改変されないこと（アップロード時と同値）を確認する。
- ダウンロード応答は画像/PDFとも `Cache-Control: private, no-store` を維持する。

## 実行コマンド（P1-08）
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
  mvn -o -f pom.server-modernized.xml \
  -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar \
  -Dtest=AttachmentStorageManagerTest,PatientImagesResourceTest,PatientImageServiceBeanTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
