# Charts オーダーUI回帰テスト追従ノート

- 作成日: 2026-02-24
- RUN_ID: 20260224T113000Z
- 対象: `web-client/src/features/charts/__tests__/`

## 1. G) 回帰テスト強化の実装内容

- 共存シナリオ（右欄編集中に下欄操作、未保存離脱、復帰）:
  - `web-client/src/features/charts/__tests__/chartsOrderDockCoexistence.recovery-order.test.tsx`
  - `SoapNotePanel` の `onOrderDockStateChange` をモック駆動で変化させ、`ChartsPage` 下欄 `order-set/document` タブ操作と離脱ガードの共存を検証。
- RP主軸（複数RP連続編集、単独RP、保存再編集）:
  - `web-client/src/features/charts/__tests__/orderDockPanel.state-compat-and-rp-regression.test.tsx`
  - 複数RP連続編集で `保存して閉じる` 後に別RPへ再編集できること、単独RPでも保存後の再編集が成立することを検証。
- RP送信（単独/複数）:
  - `web-client/src/features/charts/__tests__/chartsActionBar.orca-send.test.tsx`
  - 必須項目がそろう単独RP送信の成功と、複数RP時に `medicalInformation` へ全件展開されることを検証。
- 互換維持（quick-add/group-add + onStateChange）:
  - 既存: `web-client/src/features/charts/__tests__/orderDockPanel.categoryButtons.test.tsx`
  - 追加: `web-client/src/features/charts/__tests__/orderDockPanel.state-compat-and-rp-regression.test.tsx`
  - `data-test-id` の維持と `onStateChange(hasEditing/targetCategory/count)` の互換を確認。
  - 旧エンティティ `laboTest` を `testOrder` 互換で表示できることを同テスト内で追加確認。

## 2. OUI-01〜OUI-05 追跡サマリ

| OUI | 対応コード | 対応テスト | 判定KPI |
|---|---|---|---|
| OUI-01 | `web-client/src/features/charts/OrderDockPanel.tsx` | `web-client/src/features/charts/__tests__/orderDockPanel.categoryButtons.test.tsx` | quick-add/group-add `data-test-id` が欠落しない |
| OUI-02 | `web-client/src/features/charts/OrderDockPanel.tsx` / `web-client/src/features/charts/orderCategoryRegistry.ts` | `web-client/src/features/charts/__tests__/orderDockPanel.state-compat-and-rp-regression.test.tsx` / `web-client/src/features/charts/__tests__/orderCategoryRegistry.test.ts` | 複数RP連続編集/単独RP保存再編集 + legacy `laboTest` 表示互換が成立 |
| OUI-03 | `web-client/src/features/charts/pages/ChartsPage.tsx` | `web-client/src/features/charts/__tests__/chartsOrderDockCoexistence.recovery-order.test.tsx` | 右欄編集中でも下欄タブ操作が継続可能 |
| OUI-04 | `web-client/src/features/charts/pages/ChartsPage.tsx` | `web-client/src/features/charts/__tests__/chartsOrderDockCoexistence.recovery-order.test.tsx` | 未保存離脱ガード後に `order-set` タブへ復帰可能 |
| OUI-05 | `docs/web-client/order-ui/charts-order-ui-refactor-plan-20260224.md` / `docs/web-client/CURRENT.md` / `docs/DEVELOPMENT_STATUS.md` | `web-client/src/features/charts/__tests__/chartsActionBar.orca-send.test.tsx` | 単独/複数RP送信回帰なし + typecheck/test実行記録を更新 |

## 3. 検証コマンド

- `npm -C web-client run typecheck`
- `npm -C web-client run test -- --run src/features/charts/__tests__/orderDockPanel.categoryButtons.test.tsx src/features/charts/__tests__/orderBundleItemActions.test.tsx src/features/charts/__tests__/orderBundleValidation.test.ts src/features/charts/__tests__/orderBundleMasterSearch.test.tsx src/features/charts/__tests__/orderBundleUsageSearch.test.tsx src/features/charts/__tests__/orderBundleBundleNumberUi.test.tsx src/features/charts/__tests__/orderBundlePrescription.test.ts src/features/charts/__tests__/orderBundleStampFlow.test.tsx src/features/charts/__tests__/chartsPageDirtyDot.test.tsx src/features/charts/__tests__/soapNoteDirtyState.test.tsx src/features/charts/__tests__/chartsActionBar.orca-send.test.tsx --silent=true`
- `npm -C web-client run test -- --run src/features/charts/__tests__/chartsOrderDockCoexistence.recovery-order.test.tsx src/features/charts/__tests__/orderDockPanel.state-compat-and-rp-regression.test.tsx src/features/charts/__tests__/orderCategoryRegistry.test.ts --silent=true`
- 実行結果: typecheck PASS、指定11ファイル PASS（11 files / 97 tests）、追加回帰 PASS（3 files / 7 tests）
