# F perf/stability メモ

- RUN_ID: `20260221T032548Z`
- ブランチ: `fix/worker-f-perf-stability-20260221T032548Z`

## 実施内容

1. `PhrExportJobWorker` の ZIP 生成を `ByteArrayOutputStream` から一時ファイル出力へ変更し、`storeArtifact` への入力をファイルストリーム化。
2. PHR export の進捗更新を毎件から間引き（20件ごと + 最終件）へ変更。
3. `AttachmentStorageManager` の S3 put を `@Transactional(NOT_SUPPORTED)` な公開メソッド経由へ分離し、外部I/Oをトランザクション境界外で実行。
4. S3アップロード成功時のみ既存の rollback 補償フック登録を維持（DB rollback 時に S3 delete）。
5. `MeterRegistryProducer` の OTLP sweeper を static からインスタンス管理へ変更し、`@PreDestroy` で `shutdownNow` を実施。
6. `ReceptionRealtimeSseSupport` を managed scheduler 優先（`java:jboss/ee/concurrency/scheduler/default`）へ変更。fallback executor 使用時のみ自前 shutdown。
7. `PvtService` の接続処理を `new Thread` 無制限生成から bounded `ThreadPoolExecutor` へ変更し、accept/read timeout と shutdown 手順を追加。

## テスト

- `server-modernized` で `mvn -q test` を実行し成功。
- リポジトリ root の `mvn -q test` は既存依存不足により失敗（`opendolphin-client` の `opendolphin:itext-font:1.0` と `com.apple:AppleJavaExtensions:1.6` が解決不可）。
