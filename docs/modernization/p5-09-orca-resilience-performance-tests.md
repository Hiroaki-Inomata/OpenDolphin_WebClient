# P5-09 ORCA 連携の性能・障害試験（RUN_ID: 20260311T163020Z）

## 目的
- ORCA 連携で重要な障害パターン（遅延、タイムアウト、多重呼び出し、設定不備）を、外部環境非依存で再現可能な自動試験として固定する。

## 追加テスト
- `server-modernized/src/test/java/open/dolphin/orca/transport/OrcaHttpClientResilienceTest.java`

## 試験観点と結果
| 観点 | テスト名 | 期待結果 |
|---|---|---|
| 遅延・再試行 | `getRetriesHttp5xxAndEventuallySucceeds` | `5xx` 1回目失敗後に再試行し成功（send回数2） |
| タイムアウト | `getTimesOutByDeadlineWhenNetworkErrorContinues` | 総期限超過で `[deadline]` 例外を送出 |
| 多重呼び出し | `concurrentGetRequestsDoNotSerializeAllCalls` | 同時実行時 `maxInFlight >= 2`（直列化しない） |
| 設定不備 | `incompleteSettingsFailsFast` | 不完全設定を即時拒否（settings is incomplete） |

## 運用注意点（P5-09 時点）
- `orca.api.total-timeout-ms` を短くし過ぎると、再試行前に deadline 失敗へ倒れる。
- `orca.api.retry.network.max` と `orca.api.retry.network.backoff-ms` は同時に調整し、待機時間総和が total-timeout を超えないようにする。
- 外部遅延時のスレッド滞留を抑えるため、`GET` の再試行上限は用途ごとに最小限へ絞る。
