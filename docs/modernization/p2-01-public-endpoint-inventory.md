# P2-01 現行公開入口台帳（server-modernized）

- 更新日: 2026-03-11
- RUN_ID: 20260311T001049Z
- 目的: `P2-01`「現行の公開入口を一覧化する」の成果として、公開入口と想定利用者を1枚に固定する。
- 根拠:
  - `server-modernized/src/main/webapp/WEB-INF/web.xml`
  - `server-modernized/src/main/java/open/dolphin/rest/**`
  - `server-modernized/src/main/java/open/dolphin/touch/**`
  - `server-modernized/src/main/java/open/orca/rest/**`
  - `web-client/src/**`

## 1. HTTP入口（Servlet Mapping）

| 公開URL | Dispatcher | マッピングprefix | 主なResource配置 |
| --- | --- | --- | --- |
| `/resources/*` | `resteasy-servlet` | `/resources` | `open.dolphin.rest/**`, `open.dolphin.touch/**` |
| `/orca/*` | `resteasy-orca-servlet` | `/orca` | `open.orca.rest/**` |

補足:
- `web.xml` 上は上記2入口がREST公開面の正本。
- `@Path("/api/..."), @Path("/orca...")` などは、実際には `/resources` 配下のパスとして公開される。

## 2. 現行Webクライアント利用入口（確認済み）

`web-client/src/libs/http/httpClient.ts` と各 feature API 実装で参照される入口。

| 区分 | 代表パス | 想定利用者 | 状態 |
| --- | --- | --- | --- |
| セッション/認証 | `/api/session/login`, `/api/session/me`, `/api/logout` | Web client ログイン/ログアウト | 利用中 |
| 管理（設定/接続） | `/api/admin/config`, `/api/admin/orca/connection`, `/api/admin/access/users`, `/api/admin/master-updates/**` | Web client 管理画面 | 利用中 |
| 受付/リアルタイム | `/orca/appointments/*`, `/orca/visits/*`, `/api/realtime/reception`, `/api/orca/queue`, `/orca/pusheventgetv2` | Web client 受付画面 | 利用中 |
| カルテ/画像/添付 | `/karte/document`, `/karte/image/{id}`, `/karte/attachment/{id}`, `/patients/{patientId}/images` | Web client カルテ/画像画面 | 利用中 |
| ORCA業務API | `/orca/order/**`, `/orca/disease`, `/orca/patient/**`, `/orca/master/**`, `/orca/medicalgetv2`, `/api21/medicalmodv2`, `/api21/medicalmodv23` | Web client カルテ/患者/請求UI | 利用中 |
| ORCA XML proxy | `/orca/acceptlstv2`, `/orca/system01lstv2`, `/api/orca101/manageusersv2`, `/orca/insprogetv2` | Web client 管理XMLテスト機能 | 利用中 |

## 3. Legacy/要確認入口（呼び出し元の明確化が必要）

| 区分 | 代表パス | 実装 | 現時点判断 |
| --- | --- | --- | --- |
| Touch/ASP系 | `/touch/**` | `open.dolphin.touch.DolphinResourceASP` | 現行 web-client から直接利用を確認できず。P2-02 以降で削除候補として扱う。 |
| ORCA旧REST（/orca servlet配下） | `/orca/master/**`, `/orca/tensu/**`, `/orca/disease/**`, `/orca/deptinfo` | `open.orca.rest.OrcaResource`, `OrcaMasterResource` | 一部は現行web-clientで利用中。未利用メソッドは要確認。 |
| 旧互換alias群 | `/api01rv2/**`, `/api/api01rv2/**`, `/orcaXX/**` | `open.dolphin.rest.Orca*ApiResource` 群 | 互換用aliasが多く、実利用の最小集合をP2-02で確定する。 |

## 4. 利用者メモ（誰が呼ぶか）

- Web client:
  - 主に `/api/**`, `/karte/**`, `/orca/**` を呼び出す。
  - 根拠: `web-client/src/features/**`, `web-client/src/libs/http/httpClient.ts`。
- ORCA連携クライアント/外部連携:
  - xml2互換の `/api01rv2/**` 等を経由する可能性あり。
  - 根拠: `open.dolphin.rest.Orca*ApiResource` の複数alias定義。
- 不明（要確認）:
  - `/touch/**` の直接利用者。
  - `/orca/*` 配下のうち web-client 未参照メソッド。

## 5. 次工程への引き継ぎ

- `P2-02` で本台帳を入力として「削除/置換/統合/保留」を確定する。
- `P2-03` で `/api/v1` 命名へ移行する新API設計を定義する。
- `P2-04` 着手前に `/touch/**` の残存参照を再検索してゼロ化計画を作る。
