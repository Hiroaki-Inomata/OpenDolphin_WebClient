# Reception 状態タブ・日次状態保存（2026-02-11）

- RUN_ID: `20260211T223228Z`
- 更新: 2026-02-13（RUN_ID: `20260213T114440Z`、前回: `20260213T064029Z`）
- 対象: `web-client/src/features/reception/pages/ReceptionPage.tsx`, `web-client/src/features/charts/pages/ChartsPage.tsx`, `web-client/src/features/charts/PatientsTab.tsx`

## 目的
- 受付画面で患者カードが状態に応じて移動する運用を明確化する。
- 日付ごとに患者配列と状態を保存し、過去日付を選択した際にも当日の状態を復元できるようにする。
- カルテ（Charts）から Reception を開いた時は **既定で当日** を表示し、必要に応じて「カルテ日」へジャンプできるようにする。

## 実装概要
1. `Reception` に状態タブ UI を追加。
- `すべて / 予約 / 診察待ち / 診察中 / 診察終了 / 会計済み` のタブで表示を切り替え可能（予約は ORCA 予約一覧の表示）。
- 内部ステータスの表示名は以下へ寄せる:
  - `受付中` → `診察待ち`
  - `診療中` → `診察中`
  - `会計待ち` → `診察終了`

2. 日次状態ストアを新設（`receptionDailyState.ts`）。
- `localStorage` に日付単位で患者配列を保存。
- 患者ID単位で状態オーバーライドを保持。
- API 取得が空でも保存済み日次配列を復元して表示。

3. 日付移動 UI を追加。
- `前日 / 今日 / 翌日 / カルテ日` ボタンを追加。
- 保存済み日付チップから任意日へジャンプ可能。
- 取得元表示（`API`, `API+保存履歴`, `保存済み履歴`）を明示。
- Charts→Reception 遷移では `from=charts` + `visitDate` をヒントとして渡し、Reception 側は `date` 未指定時は当日を表示する。

4. Charts から日次状態へ反映。
- Charts で患者を開いた際に `診療中` を日次状態へ反映（source=`charts_open`）。
- `診療終了` 実行時に `会計待ち`（診察後）へ更新。
- Reception の「会計送信」実行時に `会計済み` へ更新し、ボード上の最終列へ移動。

## 検証
- `npm -C web-client run typecheck`
- `npm -C web-client run test -- --run src/features/reception/__tests__/ReceptionPage.test.tsx src/features/reception/__tests__/receptionDailyState.test.ts --silent=true`
- `npm -C web-client run test -- --run src/features/charts/__tests__/chartsActionBar.test.tsx src/features/charts/__tests__/patientsTabDraftDialog.test.tsx --silent=true`
