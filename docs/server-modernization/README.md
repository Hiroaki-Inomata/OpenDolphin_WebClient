# Server-Modernization ドキュメントハブ（現行）

- 更新日: 2026-03-11
- RUN_ID: 20260310T201058Z

> 本ファイルが **現行の入口**。Phase2 文書は Legacy/Archive として参照専用です。
> 全体の優先順位は `docs/DEVELOPMENT_STATUS.md` を最上位とします。

## 当面の作業計画
- `docs/server-modernization/planning/server_modernization_wbs_detailed.md`
- `server-modernized` の当面作業を順番に進める際の現行 WBS。完了管理は ☐ / ☑ で更新する。
- `docs/DEVELOPMENT_STATUS.md`、`AGENTS.md`、最新のユーザー/マネージャー指示と矛盾する場合はそちらを優先する。

## 参照優先順位（Server-Modernization領域）
1. `docs/DEVELOPMENT_STATUS.md`
2. `AGENTS.md` / `GEMINI.md`
3. 本ファイル
4. `docs/server-modernization/planning/server_modernization_wbs_detailed.md`
5. 目的別ドキュメント

## 目的別ドキュメント（現行）
### API / 仕様
- `docs/server-modernization/server-api-inventory.md`
- `docs/server-modernization/MODERNIZED_REST_API_INVENTORY.md`
- `docs/server-modernization/orca-additional-api-implementation-notes.md`
- `docs/server-modernization/ORCA-order-system-rule.md`（ORCAオーダー仕様・実装要件）
- `docs/server-modernization/orca-api-contract-unification-20260218.md`
- `docs/server-modernization/reception-realtime-sync-20260219.md`
- `docs/server-modernization/orca-master-reference-update-platform-design-20260212.md`
- `docs/server-modernization/api-architecture-consolidation-plan.md`
- `docs/server-modernization/rest-api-modernization.md`

### module 永続化方針
- 新規 module 書込は `beanJson` のみを正規経路とする。
- `beanBytes` は旧データ読込 fallback 専用とし、新規の JSON+XML 二重保存は行わない。
- 互換を将来整理する場合も PostgreSQL `oid` への回帰は採らず、JSON 系へ統一する。
- 判断に迷う場合は `docs/DEVELOPMENT_STATUS.md` の最新方針を優先する。

### 運用 / 接続
- `docs/server-modernization/operations/ORCA_CERTIFICATION_ONLY.md`
- `docs/server-modernization/operations/ORCA_FIRECRAWL_INDEX.md`
- `docs/server-modernization/operations/OBSERVABILITY_AND_METRICS.md`
- `docs/server-modernization/operations/CODEX_ENV_SETUP.md`
- `docs/server-modernization/operations/API_PARITY_RESPONSE_CHECK.md`
- `docs/server-modernization/api-smoke-test.md`
- `docs/web-client/operations/security-rollout-checklist-20260304.md`（Web client 連携の CSRF/Logout/画像ヘッダ運用条件）

### Web client 連携セキュリティ契約（2026-03）
- デプロイ順序は backend 先行 → frontend 後続（逆順禁止）。
- `index.html` は `__CSRF_TOKEN__` を実トークンへ置換して配信し、`Cache-Control: private, no-store` を適用する。
- unsafe method（`POST/PUT/PATCH/DELETE`）の CSRF 検証は `fetch` と `XMLHttpRequest`（upload）で同一に扱う。
- `POST /api/logout` は `credentials` + CSRF を前提に冪等で処理する。
- 画像ヘッダは `X-Client-Feature-Images` のみを使用し、旧 `X-Feature-Images` は廃止する。

### セキュリティ設定（Trusted Proxy）
- 監査ログのクライアントIP解決で `X-Forwarded-For` / `X-Real-IP` を信用するには、trusted proxy を明示設定してください。
- 設定キー:
  - system property: `audit.trusted.proxies`
  - environment variable: `AUDIT_TRUSTED_PROXIES`
- 値はカンマ区切りで指定（単一IP または CIDR 例: `203.0.113.10,203.0.113.0/24`）。
- 未設定時は forwarded ヘッダを信用せず、`remoteAddr` を採用します（loopback は開発用途として許容）。

### CLAIM 廃止 / API-only
- `docs/server-modernization/ORCA_CLAIM_DEPRECATION.md`
- `docs/server-modernization/orca-claim-deprecation/`

### レビュー / 計画
- `docs/server-modernization/planning/server_modernization_wbs_detailed.md`
- `docs/server-modernization/library-update-plan.md`
- `docs/server-modernization/server-modernized-code-review-20260117.md`

### Preprod 課題 / 検証
- `docs/preprod/implementation-issue-inventory/`

## Legacy / Archive（参照専用）
- `docs/server-modernization/phase2/` 配下
- `docs/server-modernized/phase2/` 配下
- `docs/archive/2025Q4/server-modernization/` 配下
