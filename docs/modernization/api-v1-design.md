# P2-03 新API名前空間設計（/api/v1）

- 更新日: 2026-03-11
- RUN_ID: 20260311T001459Z
- 目的: `P2-03` として、現行入口を `/api/v1` 配下に再編する命名規則と機能単位を固定する。
- 入力:
  - `docs/modernization/p2-01-public-endpoint-inventory.md`
  - `docs/modernization/remove-matrix.md`

## 1. 設計原則

- 公開入口は原則 `/api/v1/**` に統一する。
- 返却形式は JSON を正本とし、XML 専用口は新規作成しない。
- Resource は業務単位（患者/カルテ/受付/ORCA連携/添付/管理）で分割する。
- alias は原則 1 つに限定し、互換aliasは段階廃止する。

## 2. 名前空間

| ドメイン | 新ベースパス | 主用途 |
| --- | --- | --- |
| 認証/セッション | `/api/v1/auth` | login/factor2/logout/me |
| 患者 | `/api/v1/patients` | 検索、登録、更新、保険、メモ |
| カルテ | `/api/v1/kartes` | 文書、改訂、病名、観察、自由文書 |
| 添付/画像 | `/api/v1/attachments` | 画像一覧、アップロード、取得 |
| 受付 | `/api/v1/receptions` | 受付一覧、状態更新、メモ |
| リアルタイム | `/api/v1/realtime` | reception stream, chart event stream |
| 管理 | `/api/v1/admin` | 設定、アクセス管理、マスタ更新、セキュリティ |
| ORCA連携 | `/api/v1/orca` | 患者/病名/オーダ/マスタ/レポート/キュー |

## 3. 旧入口からの移行対応表（主要）

| 旧入口 | 新入口（方針） | 判定 |
| --- | --- | --- |
| `/api/session/login`, `/api/session/me`, `/api/logout` | `/api/v1/auth/login`, `/api/v1/auth/me`, `/api/v1/auth/logout` | 置換 |
| `/api/admin/**` | `/api/v1/admin/**` | 置換 |
| `/orca/patients/*`, `/orca12/patientmodv2/outpatient` | `/api/v1/orca/patients/**` | 統合 |
| `/orca/order/**` | `/api/v1/orca/orders/**` | 置換 |
| `/orca/master/**` | `/api/v1/orca/master/**` | 置換 |
| `/karte/**` | `/api/v1/kartes/**` | 置換 |
| `/patients/{patientId}/images`, `/karte/image/{id}` | `/api/v1/attachments/**` | 統合 |
| `/pvt/**`, `/api/realtime/reception` | `/api/v1/receptions/**`, `/api/v1/realtime/receptions` | 置換 |
| `/touch/**` | 新規作成しない（廃止） | 削除 |
| `/api01rv2/**`, `/api/api01rv2/**`, `/orcaXX/**` | `/api/v1/orca/**` | 統合 |

## 4. URI命名ルール

- 複数形リソースを基本: `/patients`, `/kartes`, `/orders`。
- 操作系はHTTPメソッドで表現し、`/do*` 形式は使わない。
- サブリソースは階層化: `/api/v1/patients/{patientId}/insurances`。
- 再実行可能な処理は `PUT` を優先し、作成は `POST`。

## 5. 直近実装境界（先行3系統）

`P2-03` の「まず患者、カルテ、ORCAの3系統だけ先に定義する」に従い、以下を先行対象とする。

1. 患者: `/api/v1/patients/**`
2. カルテ: `/api/v1/kartes/**`
3. ORCA: `/api/v1/orca/**`

## 6. 後続タスクへの入力

- `P2-04`: Touch/ASP 削除の対象確定（`/touch/**` 廃止）。
- `P2-06`: XML専用口の廃止対象確定（`/api01rv2/**` 系）。
- `P2-10`: 新旧 API 差分文書化時の正本。
