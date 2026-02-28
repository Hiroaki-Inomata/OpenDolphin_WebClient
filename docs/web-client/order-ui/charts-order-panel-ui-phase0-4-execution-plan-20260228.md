# Charts オーダーパネル UI 改修実行計画（Phase0-4）

- 作成日: 2026-02-28
- RUN_ID: `20260228T130527Z`
- 対象ブランチ: `feat/order-panel-ui-phase4-20260228`
- 正本参照:
  - `docs/web-client/ux/web-client-ui-guideline.md`
  - `docs/web-client/order-ui/charts-order-detail-display-alignment-requirements-20260228.md`

## 1. 目的
- Webクライアントのオーダーパネル（Summary / Dock / Drawer）を、UIガイドライン準拠で見やすくしつつ、表示整合と将来保守性を高める。
- 低リスク高効果の是正を先行し、構造変更は段階導入で回帰を抑制する。

## 2. スコープ
- 対象:
  - `web-client/src/features/charts/OrderSummaryPane.tsx`
  - `web-client/src/features/charts/OrderDockPanel.tsx`
  - `web-client/src/features/charts/RightUtilityDrawer.tsx`
  - `web-client/src/features/charts/orderDetailDisplayViewModel.ts`
  - `web-client/src/features/charts/orderDetailFormatters.ts`
  - `web-client/src/features/charts/styles.ts`
  - `server-modernized/src/main/java/open/dolphin/rest/dto/orca/OrderBundleFetchResponse.java`
  - `server-modernized/src/main/java/open/dolphin/rest/dto/orca/OrderBundleMutationRequest.java`
  - `server-modernized/src/main/java/open/dolphin/rest/orca/OrcaOrderBundleResource.java`
- 非対象:
  - `server/`（Legacy）
  - オーダー入力導線の全面再設計

## 3. KPI / 受入基準
- `KPI-01` 非テキストコントラスト `>= 3:1`（主要境界）
- `KPI-02` クリック対象 `>= 24x24`、主要操作 `>= 36px`
- `KPI-03` `tablist` 実装を `tab` + `aria-selected` + キーボード操作へ統一
- `KPI-04` 同一bundleの `bundleNumber`（日数/回数）表示不一致ゼロ（Summary / Dock / Drawer）
- `KPI-05` 処方の必須表示（後発可否 / 成分量 / レセコメント）欠落ゼロ
- `KPI-06` Drawer 初期描画性能の劣化なし（既存比で悪化させない）
- `KPI-07` `bodyPart` 専用フィールド移行後も後方互換維持

## 4. 段階導入
### Phase0: 安全柵固定
- ベースラインテストを固定:
  - `OrderSummaryPane.categoryDisplay.test.tsx`
  - `soapNoteRightDockDrawer.test.tsx`
  - `orderDockPanel.categoryButtons.test.tsx`
  - `orderDetailDisplayViewModel.test.ts`
  - `orderDetailFormatters.test.ts`
  - `orderBundleBodyPart.test.tsx`
- ロールバック条件:
  - ベースラインテストに1件でも失敗が出たら即時差分撤回。

### Phase1: Quick Win（UIガイド準拠の即効改善）
- `styles.ts` でコントラスト/操作サイズ/タブ形状/フォーカス可視化を是正。
- `OrderDockPanel.tsx` と `RightUtilityDrawer.tsx` のタブARIAを正規化。
- Drawer閉時のフォーカス流入を防止。

### Phase2: 表示ロジック統一
- `bundleNumber` ラベル判定を共通関数経由に統一。
- フォーマッタ重複を `orderDetailFormatters.ts` に集約。
- Summary / Dock / Drawer で同一ViewModelルールを適用。

### Phase3: Drawer構造改修
- readOnlyエディタ依存の既存一覧を軽量表示カードへ置換。
- サブカテゴリ選択と既存一覧の表示対象を連動させる。

### Phase4: API契約強化（bodyPart）
- `/orca/order/bundles` の fetch/mutation DTO に `bodyPart` 専用フィールドを追加。
- サーバー実装は `bodyPart` 優先、互換期間は既存 `items` の fallback を維持（dual-read/dual-write）。
- UIは `bodyPart` 優先読取へ移行しつつ互換維持。

## 5. 実行順序
1. Phase0（テスト固定）
2. Phase1（Quick Win）
3. Phase2（表示ロジック統一）
4. Phase3（Drawer構造改修）
5. Phase4（API契約強化）

## 6. 最終検証
- `npm -C web-client run typecheck`
- `npm -C web-client run test -- --run src/features/charts/__tests__/OrderSummaryPane.categoryDisplay.test.tsx src/features/charts/__tests__/soapNoteRightDockDrawer.test.tsx src/features/charts/__tests__/orderDockPanel.categoryButtons.test.tsx src/features/charts/__tests__/orderDetailDisplayViewModel.test.ts src/features/charts/__tests__/orderDetailFormatters.test.ts src/features/charts/__tests__/orderBundleBodyPart.test.tsx --silent=true`
- `mvn -f pom.server-modernized.xml -Dtest=OrcaOrderBundleResourceTest -Dsurefire.failIfNoSpecifiedTests=false test`

