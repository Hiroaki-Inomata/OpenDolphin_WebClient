# Charts オーダーUI再編計画（右欄アコーディオン + 下部フローティング段階統合）

- 作成日: 2026-02-24
- RUN_ID: 20260224T084533Z
- 最終更新RUN_ID: 20260224T213000Z
- 対象: `web-client/src/features/charts/OrderDockPanel.tsx` / `web-client/src/features/charts/pages/ChartsPage.tsx`
- 目的: 右側オーダー欄の情報過密を解消しつつ、既存オーダーの確認/編集の見失いを防止する

## 1. 背景

- 現状は `SOAP右欄(OrderDockPanel)` と `下部フローティング(セット/スタンプ・文書・画像)` が並立しており、役割境界が曖昧。
- 右欄はカテゴリ導線・説明ラベル・重複操作が多く、視認性と入力集中を阻害。
- 一方で、右欄を即時撤去して下部へ全面統合すると、既存オーダー確認/編集の文脈ロストが発生しやすい。

## 2. 方針（最終決定）

短期は「両立」、中期で「条件付き統合」とする。

1. 短期（Phase 1）:
- 右欄は「既存オーダー確認/編集」の主導線に特化し、アコーディオン化で視認性を上げる。
- 下部フローティングは「新規追加/補助操作」に限定する。

2. 中期（Phase 2）:
- 下部に `order` タブを追加し、Bottom Sheet内に「既存オーダー」タブを持つ注文ハブへ段階統合する。
- KPIが悪化しないことを確認してから、右欄を縮退/撤去する。

## 3. 実施項目

### 3.1 Phase 1（短期, 両立強化）

- `OrderDockPanel` をアコーディオン主体に再編し、既存オーダー確認導線を上位に配置する。
- 右欄上部へ「編集中固定サマリー（患者/対象/未保存/最終更新）」を常設する。
- 下部フローティングから既存オーダー編集を直接行わない（編集導線は右欄へ一意化）。
- 右欄の冗長要素を削減（段階ラベル、重複追加導線、重複頻用導線）。

### 3.2 Phase 2（中期, 条件付き統合）

- `ChartsPage` のユーティリティに `order` タブを追加し、Bottom Sheetで既存オーダー一覧/編集を提供する。
- 既存オーダー一覧には検索・絞り込み・未保存バッジ・警告バッジを実装する。
- 編集後は一覧の元位置へ復帰し、文脈ロストを防止する。
- 右欄の機能を段階的に read-only サマリーへ縮退し、最終的に撤去可否を判断する。

## 4. ガードレール（必須）

1. 既存編集導線は常に1つだけにする（Phase 1は右欄、Phase 2以降は注文ハブ）。
2. 未保存状態は常時可視化する（サマリー/タブ/一覧の3点で同一状態を表示）。
3. 編集対象選択時は該当セクションを自動展開し、フォーカス移動で対象を明示する。
4. 画面離脱・タブ切替時は未保存確認を必須にする。

## 5. 進捗管理（追跡用）

| ID | ステータス | 内容 | 主要ファイル |
|---|---|---|---|
| OUI-01 | 完了 | 右欄不要UIの一次削減（段階ラベル/重複操作） | `web-client/src/features/charts/OrderDockPanel.tsx` |
| OUI-02 | 完了 | 右欄アコーディオン化 + 編集中固定サマリー + RP連続編集/互換回帰 | `web-client/src/features/charts/OrderDockPanel.tsx`, `web-client/src/features/charts/orderCategoryRegistry.ts` |
| OUI-03 | 完了 | 右欄/下部の状態同期（未保存・選択対象） | `web-client/src/features/charts/pages/ChartsPage.tsx`, `web-client/src/features/charts/SoapNotePanel.tsx` |
| OUI-04 | 完了 | 下部 `order` タブ操作時の共存ガード/復帰 | `web-client/src/features/charts/pages/ChartsPage.tsx` |
| OUI-05 | 完了 | KPI計測と判定記録を docs へ反映 | `docs/DEVELOPMENT_STATUS.md`, `docs/web-client/CURRENT.md`, `docs/web-client/order-ui/charts-order-ui-regression-test-notes-20260224.md` |

