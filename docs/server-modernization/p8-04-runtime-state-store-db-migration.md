# P8-04 各種 Store のローカル JSON 永続化を DB 化する

- RUN_ID: 20260312T040136Z
- 実施日: 2026-03-12

## 実施内容
- 4つの状態StoreをローカルJSONファイル永続化からDB永続化へ置換した。
  - `AdminConfigStore`
  - `MasterUpdateStore`
  - `OrcaConnectionConfigStore`
  - `OrcaPatientSyncStateStore`
- 共通Repository `RuntimeStateRepository` を追加し、`opendolphin.runtime_state_store` へ JSONB を `upsert` する方式に統一した。
- ORCA患者同期ステータスの `statePath` はファイルパス表示を廃止し、DB保存先を示す識別子 (`db:opendolphin.runtime_state_store[...]`) を返すように変更した。

## 追加/更新した主なファイル
- `server-modernized/src/main/java/open/dolphin/runtime/RuntimeStateRepository.java`
- `server-modernized/src/main/java/open/dolphin/rest/admin/AdminConfigStore.java`
- `server-modernized/src/main/java/open/dolphin/rest/masterupdate/MasterUpdateStore.java`
- `server-modernized/src/main/java/open/dolphin/orca/config/OrcaConnectionConfigStore.java`
- `server-modernized/src/main/java/open/dolphin/orca/sync/OrcaPatientSyncStateStore.java`
- `server-modernized/src/main/java/open/dolphin/orca/rest/OrcaPatientSyncResource.java`

## Migration
- `V0304__runtime_state_store.sql` を追加。
  - 追加テーブル: `opendolphin.runtime_state_store`
  - 主キー: `(state_category, state_key)`
  - カラム: `payload_json (jsonb)`, `updated_at`
- Flywayミラー整合のため、以下両方に同一SQLを配置。
  - `server-modernized/src/main/resources/db/migration/`
  - `server-modernized/tools/flyway/sql/`

## 検証
- `mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DskipTests test-compile` PASS
- `mvn -o -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=OrcaConnectionConfigStoreTest,OrcaPatientSyncServiceTest,AdminMasterUpdateResourceTest,OrcaQueueResourceTest,FlywayMigrationConsistencyTest -Dsurefire.failIfNoSpecifiedTests=false test` PASS

## 備考
- `FreshSchemaBaselineTest` は sandbox 環境のポートbind制限で実行不可（`java.net.SocketException: Operation not permitted`）。
