# Charts オーダーパネル改善（RUN_ID: 20260211T203337Z）

## 目的
- カルテ入力画面の横長モニター利用を前提に、オーダー入力の視認性と同時操作性を改善する。
- カルテ画面を操作しながらオーダー入力できるよう、モーダル型の拘束をなくす。

## 実施内容
- 右パネルの「病名へ移動」導線を削除。
- ユーティリティ内の「診療操作」タブを削除。
- ユーティリティ展開時のオーバーレイ/疑似モーダル挙動を廃止。
- オーダーパネルは SOAP 記載領域の内部重ね表示ではなく、オーダー情報の右側カラムにドッキング表示。
- カルテ本体（左/中央カラム）の幅を圧縮し、右側ユーティリティ列を確保するレイアウトへ変更。
- 右カラムパネルをリサイズ可能化（右下ハンドル）。
- パネルサイズ/位置を端末ローカルへ保存し、次回表示時に復元。
  - 保存キー: `opendolphin:web-client:charts:utility-panel-layout:v1:<facilityId>:<userId>`
  - 旧互換キー: `opendolphin:web-client:charts:utility-panel-layout:v1`
- `OrderBundleEditPanel` を2カラム化。
  - 左: 頻用オーダー、スタンプ保存/取り込み
  - 右: 通常入力フォーム、登録済み一覧
- 左右カラムは独立スクロール（部分スクロール）に対応。

## 期待効果
- 横長ディスプレイで、頻用操作と通常入力を同時視認しやすくなる。
- オーダー入力を開いたまま SOAP/病名/サマリ参照が可能になる。
- 利用端末ごとにパネル配置を最適化しやすくなる。

## 検証
- `npm -C web-client run typecheck`
- `npm -C web-client run test -- --run src/features/charts/__tests__/orderBundleMasterSearch.test.tsx src/features/charts/__tests__/orderBundleBodyPart.test.tsx src/features/charts/__tests__/orderBundleStampFlow.test.tsx src/features/charts/__tests__/chartsMasterSourceCache.test.tsx --silent=true`
- `npm -C web-client run test -- --run src/features/charts/__tests__/orderBundleItemActions.test.tsx src/features/charts/__tests__/orderBundleUsageSearch.test.tsx src/features/charts/__tests__/orderBundleValidation.test.ts --silent=true`
