# Charts セマンティック配色見直し（2026-02-12）

- RUN_ID: `20260212T060228Z`
- 対象: `web-client/src/features/charts/*`
- 目的: カルテ画面の視認性を改善し、ボタンや状態表示に機能的な意味を持たせる。

## 方針

- レイアウト構成は変更しない。
- 色だけで操作意図を識別できるよう、機能別の配色ルールを導入する。
- `ChartsActionBar` / オーダー入力タブ（`OrderBundleEditPanel`）/ ユーティリティタブで同じ意味体系を採用する。

## 操作意味と色

- 実行・進行系（送信/検索/展開）: 青系
- 保存・確定系（保存して追加/保存更新）: 緑系
- 中断・注意系（診察中断/クリア/再確認）: 橙系
- 破棄・削除系（削除/全クリア/キャンセル）: 赤系
- 補助・閲覧系（ドラフト/履歴コピー/参照）: グレー〜シアン系

## 実装要点

- `ChartsActionBar`
  - 状態行を `ready/busy/guarded/locked` の4トーンに分割。
  - 診察開始/中断/終了、ORCA送信、印刷、キャンセル、ロック解除に用途別クラスを付与。
- `OrderBundleEditPanel`
  - セクションに `data-order-entity` を追加し、処方/注射/検査/算定でアクセント色を切替。
  - 頻用候補・検索プリセット・検索実行・展開・保存・履歴操作（コピー/編集/削除）へ用途別クラスを付与。
- `ChartsPage` ユーティリティタブ
  - タブに `data-utility-kind` を追加（order/stamp/document/imaging）。
  - 非アクティブ/アクティブの色をカテゴリごとに統一し、現在操作中の領域を明確化。

## 検証

- `npm -C web-client run typecheck`
- `npm -C web-client run test -- --run src/features/charts/__tests__/chartsActionBar.test.tsx src/features/charts/__tests__/chartsActionBar.orca-send.test.tsx src/features/charts/__tests__/orderBundleMasterSearch.test.tsx src/features/charts/__tests__/orderBundleItemActions.test.tsx src/features/charts/__tests__/orderBundleUsageSearch.test.tsx src/features/charts/__tests__/orderBundleBundleNumberUi.test.tsx src/features/charts/__tests__/orderBundleValidation.test.ts --silent=true`
- `npm -C web-client run test -- --run src/features/charts/__tests__/chartsMasterSourceCache.test.tsx --silent=true`
