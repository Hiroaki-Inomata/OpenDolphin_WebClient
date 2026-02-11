# Charts オーダー画面 ORCAマスタ連携強化

- 実施日: 2026-02-11
- RUN_ID: 20260211T210116Z
- 対象: `web-client/src/features/charts/OrderBundleEditPanel.tsx`

## 1. モダナイズ版サーバー ORCAマスタAPI 網羅調査

`server-modernized/src/main/java/open/orca/rest/OrcaMasterResource.java` の実装済みエンドポイントは以下。

| API | 主用途 | オーダー画面との関係 |
| --- | --- | --- |
| `GET /orca/master/generic-class` | 薬効分類/薬剤候補検索 | 処方/注射/造影薬剤の候補検索に使用 |
| `GET /orca/master/generic-price` | 単一薬剤コードの薬価参照 | 今回は未接続（価格表示要件なし） |
| `GET /orca/master/youhou` | 用法マスタ検索 | 処方の用法プルダウン候補に使用 |
| `GET /orca/master/material` | 特定器材マスタ検索 | 材料検索に使用（既存） |
| `GET /orca/master/kensa-sort` | 検査区分マスタ検索 | 検査区分検索に使用（既存） |
| `GET /orca/master/hokenja` | 保険者マスタ検索 | 今回は未接続（オーダー画面要件外） |
| `GET /orca/master/address` | 郵便番号→住所検索 | 今回は未接続（オーダー画面要件外） |
| `GET /orca/master/etensu` | 点数/部位/コメント帯検索 | 点数検索・部位検索・コメント検索に使用 |

補足:
- 認証は `userName` / `password` ヘッダー（既存仕様）
- `etensu` は `category` により用途を切替（コメント帯=8, 部位帯=2）

## 2. 今回の実装（オーダー画面）

### 2.1 リアルタイム予測候補表示
- 主項目入力（項目名）に対し、現在選択中の検索プリセット（例: 処方薬剤、検査項目など）で ORCA マスタをリアルタイム検索。
- 候補を `datalist` で即時提示し、候補確定時にコード/単位/備考を行へ補完。

### 2.2 用法入力のプルダウン化
- 処方 (`medOrder`) の用法欄を自由テキストからプルダウン選択へ変更。
- `youhou` マスタ検索結果を候補化し、選択時に用法と補助コードを反映。

### 2.3 コメント入力のプルダウン化
- コメントコードの下書き入力を自由記述中心から候補選択中心へ変更。
- `comment`（`etensu category=8`）検索結果をプルダウンに反映。
- 追加済みコメントのコード/名称は手修正ではなく再選択方式に変更（数量/単位は継続編集可能）。

## 3. 変更ファイル

- `web-client/src/features/charts/OrderBundleEditPanel.tsx`
- `web-client/src/features/charts/__tests__/orderBundleUsageSearch.test.tsx`
- `web-client/src/features/charts/__tests__/orderBundleMasterSearch.test.tsx`
- `web-client/src/features/charts/__tests__/orderBundleBundleNumberUi.test.tsx`
- `web-client/src/features/charts/__tests__/orderBundleItemActions.test.tsx`
- `web-client/src/features/charts/__tests__/contraindicationWarning.test.tsx`
- `web-client/src/features/charts/__tests__/__snapshots__/orderBundleBodyPart.test.tsx.snap`

## 4. 検証

- `npm -C web-client run typecheck`
- `npm -C web-client run test -- --run src/features/charts/__tests__/orderBundleMasterSearch.test.tsx src/features/charts/__tests__/orderBundleBodyPart.test.tsx src/features/charts/__tests__/orderBundleItemActions.test.tsx src/features/charts/__tests__/orderBundleUsageSearch.test.tsx src/features/charts/__tests__/orderBundleBundleNumberUi.test.tsx src/features/charts/__tests__/orderBundleValidation.test.ts src/features/charts/__tests__/contraindicationWarning.test.tsx --silent=true`

上記はすべて PASS。
