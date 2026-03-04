# Web Client Security Spec

## 1. Secrets
- `VITE_` 接頭辞の変数は公開配布物へ埋め込まれるため、機密値を設定しない。
- `.env.local` / `.env.*` に機密値を置かない。必要な認証情報はサーバ側または Secret Manager で管理する。
- `npm run verify:no-public-secrets` を CI 必須チェックとして運用する。

## 2. CSRF
- unsafe method（`POST` / `PUT` / `PATCH` / `DELETE`）かつ同一オリジンへの送信は `X-CSRF-Token` を必須とする。
- `fetch` と `XMLHttpRequest` は `buildHttpHeaders` を共通利用し、CSRF 付与判定を統一する。
- `meta[name="csrf-token"]` が空・空白・`__CSRF_TOKEN__` の場合は未設定扱いとする。
- 本番相当（`import.meta.env.PROD === true`）では token 欠落時に送信前に失敗させる。
- dev/test では `VITE_ALLOW_MISSING_CSRF=1` が明示された場合のみ token 欠落を許可する。

## 3. URL
- query には患者関連キー（`patientId` / `appointmentId` / `receptionId` / `visitDate` / `invoiceNumber`）と自由入力キー（`kw` / `keyword`）を残さない。
- deep link で受信した値は処理後に `replace` で scrub し、必要最小限の encounter context を sessionStorage（TTL 2時間）へ退避する。
- `returnTo` は保存・遷移前に必ず `scrubPathWithQuery` を通し、機微クエリと hash を除去する。

## 4. Storage
- localStorage に患者関連情報（患者ID・氏名・予約/受付ID・請求番号・自由入力 keyword）を保存しない。
- sessionStorage は最小限に制限し、患者関連コンテキストは TTL 2時間で期限管理する。
- `patient-tabs` 永続化から `name` / `department` を除去し、旧データ読み込み時も破棄する。
- `orca-claim-send` / `orca-income-info` の永続化では請求番号や警告詳細を保存しない。
- logout 時に患者関連 key を `clearScopedStorage` で必ず削除する。

## 5. Logout
- logout は以下の順序で実行する。
  1. サーバ logout API（`POST`, `credentials: include`）を best-effort 実行
  2. クライアント側 storage cleanup
  3. shared auth cleanup
  4. `/login` へ replace 遷移
- サーバ logout が 404 の場合は未実装として扱い、監査ログへ `outcome=unsupported` を記録する。
- サーバ logout が失敗しても UI logout は継続し、患者関連データを残さない。

## 6. バックエンド依頼事項
- デプロイ順序は「backend の CSRF 注入対応を先行」「frontend を後続」とする。逆順デプロイは禁止。
- `index.html` の `meta[name="csrf-token"]` へ実トークンを注入する（`__CSRF_TOKEN__` を本番値へ置換）。配信時はキャッシュでトークンが残留しないよう `Cache-Control: private, no-store`（または同等以上）を設定する。
- `/api/logout` は `POST` + `credentials` + CSRF 必須で受け付ける。frontend は 404 を `unsupported` 扱いで継続するため、404 が継続する環境では監査ログ上の未実装警告が残る点に注意する。
- 画像機能ヘッダは `X-Client-Feature-Images` のみを利用する。`X-Feature-Images` は廃止済みのため受理前提にしない。
