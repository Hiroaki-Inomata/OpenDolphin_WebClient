# Charts スタンプ独立管理化（2026-02-12）

- RUN_ID: 20260212T043317Z
- 対象: Webクライアント（Charts）

## 背景
- カルテの各オーダー編集UI（処方/算定など）にスタンプ機能が埋め込まれていた。
- 同時に独立したスタンプUI（`StampLibraryPanel`）も存在しており、操作導線が重複していた。

## 方針
- オーダー編集UIからスタンプ機能を撤去し、スタンプ操作は独立UIに一本化する。
- ただし、既存スタンプの編集・新規登録は独立UIで継続可能にする。

## 実装内容
1. `OrderBundleEditPanel`
- スタンプ保存/取り込み/コピー/ペーストUIを削除。
- スタンプ関連の状態・クエリ・ミューテーション・監査分岐を削除。
- 左カラムの役割を「頻用オーダー」専用に整理。

2. `StampLibraryPanel`
- 閲覧/検索/プレビュー機能は維持。
- 編集フォームを追加し、以下を実装。
  - 選択スタンプの編集フォーム読み込み（サーバー/ローカル）
  - ローカル新規登録
  - ローカル既存更新
  - ローカル削除
- Phase2 クリップボードコピーは継続。

3. `stampStorage`
- ローカルスタンプ更新API `updateLocalStamp` を追加。
- ローカルスタンプ削除API `deleteLocalStamp` を追加。

4. `ChartsPage`
- スタンプ説明文を独立管理前提へ更新。
- `StampLibraryPanel` のオーダー遷移依存プロップを削除。

## 検証
- `npm -C web-client run typecheck` PASS
- `npm -C web-client run test -- --run src/features/charts/__tests__/orderBundleStampFlow.test.tsx src/features/charts/__tests__/stampLibraryPanel.test.tsx src/features/charts/__tests__/orderBundleBodyPart.test.tsx -u --silent=true` PASS
  - Snapshot 1件更新: `src/features/charts/__tests__/__snapshots__/orderBundleBodyPart.test.tsx.snap`
