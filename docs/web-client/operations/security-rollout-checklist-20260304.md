# Web Client Security Rollout Checklist (CSRF / Logout / Images)

- 更新日: 2026-03-04
- RUN_ID: 20260304T053109Z
- 対象: web-client + server-modernized + CDN/Reverse Proxy

## 1. デプロイ順序（必須）
- backend 先行、frontend 後続を厳守する。
- 逆順（frontend 先行）は禁止する。
- 理由: frontend は `index.html` の `__CSRF_TOKEN__` 置換と `/api/logout` 契約を前提に、unsafe method を CSRF 必須でブロックするため。

## 2. Backend 側の事前チェック（frontend デプロイ前に完了）
- [ ] `index.html` の `meta[name="csrf-token"]` へ実トークンを注入する。
- [ ] 未置換値 `__CSRF_TOKEN__` を本番配信しない。
- [ ] `POST/PUT/PATCH/DELETE` で CSRF 検証を有効化する。
: `fetch` と `XMLHttpRequest`（アップロード経路含む）を同一条件で検証する。
- [ ] `POST /api/logout` を提供する。
: `credentials` 前提のセッション無効化 + CSRF 必須。
- [ ] `POST /api/logout` は冪等にする（複数回呼ばれても 200/204 等で安全に完了）。

## 3. HTML キャッシュ要件（必須）
- [ ] `index.html` は `Cache-Control: private, no-store`（または同等以上）を設定する。
- [ ] CDN / Reverse Proxy で HTML キャッシュを無効化する。
- [ ] デプロイ時に旧 HTML キャッシュを必ず purge/invalidate する。
- [ ] 共有キャッシュに CSRF トークン入り HTML を残さない。

## 4. Frontend 側の事後チェック（backend 反映後に実施）
- [ ] `meta[name="csrf-token"]` が空/空白/`__CSRF_TOKEN__` でないことを確認する。
- [ ] unsafe method の同一オリジン request に `X-CSRF-Token` が付与されることを確認する。
- [ ] upload（XHR）経路でも同一の CSRF 条件で送信されることを確認する。
- [ ] logout 実行時に `POST /api/logout` が呼ばれ、UI logout が完了することを確認する。

## 5. 画像ヘッダ運用（旧ヘッダ廃止）
- [ ] クライアントは `X-Client-Feature-Images` のみ送信する。
- [ ] backend は `X-Feature-Images` を前提条件にしない。
- [ ] 旧 `X-Feature-Images` は廃止済みとして運用し、再導入しない。

## 6. 受け入れ判定（Go/No-Go）
- Go 条件:
  - backend 先行適用完了
  - `index.html` CSRF 注入 + no-store 適用確認済み
  - unsafe method CSRF（fetch/XHR）検証 OK
  - `/api/logout`（冪等 + CSRF + credentials）検証 OK
  - 画像ヘッダが `X-Client-Feature-Images` のみで疎通 OK
- No-Go 条件:
  - `__CSRF_TOKEN__` のまま配信
  - HTML キャッシュが有効なまま
  - `/api/logout` 未実装（404 常態）
  - 旧 `X-Feature-Images` 依存が残存
