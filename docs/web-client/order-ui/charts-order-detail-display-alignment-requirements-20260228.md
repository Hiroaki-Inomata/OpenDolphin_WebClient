# SOAP右基準 オーダー詳細表示アライメント実装要件書

- 作成日: 2026-02-28
- RUN_ID: `20260228T043102Z`
- 対象: SOAP右基準で `OrderSummaryPane` / `OrderDockPanel` / `RightUtilityDrawer` のオーダー詳細表示を統一する

## 1. 背景と目的（表示側主修正、編集側は補助）
- 現状、表示面で情報密度と表記が分断されている。
- `OrderSummaryPane` は処方で `RP番号` `後発可否` `薬剤量/成分量` `レセプトコメント` まで表示できるが、`OrderDockPanel` と `RightUtilityDrawer` は同等情報を保持しながら省略表示になっている。
- `bundleNumber` の意味ラベルが画面で一致していない。処方は画面によって `回数` と `日数` が混在する。
- bodyPart は `002` コード接頭辞で暗黙判定しており、`/orca/order/bundles` DTO に専用フィールドがないため、表示整合の前提が脆弱。
- 本要件は「表示統一」を主対象とし、編集側は表示統一に必要な最小補助修正のみを許容する。

## 2. 対象範囲 / 非対象
| 区分 | 内容 |
|---|---|
| 対象 | SOAP右系の表示ロジック統一（`OrderSummaryPane` / `OrderDockPanel` / `RightUtilityDrawer`） |
| 対象 | 表示用ViewModel・フォーマッタ・ソート・ラベル規約の単一化 |
| 対象 | bodyPart の表示要件定義、API拡張仕様定義、段階導入計画 |
| 対象 | 表示整合に必要な編集パネルの最小補助（値の意味判定に必要なメタ出力） |
| 非対象 | Legacy `server/` の改修 |
| 非対象 | Order入力UXの全面再設計（操作導線・レイアウト刷新） |
| 非対象 | ORCA送信業務ルールの再定義（既存RP必須チェックの意味変更） |

## 3. 画面別 現状差分
| 画面 | 現状 | 差分（問題） | 必須対応 |
|---|---|---|---|
| `OrderSummaryPane` | 処方カードで `RP`・後発可否・薬剤量/成分量・用法/回数・薬剤コメント・レセコメントまで表示 | 最も情報が多く、他画面との整合基準が未定義 | 本画面を表示正本（SOAP右基準）として仕様化し、他画面を追従させる |
| `OrderDockPanel` | カード要約は chip 中心。処方は `用法 / 日数` と薬剤名短縮。後発可否・成分量・レセコメント非表示 | 表示粒度不足。処方の `bundleNumber` が `日数` 固定に寄る | 正本ViewModelを使用し、詳細サマリを同等表示に引き上げる |
| `RightUtilityDrawer` | 既存一覧は `名称 / エンティティ / 項目名3件` のみ | 詳細不足。`bundleNumber` 意味表示なし。処方専用の詳細差分を吸収できない | 一覧表示を正本ViewModelへ置換し、最低限の詳細行を統一表示 |
| 編集パネル（`OrderBundleEditPanel` / `PrescriptionOrderEditorPanel`） | `bundleNumber` は入力側で文脈切替（処方は `日数/回数`、注射は `回数`）。bodyPart は `002` 依存で items 混在 | 表示側へ意味情報を安定供給する契約が弱い | 表示統一に必要な補助のみ実施。編集UIの主設計変更は行わない |

## 4. 種別別 最終要件マトリクス（表示項目 / 優先ソース / 表記規則）
| 種別 | 表示項目（最終） | 優先ソース | 表記規則 |
|---|---|---|---|
| 処方 | 入力者行、RP番号、薬剤名、後発可否、薬剤量、成分量、用法、`bundleNumber`（日数/回数）、薬剤コメント、レセプトコメント | `prescriptionBundles` を最優先。欠損時のみ `orderBundles` の処方グループへフォールバック | `RP{bundleNumber}`。`bundleNumber` ラベルは classCode/タイミングで `日数`/`回数` を判定。コード先頭除去、空値は `未設定/不明` を明示 |
| 注射 | 入力者行、項目名、薬剤量、投与情報（admin/adminMemo）、`bundleNumber` | `orderBundles` の注射グループ | `bundleNumber` は常に `回数`。項目は `名称 + 数量単位`。空値は `投与情報なし` |
| 処置 | 入力者行、項目名、数量単位、必要時メモ | `orderBundles` の処置グループ | `名称 + 数量単位`。空一覧は `項目情報なし` |
| 検査 | 入力者行、項目名、（放射線/リハビリは部位）、必要時メモ | `orderBundles` の検査グループ | 検査は名称中心。bodyPart は専用フィールド優先、未拡張期間は互換抽出 |
| 算定 | 入力者行、項目名、数量単位、項目メモ、束メモ、`bundleNumber` | `orderBundles` の算定グループ | `bundleNumber` は `回数`。メモは `メモ:` 接頭で統一 |
| 文書 | 入力者行、文書名、本文要約（または未取得理由） | document panel 状態を優先。未接続時はフォールバック文言 | 未取得は `文書情報なし / 本文情報なし` を固定。取得済み時は名称を必ず表示 |

