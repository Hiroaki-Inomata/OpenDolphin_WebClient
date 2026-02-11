# Reception 状態タブ・日次状態保存（2026-02-11）

- RUN_ID: `20260211T223228Z`
- 対象: `web-client/src/features/reception/pages/ReceptionPage.tsx`, `web-client/src/features/charts/pages/ChartsPage.tsx`, `web-client/src/features/charts/PatientsTab.tsx`

## 目的
- 受付画面で患者カードが状態に応じて移動する運用を明確化する。
- 日付ごとに患者配列と状態を保存し、過去日付を選択した際にも当日の状態を復元できるようにする。
- Reception の既定日付を「カルテで開いている診療日」に寄せる。

## 実装概要
1. `Reception` に状態タブ UI を追加。
- `すべて / 予約 / 受付 / 診療中 / 診察後 / 会計済み` のタブで表示を切り替え可能。
- `会計待ち` は表示上 `診察後（会計待ち）` として扱う。

2. 日次状態ストアを新設（`receptionDailyState.ts`）。
- `localStorage` に日付単位で患者配列を保存。
- 患者ID単位で状態オーバーライドを保持。
- API 取得が空でも保存済み日次配列を復元して表示。

3. 日付移動 UI を追加。
- `前日 / 今日 / 翌日 / カルテ日` ボタンを追加。
- 保存済み日付チップから任意日へジャンプ可能。
- 取得元表示（`API`, `API+保存履歴`, `保存済み履歴`）を明示。

4. Charts から日次状態へ反映。
- Charts で患者を開いた際に `診療中` を日次状態へ反映。
- `診療終了` 実行時に `会計待ち`（診察後）へ更新。
- Reception 遷移時の `date/visitDate` 引き継ぎを追加。

## 検証
- `npm -C web-client run typecheck`
- `npm -C web-client run test -- --run src/features/reception/__tests__/ReceptionPage.test.tsx src/features/reception/__tests__/receptionDailyState.test.ts --silent=true`
- `npm -C web-client run test -- --run src/features/charts/__tests__/chartsActionBar.test.tsx src/features/charts/__tests__/patientsTabDraftDialog.test.tsx --silent=true`

