# Server-Modernization ドキュメントハブ（現行）

- 更新日: 2026-03-11
- RUN_ID: 20260311T050147Z

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
- `docs/modernization/architecture-principles.md`（刷新方針の固定版）
- `docs/modernization/deferred-scope.md`（一時据え置き領域と後続候補）
- `docs/modernization/business-critical-flows.md`（最重要業務フロー定義）
- `docs/modernization/acceptance-criteria.md`（受け入れ条件定義）
- `docs/modernization/p1-03-baseline-fixture-setup.md`（P1-03 基準データ・fixture 初期化手順）
- `docs/server-modernization/server-api-inventory.md`
- `docs/server-modernization/MODERNIZED_REST_API_INVENTORY.md`
- `docs/modernization/p2-01-public-endpoint-inventory.md`（P2-01: 現行公開入口台帳）
- `docs/modernization/remove-matrix.md`（P2-02: 旧入口の削除/置換/統合マトリクス）
- `docs/modernization/api-v1-design.md`（P2-03: /api/v1 名前空間設計）
- `docs/modernization/p2-04-touch-asp-removal.md`（P2-04: Touch/ASP 入口削除）
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

### テスト実行方針（server-modernized / Mockito inline）
- 既定実行は **JDK25（Homebrew OpenJDK）** を使用する。
- 検証対象（管理設定/認証まわり）は以下を基準テストとする。
  - `AdminAccessResourceTest`
  - `AdminOrcaConnectionResourceTest`
  - `SessionAuthResourceTest`
  - `LogoutResourceTest`
- 実行コマンド（既定）:
  - `mvn -f pom.server-modernized.xml -pl server-modernized -am -Dtest=AdminAccessResourceTest,AdminOrcaConnectionResourceTest,SessionAuthResourceTest,LogoutResourceTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 既定環境で Mockito inline の attach が不安定な場合のみ、fallback として **JDK21 + byte-buddy-agent** を用いる。
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -f pom.server-modernized.xml -pl server-modernized -am -DargLine=-javaagent:/Users/Hayato/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar -Dtest=AdminAccessResourceTest,AdminOrcaConnectionResourceTest,SessionAuthResourceTest,LogoutResourceTest -Dsurefire.failIfNoSpecifiedTests=false test`

### CI 常時実行（P1-10）
- Workflow: `.github/workflows/server-modernized-characterization.yml`
- 目的: 性格確認テストを `PR軽量` と `夜間拡張` に分けて常時実行し、回帰を早期検知する。
- トリガ:
  - PR (`server-modernized/**`, `common/**`, `pom.server-modernized.xml`, workflow 自身)
  - nightly schedule（毎日 UTC 18:00）
  - `workflow_dispatch`
- 実行環境:
  - 一次実行: `JDK25 (Temurin)`
  - fallback: 一次実行失敗時のみ `JDK21 + -DargLine=-javaagent:${HOME}/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar`
- 実行セット:
  - PR軽量（患者・カルテ・ORCA）:
    - `PatientServiceBeanAddPatientTest`
    - `PatientModV2OutpatientResourceIdempotencyTest`
    - `KarteServiceBeanDocPkTest`
    - `KarteRevisionServiceBeanAttachmentCloneTest`
    - `OrcaPatientApiResourceRunIdTest`
    - `OrcaPatientResourceIdempotencyTest`
    - `OrcaAcceptanceListResourceTest`
  - 夜間拡張（P1-04〜P1-09 の固定テスト群）:
    - 患者、カルテ、ORCA、PVT、添付、管理設定/認証の代表26クラス（実行テスト数の目安: 113）
- 失敗時対応:
  - Actions Artifacts の surefire report（`**/target/surefire-reports/*.xml` / `*.txt`）を確認する。
  - failing class を同じ `-Dtest=` 指定でローカル再現する。
  - 修正後は同クラス群で再実行し、WBSと `docs/DEVELOPMENT_STATUS.md` を更新する。

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
