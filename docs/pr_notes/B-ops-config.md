# B: ops/config hardening

- RUN_ID: `20260221T032517Z`
- Branch: `fix/worker-b-ops-config-20260221T032517Z`

## 変更概要
- `StubEndpointExposureFilter` を既定 deny に変更し、環境未設定かつ stub 許可時は fail-fast 化。
- `OrcaPatientSyncScheduler` を `ORCA_PATIENT_SYNC_ENABLED` 未設定時 OFF に変更。
- `MasterUpdateScheduler` に `MASTER_UPDATE_SCHEDULER_ENABLED` を導入し既定 OFF 化。
- `AdminConfigStore` / `OrcaConnectionConfigStore` / `MasterUpdateStore` / `MasterUpdateService` / `OrcaPatientSyncStateStore` から `/tmp` / `user.home` フォールバックを除去し、`jboss.server.data.dir` 必須化。
- `ServletStartup` のタイムゾーンを `OPENDOLPHIN_TIMEZONE`（既定 `Asia/Tokyo`）で解決し、起動時に構成サマリを1行 INFO 出力。
- `server-modernized.env.sample` を新規キーと既定値に合わせて更新。

## 注意点
- 本対応後は `-Djboss.server.data.dir=...` 未指定で起動すると fail-fast する（安全側挙動）。
- stub endpoint を有効化する場合は `OPENDOLPHIN_ENVIRONMENT` の明示設定が必要。
