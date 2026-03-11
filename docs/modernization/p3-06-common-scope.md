# P3-06 audit / util / common 最小責務定義

- 実施日: 2026-03-11
- RUN_ID: 20260311T080109Z
- WBS: `P3-06`

## 目的
`common` を「何でも入る箱」にしないため、`open.dolphin.audit` / `open.dolphin.util` / `open.dolphin.common` の責務を最小化し、配置ルールを固定する。

## 現在の棚卸し結果
### `open.dolphin.audit`
- `AuditTrailService`: セッション層から監査イベント永続化を抽象化する最小 API。
- `AuditEventEnvelope`: 監査イベントを transport/保存へ橋渡しするシリアライズ DTO。
- 判断: 維持（横断責務として妥当）。

### `open.dolphin.util`
- `LegacyBase64`: 旧互換の Base64 変換ユーティリティ。
- 判断: 維持（単機能・副作用なし・依存最小）。

### `open.dolphin.common`
- `OrcaApi` / `OrcaConnect` / `OrcaAnalyze`: ORCA 通信・解析の旧実装。
- `common.cache.CacheUtil`: 汎用 TTL cache 補助。
- 判断:
  - `Orca*` は legacy 扱い。`@Deprecated` を付与し、新規利用を禁止。
  - `CacheUtil` は汎用ユーティリティとして維持可能だが、用途は「短寿命キャッシュ補助」に限定。

## 配置ルール（P3-06 で固定）
1. `open.dolphin.audit` には「監査の契約（API/Envelope）」のみ置く。
2. `open.dolphin.util` には「純粋関数・副作用なし・外部I/Oなし」のみ置く。
3. `open.dolphin.common` への新規クラス追加は禁止。
4. ORCA 連携実装は `server-modernized` の ORCA adapter 境界（P5 系）へ集約する。
5. 新規の共通化は、まず `domain` / `api-contract` / `persistence` のどこに置けるかを先に検討し、最後の手段としてのみ共通層化する。

## 実施内容
- `open.dolphin.common.OrcaApi` / `OrcaConnect` / `OrcaAnalyze` に `@Deprecated(since = "2026-03")` を付与し、新規流入防止をコード上で明示。

## 次段タスクへの接続
- P3-07 ではダミー参照や死蔵補助コードを削除し、P3-06 で定義した「新規追加禁止・責務限定」を実コードへ反映していく。
