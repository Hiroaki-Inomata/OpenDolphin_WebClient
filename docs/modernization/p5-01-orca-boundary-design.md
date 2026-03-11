# P5-01 ORCA境界の責務定義（RUN_ID: 20260311T120154Z）

## 目的
- 業務サービスから ORCA 固有詳細（XML形式、HTTP endpoint、transport 実装）を隠蔽する。
- 患者検索・患者更新・受付の3ユースケースを業務語彙で呼べる境界を固定する。

## 境界方針
- 業務層は `open.dolphin.orca.adapter.OrcaPatientAdapter` のみを参照する。
- adapter 実装内部でのみ `OrcaTransport` / endpoint / XML payload を扱う。
- 返却値は「業務判断に必要な最小情報 + 相関ID(requestId/runId)」を基本とする。

## 新規インターフェース
- 追加: `server-modernized/src/main/java/open/dolphin/orca/adapter/OrcaPatientAdapter.java`
- 提供ユースケース:
  - `searchPatients`（患者検索）
  - `upsertPatient`（患者更新/登録）
  - `registerReception`（受付登録）

## 責務分担
- 業務 service
  - 入力検証、業務ルール判定、認可。
  - ORCA呼び出しは adapter へ委譲。
- ORCA adapter
  - endpoint 選択、payload 変換、response 正規化、外部失敗分類。
- transport
  - HTTP/TLS/認証/timeout/retry の技術詳細。

## 完了条件との対応
- ORCA adapter の公開 interface を患者検索・患者更新・受付で定義。
- 業務側が XML/HTTP 詳細を直接知らない境界をコード上で明示。

## 次タスクへの接続
- P5-02: 接続設定/認証情報の外部化を adapter 実装に適用。
- P5-04: retry/timeout/失敗分類を adapter 内部ポリシーへ移管。