### 5.1 OUI-01〜OUI-05 追跡マトリクス（1:1, 監査用）

| OUI-ID | 対応コード (file) | 対応テスト (test) | KPIイベント | 判定KPI |
|---|---|---|---|---|
| OUI-01 | `web-client/src/features/charts/OrderDockPanel.tsx`（quick-add/group-add 導線） | `web-client/src/features/charts/__tests__/orderDockPanel.categoryButtons.test.tsx` | `kpi.order_ui.quick_add_group_add.maintained` | quick-add/group-add の `data-test-id` が 10/10 維持される |
| OUI-02 | `web-client/src/features/charts/OrderDockPanel.tsx` / `web-client/src/features/charts/orderCategoryRegistry.ts`（RP編集導線/インライン編集/legacy検査互換） | `web-client/src/features/charts/__tests__/orderDockPanel.state-compat-and-rp-regression.test.tsx` / `web-client/src/features/charts/__tests__/orderCategoryRegistry.test.ts` | `kpi.order_ui.rp_primary_and_legacy_compat.stable` | 複数RP連続編集・単独RP保存再編集 + legacy `laboTest` 表示互換が回帰しない |
| OUI-03 | `web-client/src/features/charts/pages/ChartsPage.tsx`（右欄状態を下欄タブへ反映） | `web-client/src/features/charts/__tests__/chartsOrderDockCoexistence.recovery-order.test.tsx` | `kpi.order_ui.coexistence_guard.edit_state_persisted` | 右欄編集中に下欄タブ操作しても編集状態が維持される |
| OUI-04 | `web-client/src/features/charts/pages/ChartsPage.tsx`（下欄 `order-set` タブ運用） | `web-client/src/features/charts/__tests__/chartsOrderDockCoexistence.recovery-order.test.tsx` | `kpi.order_ui.coexistence_guard.unsaved_return_supported` | 未保存離脱ガード表示後に `order-set` タブへ復帰できる |
| OUI-05 | `docs/web-client/order-ui/charts-order-ui-refactor-plan-20260224.md` / `docs/web-client/order-ui/charts-order-ui-implementation-trace-20260224.md` / `docs/web-client/CURRENT.md` / `docs/DEVELOPMENT_STATUS.md` | `web-client/src/features/charts/__tests__/chartsActionBar.orca-send.test.tsx` | `kpi.order_ui.compatibility_and_measurement.recorded` | 単独RP/複数RP送信回帰なし + typecheck/test 群 PASS を実施記録へ残す |

### 5.2 KPI計測結果（2026-02-24 / RUN_ID=20260224T113000Z）

| OUI | 実測結果 | 判定 |
|---|---|---|
| OUI-01 | `orderDockPanel.categoryButtons.test.tsx` PASS（5/5）で quick-add/group-add `data-test-id` 維持 | 達成 |
| OUI-02 | `orderDockPanel.state-compat-and-rp-regression.test.tsx` PASS（4/4）+ `orderCategoryRegistry.test.ts` PASS（2/2） | 達成 |
| OUI-03 | `chartsOrderDockCoexistence.recovery-order.test.tsx` で右欄編集中の下欄操作ガード/継続が PASS（1/1） | 達成 |
| OUI-04 | 同テストで未保存離脱ガード後の `order-set` 復帰が PASS（1/1） | 達成 |
| OUI-05 | `chartsActionBar.orca-send.test.tsx` PASS（9/9）+ typecheck PASS + 指定11ファイル PASS（97 tests）+ 追加回帰 PASS（7 tests） | 達成 |

## 6. KPI / 切替判定

右欄縮退/撤去の判定条件は以下を満たすこと。

- 既存オーダー編集完了時間: 現状比で悪化しない
- 編集ミス/見落とし率: 増加しない
- 未保存取りこぼし: 減少または同等

## 7. 参照ドキュメント

- `docs/web-client/order-ui/charts-order-dock-20260215.md`
- `docs/web-client/ux/charts-order-panel-floating-layout-20260211.md`
- `docs/web-client/order-ui/charts-order-ui-implementation-trace-20260224.md`
- `docs/web-client/CURRENT.md`
- `docs/DEVELOPMENT_STATUS.md`
