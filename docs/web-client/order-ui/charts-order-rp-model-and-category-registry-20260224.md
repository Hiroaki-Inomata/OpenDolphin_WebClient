# Charts オーダー: RPモデル/カテゴリ単一化 実装ノート

- 更新日: 2026-02-24
- RUN_ID: `20260224T100000Z`

## 目的
- ORCA 送信仕様に合わせ、薬剤/注射を RP（bundle）単位で扱う実装を明確化する。
- 処方/注射/処置/検査/算定のカテゴリ定義を単一化し、UI・バリデーション・送信マッピングの不整合を防ぐ。

## 実装概要
### 1. カテゴリ/エンティティ定義の単一化
- 追加: `web-client/src/features/charts/orderCategoryRegistry.ts`
- 集約した要素:
  - 表示名（entity/group）
  - グルーピング（`OrderGroupKey`）
  - 検索プリセット（`masterSearchPresets` / `defaultMasterSearchType`）
  - バリデーション要件（`requiresUsage` など）
  - 送信クラス既定値（`classCode` / `className`）
  - etensuカテゴリ
  - 編集画面メタ（タイトル/ラベル）

### 2. RP主軸UI（処方/注射）と既存カテゴリ整合
- `OrderDockPanel` で registry を参照するよう変更。
- quick-add / group-add の `data-test-id` と onStateChange は互換維持。
- 折りたたみ未展開時は本文非マウントを維持。
- 不要UI（送信状態表示）を削除済み前提でテストを更新。

### 3. ORCA送信整合（medicalmodv2）
- `ChartsActionBar` を registry 参照へ変更し、送信対象entity/クラスフォールバックを一元化。
- 送信前に、薬剤RP/注射RPについて必須項目を検証:
  - `Medical_Class`
  - `Medical_Class_Number`
  - `Medication_info`
- 欠落時は送信を停止し、理由を banner に表示。

## RP -> payload 変換方針
- RP（bundle）1件を `Medical_Information_child` 1件へ変換。
- RPヘッダ:
  - `classCode`（またはentity既定） -> `Medical_Class`
  - `bundleNumber` -> `Medical_Class_Number`
- RP明細:
  - `items[]`（有効コード行） -> `Medication_info[]`

## 互換I/F
- 維持:
  - `order-dock-quick-add-*`
  - `order-dock-group-add-*`
  - `onStateChange(hasEditing, targetCategory, count)`
- 既存カテゴリ（処置/検査/算定）は従来操作を維持しつつ registry 参照へ統合。

## テスト
- 追加: `orderCategoryRegistry.test.ts`
- 追加: `chartsActionBar.orca-send.test.tsx`（RP必須項目欠落の送信停止）
- 更新: `orderDockPanel.categoryButtons.test.tsx`（不要UI削除/折りたたみ表示仕様に追従）