## 5. bodyPart 要件（002依存リスクとAPI拡張案）
### 5.1 現状リスク
- クライアント編集は `code.startsWith('002')` で bodyPart を抽出している。
- サーバー推薦テンプレートも同様に `002` 接頭辞で bodyPart を分離している。
- `/orca/order/bundles` Fetch/Mutation DTO は bodyPart 専用フィールドを持たず、`items[]` の暗黙解釈に依存している。
- その結果、`002` 以外のマスタ体系、コード欠損、将来仕様変更時に表示破綻のリスクがある。

### 5.2 API拡張案（必須）
| API | 追加フィールド | 要件 |
|---|---|---|
| `GET /orca/order/bundles` | `bodyPart`（`code/name/quantity/unit/memo`） | `items[]` とは独立して返す。互換期間は従来 `items[]` も返す |
| `POST /orca/order/bundles` | `bodyPart`（同構造） | 受信時は `bodyPart` 優先。未指定時のみ従来 `items[]` の互換解釈 |
| 推薦API | 既存 `template.bodyPart` を継続 | `bundles` 系と同一契約へ合わせる |

### 5.3 移行互換
- Phase1（UIのみ）では `bodyPart` 専用フィールドが無い前提で `002` 互換抽出を残す。
- Phase2（API拡張）で `bodyPart` 専用フィールド優先へ切替。
- Phase3（最終統合）で `002` 依存を削減し、互換コードは監視後に段階撤去する。

## 6. 実装要件（表示ViewModel統一 / フォーマッタ統一 / ソート規則 / ラベル統一）
### 6.1 表示ViewModel統一
- `OrderDetailDisplayViewModel` を新設し、`OrderSummaryPane` `OrderDockPanel` `RightUtilityDrawer` の3画面は必ず同一ビルダー関数を通す。
- ViewModelは最低限、`group` `entity` `operatorLine` `title` `detailLines[]` `chips[]` `bundleNumberLabel` `bundleNumberValue` `warnings` `missingFlags` を持つ。
- 処方の詳細要素（後発可否・成分量・レセコメント）は ViewModel 正規項目として保持し、画面ごとの欠落を禁止する。

### 6.2 フォーマッタ統一
- `normalizeInline` `stripLeadingCode` `formatQuantityWithUnit` `formatDateTime` `memo整形` を共通モジュールへ集約し、各画面の重複実装を禁止する。
- フォーマット結果の同値性を単体テストで担保する。

### 6.3 ソート規則統一
- 3画面すべてで `started desc` → `documentId desc` → `index desc` の同一ルールを適用する。
- `OrderDockPanel` のグループ内一覧は現状順依存を廃止し、正本ソートを適用する。

### 6.4 ラベル統一
- `bundleNumber` のラベル判定を共通関数化し、画面固有判定を禁止する。
- 判定規則は以下を固定する。
- 処方: classCode `22x` または timing=`tonyo` は `回数`。
- 処方: classCode `21x/23x` または timing=`regular/gaiyo` は `日数`。
- 注射/算定: `回数`。
- 判定不能時: `回数・日数` ではなく、診療種別既定（処方は `日数`、他は `回数`）へフォールバック。

## 7. 受入基準
- AC-01: 同一bundleを `OrderSummaryPane` `OrderDockPanel` `RightUtilityDrawer` で表示したとき、要件マトリクスの必須項目欠落がない。
- AC-02: 処方の `bundleNumber` 表記は3画面で同一（`日数`/`回数` 不一致ゼロ）。
- AC-03: 処方の後発可否・成分量・レセコメントが3画面で同一ルール表示される。
- AC-04: 3画面の並び順が同一ソート結果になる。
- AC-05: 文書カテゴリは常に1カードを維持し、未取得時フォールバック文言が崩れない。
- AC-06: bodyPart は API未拡張時も既存表示を維持し、API拡張後は専用フィールド優先で表示される。
- AC-07: 既存のRP必須チェック挙動を変更しない。

## 8. テスト要件（更新対象テスト群）
| 区分 | 更新対象 |
|---|---|
| 表示統一（既存） | `web-client/src/features/charts/__tests__/OrderSummaryPane.categoryDisplay.test.tsx` |
| 表示統一（既存） | `web-client/src/features/charts/__tests__/soapNoteRightDockDrawer.test.tsx` |
| 表示統一（既存） | `web-client/src/features/charts/__tests__/orderDockPanel.categoryButtons.test.tsx` |
| 表記統一（既存） | `web-client/src/features/charts/__tests__/orderBundleBundleNumberUi.test.tsx` |
| bodyPart互換（既存） | `web-client/src/features/charts/__tests__/orderBundleBodyPart.test.tsx` |
| API拡張（既存） | `server-modernized/src/test/java/open/dolphin/rest/orca/OrcaOrderBundleResourceTest.java` |
| 新規追加（必須） | `web-client/src/features/charts/__tests__/orderDetailDisplayViewModel.test.ts`（ViewModelの項目・ラベル・ソート） |
| 新規追加（必須） | `web-client/src/features/charts/__tests__/orderDetailFormatters.test.ts`（共通フォーマッタ同値性） |

