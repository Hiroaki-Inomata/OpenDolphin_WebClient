# P4-02 KarteServiceBean use case 分割（RUN_ID: 20260311T100117Z）

## 実施概要
- `KarteServiceBean` のうち、変更頻度が高く副作用の大きい write 系 use case を専用 service へ切り出した。
- 分割対象は以下の3系統。
  - 文書 write: `KarteDocumentWriteService`
  - 傷病名 write/read: `KarteDiagnosisService`
  - 観察 write/read: `KarteObservationService`
- `KarteServiceBean` 側は public API シグネチャを維持し、各 use case service への委譲に変更した。

## 追加した service
- `server-modernized/src/main/java/open/dolphin/session/KarteDocumentWriteService.java`
  - `addDocument` / `updateDocument` / `addDocumentAndUpdatePVTState` / `deleteDocument` / `updateTitle`
  - 文書グラフ同期、revision bulk update、外部アセット保存、完全性 seal を集約。
- `server-modernized/src/main/java/open/dolphin/session/KarteDiagnosisService.java`
  - `getDiagnosis` / `postPutSendDiagnosis` / `addDiagnosis` / `updateDiagnosis` / `removeDiagnosis`
  - batch flush/clear と監査記録を集約。
- `server-modernized/src/main/java/open/dolphin/session/KarteObservationService.java`
  - `getObservations` / `addObservations` / `updateObservations` / `removeObservations`
  - query 分岐と batch write を集約。

## 互換性
- REST 層・呼び出し側の公開メソッド契約は維持（`KarteServiceBean` のシグネチャ変更なし）。
- 既存のユニットテストは、CDI を使わない直接 new パターンで失敗しないよう service 注入を明示化した。

## テスト
- `mvn -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile` PASS
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=KarteServiceBeanDocPkTest,KarteServiceBeanRevisionBulkUpdateTest,KarteServiceBeanBatchWriteTest,KarteServiceBeanGetKarteTest,DocumentIntegrityServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` PASS
