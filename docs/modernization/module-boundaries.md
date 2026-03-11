# P3-01 モジュール境界設計（RUN_ID: 20260311T061511Z）

- 更新日: 2026-03-11
- 目的: `common` 解体の着手条件として、責務境界と依存方向を固定する。
- 対象: `common` / `server-modernized` / 新規 module（`P3-02` で作成）

## 1. 目標モジュール

| モジュール | 主要責務 | 代表ソース候補 |
| --- | --- | --- |
| `domain` | 業務ルール、ユースケース、値オブジェクト | `open/dolphin/session/**`（業務判断部分） |
| `persistence` | Entity, Repository, Query, Migration 接続 | `open/dolphin/infomodel/**`, DAO/JPA 関連 |
| `api-contract` | API 入出力 DTO、エラー契約、公開 enum | `open/dolphin/rest/*dto*`（新設） |
| `orca-adapter` | ORCA 通信、ORCA DTO 変換、外部境界 | `open/orca/rest/**`, `open/dolphin/orca/**` |
| `ingestion` | PVT/JMS 受信、再試行、重複防止 | `PvtService`, `MessageSender` 系 |
| `storage` | 添付保存、設定保存、外部ストレージ抽象 | `open/dolphin/storage/**`, Store 系 |
| `api-server` | REST Resource, 認証/認可、HTTP エンドポイント | `open/dolphin/rest/**` |

## 2. 依存方向（許可）

- `api-server` -> `domain`, `api-contract`, `orca-adapter`, `storage`
- `domain` -> `persistence`, `orca-adapter`, `storage`
- `ingestion` -> `domain`, `api-contract`
- `orca-adapter` -> `api-contract`
- `storage` -> `api-contract`
- `persistence` -> （下位のみ、他業務モジュールへ逆参照禁止）
- `api-contract` -> （他モジュールへ依存禁止）

## 3. 禁止ルール

- `api-server` から `Entity` を直接返却しない（`api-contract` DTO 経由のみ）。
- `domain` から `JAX-RS` / `Servlet` / `HttpServletRequest` へ依存しない。
- `orca-adapter` の XML/HTTP 詳細を `domain` / `api-server` に露出しない。
- `storage` の実装詳細（S3 SDK / ローカルパス）を `domain` へ露出しない。
- `common` への新規クラス追加を禁止（解体中のため）。

## 4. 既存パッケージの一次割当（P3-02 への入力）

| 既存パッケージ | 一次移管先 |
| --- | --- |
| `open/dolphin/rest/**` | `api-server` |
| `open/dolphin/session/**` | `domain`（インフラ混在分は後続分離） |
| `open/dolphin/infomodel/**` | `persistence` |
| `open/orca/rest/**`, `open/dolphin/orca/**` | `orca-adapter` |
| `open/dolphin/msg/**`, `PvtService` 周辺 | `ingestion` |
| `open/dolphin/storage/**`, `*Store` | `storage` |

## 5. P3-02 実装指針（最小）

1. 先行作成は `domain` と `api-contract` の 2 module。
2. 親 POM に依存方向チェック（循環禁止）を追加。
3. 既存コード移動は最小限に留め、まず空 module + テスト雛形を通す。
