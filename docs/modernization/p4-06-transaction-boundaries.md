# P4-06 トランザクション境界見直し（RUN_ID: 20260311T120154Z）

## 目的
- 外部I/O（S3削除・クライアント通知）を更新トランザクション内から外し、DB更新単位を短く保つ。
- 対象は WBS 指示どおり `患者更新` `カルテ保存` `添付保存` の3系統。

## 実施内容
### 患者更新（PatientServiceBean）
- `updateForFacility` 経由の `updatePvtList` で即時実行していた `ChartEventServiceBean#notifyEvent` を、トランザクションコミット後に遅延実行する方式へ変更。
- `TransactionSynchronizationRegistry` を利用し、`STATUS_COMMITTED` のときのみ通知を送る。
- トランザクション外（または同期レジストリ未利用時）は即時通知のままフォールバックする。

### カルテ保存/更新（KarteDocumentWriteService）
- 文書削除・差分更新時の添付削除呼び出しを `deleteExternalAsset` から `scheduleDeleteExternalAssetAfterCommit` へ変更。
- これにより DB の削除処理と外部S3削除を分離し、ロールバック時の不整合を抑止。

### 添付保存（AttachmentStorageManager）
- `scheduleDeleteExternalAssetAfterCommit` を追加。
  - トランザクション有効時: `registerInterposedSynchronization` で commit 後のみ削除。
  - トランザクション外: 即時削除。
- `deleteExternalAssetOutsideTransaction`（`TxType.NOT_SUPPORTED`）を追加し、S3削除をトランザクション境界外で実行。
- 既存の upload 側 `uploadToS3OutsideTransaction` と同じ方針に統一。

## 検証
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=AttachmentStorageManagerTest,PatientServiceBeanAddPatientTest,KarteServiceBeanDocPkTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 結果: PASS（17 tests）

## ワーカー向け: 専用worktree作成手順（再発防止込み）
1. `RUN_ID` を採番する（UTC推奨）。
   - 例: `date -u +%Y%m%dT%H%M%SZ`
2. `master` 基点でタスク専用ブランチを切る。
   - 例: `task/p4-06-<RUN_ID>`
3. **worktreeは sandbox の writable root 配下に作成する。**
   - 本リポジトリでは `OpenDolphin_WebClient` 配下に作る。
   - 例: `git worktree add .task-worktrees/p4-06-<RUN_ID> -b task/p4-06-<RUN_ID> master`
4. 作業は作成した worktree 配下で完結させる。
5. 完了後、作業worktreeで commit し、親worktreeの `master` に merge する。
6. `git worktree remove .task-worktrees/p4-06-<RUN_ID>` で削除し、作業ブランチも削除する。

## 影響ファイル
- `server-modernized/src/main/java/open/dolphin/session/PatientServiceBean.java`
- `server-modernized/src/main/java/open/dolphin/session/KarteDocumentWriteService.java`
- `server-modernized/src/main/java/open/dolphin/storage/attachment/AttachmentStorageManager.java`
- `server-modernized/src/test/java/open/dolphin/storage/attachment/AttachmentStorageManagerTest.java`
