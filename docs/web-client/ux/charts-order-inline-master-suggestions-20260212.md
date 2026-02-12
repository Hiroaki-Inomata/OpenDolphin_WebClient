# Charts オーダーパネル: 入力欄統合候補UI（2026-02-12）

- RUN_ID: `20260212T210125Z`
- 対象: `web-client/src/features/charts/OrderBundleEditPanel.tsx`

## 背景
- 既存のオーダーパネルでは、実際の入力欄とは別に「マスタ検索」「用法検索」「コメントマスタ検索」の独立セクションがあり、同じ入力目的に対して操作経路が重複していた。
- 処方薬剤・検査名・コメント候補を、実際の入力欄からそのまま部分一致候補選択できる導線へ統合する必要があった。

## 実装内容
- 主項目（薬剤/検査/処置など）:
  - 独立したマスタ検索欄を撤去。
  - 主項目の入力文字列をそのまま ORCA マスタへ問い合わせ、候補テーブルへ部分一致表示。
  - 候補選択時にコード/名称/単位/備考を同一行へ自動反映。
  - 検索対象プリセット（例: 注射薬剤/注射手技、画像検査/画像器材/造影薬剤）は主項目セクション内へ集約。
- 用法:
  - 独立した用法検索セクションのキーワード・フィルタUIを撤去。
  - 用法入力欄への入力をそのまま部分一致検索に利用し、候補選択で用法欄を自動入力。
- コメント:
  - 独立したコメント検索 + プルダウンUIを撤去。
  - コメント内容入力欄に対して部分一致候補を表示し、候補選択でコード/名称を自動反映。
  - `medicationgetv2` の選択式コメント候補は主項目コード入力時に従来どおり提示し、コメントへ追加可能。

## テスト
- `npm -C web-client run typecheck`
- `npm -C web-client run test -- --run src/features/charts/__tests__/orderBundleMasterSearch.test.tsx src/features/charts/__tests__/orderBundleUsageSearch.test.tsx src/features/charts/__tests__/orderBundleItemActions.test.tsx src/features/charts/__tests__/orderBundleBundleNumberUi.test.tsx src/features/charts/__tests__/contraindicationWarning.test.tsx src/features/charts/__tests__/orderBundleBodyPart.test.tsx --silent=true`

