# P2-02 削除マトリクス（旧入口）

- 更新日: 2026-03-11
- RUN_ID: 20260311T001341Z
- 入力: `docs/modernization/p2-01-public-endpoint-inventory.md`
- 方針前提: `docs/modernization/architecture-principles.md`（後方互換を前提にしない）

## 判定区分

- `削除`: 現行 web-client から利用されず、互換維持目的のみの入口。
- `置換`: 現行利用ありだが、新API命名へ移行対象。
- `統合`: 同一機能の alias が複数あるため1系統へ集約。
- `保留`: 外部利用の可能性があり、利用者確認が完了するまで維持。

## マトリクス

| 入口ファミリ | 代表パス | 現行利用者 | 判定 | 理由 | 後続タスク |
| --- | --- | --- | --- | --- | --- |
| Touch/ASP | `/touch/**` | 不明（現行web-client未使用） | 削除 | 旧互換専用。公開面削減を優先。 | `P2-04`, `P2-05` |
| xml2 alias（二重） | `/api01rv2/**`, `/api/api01rv2/**` | 一部管理機能/互換クライアント | 統合 | 同義の多重aliasが運用負荷。 | `P2-03`, `P2-06`, `P2-10` |
| ORCA番号付きalias | `/orca06/**`, `/orca12/**`, `/orca22/**`, `/orca25/**`, `/orca101/**`, `/orca102/**` | web-client一部 + 互換クライアント | 統合 | 番号付き入口を用途別 `/api/v1/orca/*` に寄せる。 | `P2-03`, `P2-10` |
| 旧REST命名（Karte） | `/karte/**` | web-client（現行利用中） | 置換 | 利用中のため即削除不可。新命名へ段階移行。 | `P2-03`, `P4-01`, `P4-08` |
| 旧REST命名（Stamp/Letter/Appo/PVT） | `/stamp/**`, `/odletter/**`, `/appo/**`, `/pvt/**` | web-client一部 / 不明 | 置換 | Resource粒度統一のため再設計対象。 | `P2-03`, `P4-03`, `P4-08` |
| 管理API（/api/admin） | `/api/admin/**` | web-client管理画面 | 置換 | 利用中。認可・監査再設計を前提に命名更新。 | `P2-03`, `P4-04`, `P8-06` |
| セッションAPI | `/api/session/**`, `/api/logout` | web-client認証導線 | 置換 | 利用中。認証方式一本化で再定義。 | `P2-03`, `P9-03` |
| ORCA servlet直配下 | `/orca/master/**`, `/orca/tensu/**`, `/orca/deptinfo` | web-client一部 + 互換用途 | 保留 | 呼び出し元確認が未完。削除前に利用実測が必要。 | `P2-10`, `P5-01`, `P5-06` |
| ORCA業務入口 | `/orca/order/**`, `/orca/disease`, `/orca/patient/**` | web-client業務導線 | 置換 | 利用中。機能単位の新APIへ置換。 | `P2-03`, `P5-06` |
| Realtime/queue | `/api/realtime/reception`, `/api/orca/queue` | web-client受付/カルテ | 置換 | 利用中。受信分離/監視設計に合わせ再定義。 | `P2-03`, `P7-01`, `P7-06` |

## 優先実施順

1. Touch/ASP と LegacyTouch 抽象層を削除（`P2-04`, `P2-05`）。
2. xml2 alias と ORCA番号付きaliasを整理し、残す契約を1系統に集約（`P2-03`, `P2-06`）。
3. 現行web-client利用中の `/karte/**`, `/api/admin/**`, `/orca/**` は新命名へ移行した後に旧入口を廃止（`P2-10`）。

## 承認メモ（更新欄）

- 2026-03-11: 初版作成。P2-01台帳を入力に判定区分を固定。
