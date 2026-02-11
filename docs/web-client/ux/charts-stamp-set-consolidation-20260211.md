# Charts スタンプ/セット統廃合とオーダー最適化（2026-02-11）

- RUN_ID: `20260211T202801Z`
- 対象: `web-client/src/features/charts/pages/ChartsPage.tsx`, `web-client/src/features/charts/StampLibraryPanel.tsx`

## 背景
- カルテ画面のユーティリティで「セット」と「スタンプ」が別タブ化され、機能差が利用者に伝わりにくい。
- スタンプ利用時にオーダー編集タブへ戻る操作が固定で、対象エンティティへの遷移が最短化されていなかった。

## 統廃合方針
- ユーティリティタブ名は「スタンプ」に統一し、入口を1つに統合する。
- 責務は維持する。
  - セット: 病名/SOAP/オーダー/画像を横断保存・適用。
  - スタンプ: オーダー専用定型入力の検索・コピー・貼り付け。

## 実装内容
- `ChartsPage` から独立した「スタンプ」タブを削除し、`order-set` タブへスタンプライブラリを内包。
- `order-set` タブ名称を「スタンプ」へ統一。
- `StampLibraryPanel` の `onOpenOrderEdit` を `targetEntity` 引数付きに変更し、選択スタンプの対象エンティティをコールバックするよう改修。
- `ChartsPage` 側で `handleOpenOrderEditorFromEntity` を利用し、対象エンティティに応じたオーダー編集タブへ直接遷移する導線へ変更。

## オーダー画面の最適化ポイント
- スタンプ選択後に「オーダー編集を開く」を押すと、処方/注射/処置/検査/算定の該当タブへ直接フォーカス移動。
- 従来の固定遷移（処置タブ固定）を廃止し、不要なタブ切替を削減。

## 検証
- `npm -C web-client run typecheck`
- `npm -C web-client run test -- --run src/features/charts/__tests__/stampLibraryPanel.test.tsx --silent=true`
- `npm -C web-client run test -- --run src/features/charts/__tests__/orderBundleStampFlow.test.tsx src/features/charts/__tests__/chartOrderSetStorage.test.ts --silent=true`
- `npm -C web-client run test -- --run src/features/charts/__tests__/orderBundleMasterSearch.test.tsx src/features/charts/__tests__/orderBundleBodyPart.test.tsx --silent=true`
