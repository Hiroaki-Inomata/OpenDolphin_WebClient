# server-modernized 運用メモ（Subjectives 既定値）

## 既定動作
- subjectives（`/orca/chart/subjectives`）は **デフォルトで REAL**。プロファイルが dev / prod いずれでも、環境変数やシステムプロパティを設定しない状態で stub 応答にはならない。

## stub へ切り替える方法（上から優先）
1. `ORCA_POST_SUBJECTIVES_MODE=stub`（または `-Dorca.post.subjectives.mode=stub`）
2. `ORCA_POST_SUBJECTIVES_USE_STUB=true`（または `-Dorca.post.subjectives.useStub=true`）
3. 全体設定 `ORCA_POST_MODE=stub`（subjectives 個別設定が無い場合のみ有効）

## REAL を明示したい場合
- `ORCA_POST_SUBJECTIVES_MODE=real` または `-Dorca.post.subjectives.mode=real` を指定する。

## 開発/検証で stub を使う例
```bash
# stub 応答に切替えて動作確認したいとき
ORCA_POST_SUBJECTIVES_MODE=stub
# または
ORCA_POST_SUBJECTIVES_USE_STUB=true
```

## Web client 連携のセキュリティ運用契約（2026-03）
- backend 先行 → frontend 後続でデプロイする（逆順禁止）。
- `index.html` の `meta[name="csrf-token"]` は `__CSRF_TOKEN__` を実トークンへ置換し、`Cache-Control: private, no-store` を適用する。
- unsafe method（`POST/PUT/PATCH/DELETE`）の CSRF 検証は `fetch` と `XMLHttpRequest`（upload）を同一条件で扱う。
- `POST /api/logout` は `credentials` + CSRF を前提に冪等で処理する。
- 画像ヘッダは `X-Client-Feature-Images` のみを受け入れ、旧 `X-Feature-Images` は廃止する。
- 詳細チェックリスト: `docs/web-client/operations/security-rollout-checklist-20260304.md`
