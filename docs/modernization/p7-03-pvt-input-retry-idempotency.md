# P7-03 PVT入力パイプライン再試行/重複防止/毒メッセージ退避（RUN_ID: 20260311T220125Z）

## 目的
- `PvtSocketWorker` の入力処理で、失敗時の再試行と重複受信の抑止を行う。
- 再試行で解消できない入力を毒メッセージとして隔離し、主処理系の破壊を防ぐ。

## 実装内容
- `PvtSocketWorker` に以下を追加。
  - 再試行制御: `maxHandleAttempts` 回まで payload 処理を再実行。
  - バックオフ: `handleRetryBackoffMillis` で試行間隔を制御。
  - 重複防止: payload の SHA-256 を idempotency key とし、`idempotencyWindowMillis` 内の再受信を ACK でスキップ。
  - 毒メッセージ退避: `poisonQueueCapacity` 件までメモリ保持し、原因・試行回数・payload 先頭断片を記録。
- `PvtService` に設定読み込みを追加。
  - `pvt.listen.retry.max`
  - `pvt.listen.retry.backoffMillis`
  - `pvt.listen.idempotency.windowMillis`
  - `pvt.listen.poison.capacity`
  - 未設定時は安全側デフォルトへフォールバック。

## 検証
- 追加テスト: `PvtSocketWorkerPipelineTest`
  - 重複 payload が再処理されないこと
  - 一時失敗時に再試行して成功すること
  - 恒久失敗時に poison queue へ退避されること

## 運用メモ
- poison queue はインメモリ保持のため、プロセス再起動時に消える。
- 永続保管/再送オペレーションは `P7-05` の replay ツールで補完する前提。