## 9. 段階導入計画
### Phase 1: UIのみ（先行）
- 目的: 表示差分を即時解消する。
- 作業: ViewModel統一、フォーマッタ統一、ソート統一、ラベル統一をUI内で完結。
- 完了条件: AC-01〜AC-05 を満たす。

### Phase 2: API拡張
- 目的: bodyPart の暗黙依存を解消する。
- 作業: `/orca/order/bundles` Fetch/Mutation DTO に `bodyPart` 追加、Resourceで読み書き対応、互換維持。
- 完了条件: AC-06 を満たし、既存UIの後方互換を保持する。

### Phase 3: 最終統合
- 目的: UI/API契約を一本化し保守負荷を下げる。
- 作業: `002` 依存抽出を縮退、共通ViewModelを唯一経路化、重複ロジックを削除。
- 完了条件: AC-01〜AC-07 を全充足、追加テストを含めCI緑化。

## 10. 主要コード参照（相対パス + 行番号）
- `web-client/src/features/charts/OrderSummaryPane.tsx:129`（処方詳細の組み立て）
- `web-client/src/features/charts/OrderSummaryPane.tsx:139`（薬剤量/成分量表示）
- `web-client/src/features/charts/OrderSummaryPane.tsx:142`（用法 + `回数` 表記）
- `web-client/src/features/charts/OrderSummaryPane.tsx:147`（レセプトコメント表示）
- `web-client/src/features/charts/OrderSummaryPane.tsx:215`（ソート適用）
- `web-client/src/features/charts/OrderDockPanel.tsx:102`（Dockカード要約生成）
- `web-client/src/features/charts/OrderDockPanel.tsx:125`（処方メタ `日数` 表記）
- `web-client/src/features/charts/OrderDockPanel.tsx:1270`（Dockカードのメタ表示）
- `web-client/src/features/charts/OrderDockPanel.tsx:880`（グループ優先順のみ、グループ内ソート未統一）
- `web-client/src/features/charts/RightUtilityDrawer.tsx:86`（最新順ソート関数）
- `web-client/src/features/charts/RightUtilityDrawer.tsx:126`（Drawer一覧要約は項目名中心）
- `web-client/src/features/charts/RightUtilityDrawer.tsx:372`（Drawer既存一覧レンダリング）
- `web-client/src/features/charts/OrderBundleEditPanel.tsx:199`（`002` bodyPart判定定数）
- `web-client/src/features/charts/OrderBundleEditPanel.tsx:317`（items分解でbodyPart抽出）
- `web-client/src/features/charts/OrderBundleEditPanel.tsx:345`（items再構築でbodyPart先頭混在）
- `web-client/src/features/charts/OrderBundleEditPanel.tsx:1808`（編集側 `日数/回数` 切替）
- `web-client/src/features/charts/OrderBundleEditPanel.tsx:3194`（部位UI）
- `web-client/src/features/charts/PrescriptionOrderEditorPanel.tsx:184`（`bundleNumber` 取込）
- `web-client/src/features/charts/PrescriptionOrderEditorPanel.tsx:978`（処方 `回数/日数` ラベル）
- `web-client/src/features/charts/orderCategoryRegistry.ts:391`（カテゴリ定義）
- `web-client/src/features/charts/orderCategoryRegistry.ts:308`（放射線のbodyPart必須）
- `web-client/src/features/charts/orderBundleApi.ts:16`（OrderBundle型）
- `server-modernized/src/main/java/open/dolphin/rest/dto/orca/OrderBundleFetchResponse.java:65`（`/bundles` DTO、bodyPart専用なし）
- `server-modernized/src/main/java/open/dolphin/rest/dto/orca/OrderBundleMutationRequest.java:32`（Mutation DTO、bodyPart専用なし）
- `server-modernized/src/main/java/open/dolphin/rest/orca/OrcaOrderBundleResource.java:70`（`002` bodyPart接頭辞）
- `server-modernized/src/main/java/open/dolphin/rest/orca/OrcaOrderBundleResource.java:195`（enteredBy付与）
- `server-modernized/src/main/java/open/dolphin/rest/orca/OrcaOrderBundleResource.java:939`（推薦テンプレートのbodyPart抽出）
- `server-modernized/src/main/java/open/dolphin/rest/dto/orca/OrderBundleRecommendationResponse.java:151`（推薦DTOはbodyPart専用あり）
- `web-client/src/features/charts/__tests__/OrderSummaryPane.categoryDisplay.test.tsx:95`（処方詳細表示テスト）
- `web-client/src/features/charts/__tests__/orderBundleBundleNumberUi.test.tsx:130`（`日数/回数` UIテスト）
- `web-client/src/features/charts/__tests__/orderBundleBodyPart.test.tsx:147`（bodyPart保存テスト）
- `server-modernized/src/test/java/open/dolphin/rest/orca/OrcaOrderBundleResourceTest.java:122`（enteredBy検証）