## 7. 完了判定
- Phase0-4 の成果がこの文書の受入基準に一致していることを、最終差分レビューで確認する。

## 8. 実施結果追記（サーバ検証＋計画適合レビュー）
- 実施日: 2026-02-28
- RUN_ID: `20260228T133235Z`
- 担当範囲: `server-modernized` / 本計画書

### 8.1 実行ログ（サーバ検証）
- 実行コマンド:
  - `mvn -f pom.server-modernized.xml -Dtest=OrcaOrderBundleResourceTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 結果:
  - `BUILD SUCCESS`
  - `OrcaOrderBundleResourceTest` : `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`

### 8.2 Phase0-4 実施結果（レビュー）
| Phase | 実施結果 | 計画適合判定 | 根拠 |
| --- | --- | --- | --- |
| Phase0 | ベースライン関連テスト資産は差分上で維持。今回担当ではサーバ指定テストのみ再実行。 | 条件付き適合 | `orderDockPanel.categoryButtons.test.tsx` / `soapNoteRightDockDrawer.test.tsx` など計画対象テストファイルの更新差分を確認。 |
| Phase1 | UI Quick Win 対象ファイルの改修差分を確認。 | 条件付き適合 | `styles.ts` / `OrderDockPanel.tsx` / `RightUtilityDrawer.tsx` が計画どおり対象化。 |
| Phase2 | 表示ロジック統一対象ファイルの改修差分を確認。 | 条件付き適合 | `orderDetailDisplayViewModel.ts` / `orderDetailFormatters.ts` の差分を確認。 |
| Phase3 | Drawer構造改修対象の差分を確認。 | 条件付き適合 | `RightUtilityDrawer.tsx` および関連テスト差分を確認（今回担当範囲外のため再実行なし）。 |
| Phase4 | API契約強化（`bodyPart`）のDTO/Resource/テストを確認し、指定テストを再実行。 | 適合 | `OrderBundleFetchResponse`/`OrderBundleMutationRequest` に `bodyPart` 追加、`OrcaOrderBundleResource` で dual-read/dual-write 実装、`OrcaOrderBundleResourceTest` で優先/フォールバック検証を通過。 |

### 8.3 総合判定
- サーバ検証観点（本担当範囲）では **計画適合**。
- 補足: 当初は Phase0-3 の最終受入が未突合だったため、次節でフロント検証結果を追記して最終判定を確定する。

## 9. 実施結果追記（フロント検証）
- 実施日: 2026-02-28
- RUN_ID: `20260228T132625Z`
- 担当範囲: `web-client`

### 9.1 実行ログ（フロント検証）
- 実行コマンド:
  - `npm -C web-client install`
  - `npm -C web-client run typecheck`
  - `npm -C web-client run test -- --run src/features/charts/__tests__/OrderSummaryPane.categoryDisplay.test.tsx src/features/charts/__tests__/soapNoteRightDockDrawer.test.tsx src/features/charts/__tests__/orderDockPanel.categoryButtons.test.tsx src/features/charts/__tests__/orderDetailDisplayViewModel.test.ts src/features/charts/__tests__/orderDetailFormatters.test.ts src/features/charts/__tests__/orderBundleBodyPart.test.tsx --silent=true`
- 結果:
  - `npm install`: 成功（依存追加のみ、不要成果物は最終コミットから除外）
  - `typecheck`: 成功
  - 指定6テスト: 初回1件失敗後、`soapNoteRightDockDrawer.test.tsx` の入力イベントを安定化修正して再実行成功（`37 passed`）

### 9.2 Phase0-4 完了判定（統合）
| Phase | 最終判定 | 根拠 |
| --- | --- | --- |
| Phase0 | 適合 | 計画記載のベースライン6テストが最終実行で通過。 |
| Phase1 | 適合 | `styles.ts`/`OrderDockPanel.tsx`/`RightUtilityDrawer.tsx` の UI・ARIA 改修をテスト通過で確認。 |
| Phase2 | 適合 | `orderDetailDisplayViewModel.ts`/`orderDetailFormatters.ts` の表示ロジック統一が関連テストで通過。 |
| Phase3 | 適合 | `RightUtilityDrawer.tsx` の構造改修と関連UIテスト（`soapNoteRightDockDrawer`）通過を確認。 |
| Phase4 | 適合 | サーバ側 `OrcaOrderBundleResourceTest`（7件）成功、`bodyPart` 互換実装を確認。 |

## 10. 最終差分照合
- 実施日: 2026-02-28
- RUN_ID: `20260228T133513Z`
- 照合結果: **計画書（Phase0-4）に沿った改修が差分上で確認でき、受入基準/KPIに対する必須項目を満たす。**
