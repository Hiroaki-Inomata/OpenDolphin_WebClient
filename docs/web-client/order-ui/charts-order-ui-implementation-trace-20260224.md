# Charts オーダーUI 実装詳細トレース（OUI-01〜OUI-05）

- 作成日: 2026-02-24
- RUN_ID: 20260224T213000Z
- 対象: Charts オーダーUI再編の実装結果（RP主軸化・共存ガード・KPI計測・互換維持）

## 1. 目的

- OUI-01〜OUI-05 を `OUI-ID / file / test / KPIイベント` で 1:1 追跡し、監査時に計画と実装結果を同一キーで照合できるようにする。

## 2. 追跡表（1:1）

| OUI-ID | 対応コード (file) | 対応テスト (test) | KPIイベント | 判定KPI |
|---|---|---|---|---|
| OUI-01 | `web-client/src/features/charts/OrderDockPanel.tsx` | `web-client/src/features/charts/__tests__/orderDockPanel.categoryButtons.test.tsx` | `kpi.order_ui.quick_add_group_add.maintained` | quick-add/group-add の `data-test-id` が 10/10 維持される |
| OUI-02 | `web-client/src/features/charts/OrderDockPanel.tsx` / `web-client/src/features/charts/orderCategoryRegistry.ts` | `web-client/src/features/charts/__tests__/orderDockPanel.state-compat-and-rp-regression.test.tsx` / `web-client/src/features/charts/__tests__/orderCategoryRegistry.test.ts` | `kpi.order_ui.rp_primary_and_legacy_compat.stable` | 複数RP連続編集・単独RP保存再編集 + legacy `laboTest` 表示互換が回帰しない |
| OUI-03 | `web-client/src/features/charts/pages/ChartsPage.tsx` | `web-client/src/features/charts/__tests__/chartsOrderDockCoexistence.recovery-order.test.tsx` | `kpi.order_ui.coexistence_guard.edit_state_persisted` | 右欄編集中に下欄タブ操作しても編集状態が維持される |
| OUI-04 | `web-client/src/features/charts/pages/ChartsPage.tsx` | `web-client/src/features/charts/__tests__/chartsOrderDockCoexistence.recovery-order.test.tsx` | `kpi.order_ui.coexistence_guard.unsaved_return_supported` | 未保存離脱ガード表示後に `order-set` タブへ復帰できる |
| OUI-05 | `docs/web-client/order-ui/charts-order-ui-refactor-plan-20260224.md` / `docs/web-client/CURRENT.md` / `docs/DEVELOPMENT_STATUS.md` | `web-client/src/features/charts/__tests__/chartsActionBar.orca-send.test.tsx` | `kpi.order_ui.compatibility_and_measurement.recorded` | 単独RP/複数RP送信回帰なし + typecheck/test 群 PASS を実施記録へ残す |

## 3. 今回実装結果の要約

- RP主軸化: 処方/注射RPの編集・再編集・送信回帰を重点対象として維持。
- 共存ガード: 右欄編集中の下欄タブ操作、未保存離脱ガード、`order-set` 復帰を共存シナリオとして固定化。
- KPI計測: OUI別にイベントキーを定義し、判定KPIと実測記録を `refactor plan` と対応づけ。
- 互換維持: `laboTest`（legacy 検査）表示互換と `onStateChange` / `data-test-id` 互換を維持対象として明示。

## 4. 導線（正本）

- 計画: `docs/web-client/order-ui/charts-order-ui-refactor-plan-20260224.md`
- ハブ: `docs/web-client/CURRENT.md`
- 単一参照: `docs/DEVELOPMENT_STATUS.md`
