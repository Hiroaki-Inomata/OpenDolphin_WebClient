# cmd_20260218_01_sub_7 調査結果（guard triage）

## 1. 再現条件と証跡

### A) 失敗側（先行証跡）
- 既存証跡: `artifacts/verification/20260218T065900Z-cmd_20260218_01_sub_5-major-category-final-verify/summary.json`
- 事象:
  - `bootstrapReady=false`
  - 画面表示 `master未同期`
  - `masterResponses: []`（`/orca/master/*` 未発火）
  - `+処方/+注射/+検査/+処置/+算定` 到達前に停止

### B) 対照側（今回採取）
- 新規証跡: `artifacts/verification/20260217T235005Z-cmd_20260218_01_sub_7-guard-triage/summary.json`
- 事象:
  - `patientSummaryMissingMasterAttr=false`
  - quick-add 5ボタンが `visible=true / disabled=false`
  - `+処方` クリック成功
- 追試証跡: `artifacts/verification/20260217T235005Z-cmd_20260218_01_sub_7-guard-triage/control.json`
  - `missingMaster=false` の状態で `#medOrder-item-name-0` に `アム` 入力
  - `/orca/master/generic-class?...` が `200` 応答で1件発火

## 2. 停止ガード（コード行単位）

1) オーダー導線停止ガード
- `web-client/src/features/charts/OrderDockPanel.tsx:342`
  - `const canEdit = Boolean(patientId && !meta.readOnly && !meta.missingMaster && !meta.fallbackUsed);`
- `web-client/src/features/charts/OrderDockPanel.tsx:584`
  - quick-add ボタンが `disabled={!canEdit}`
- `web-client/src/features/charts/OrderDockPanel.tsx:506`
  - `!canEdit` 時は `openEditor` に進まず即 return

2) ガード値の注入経路
- `web-client/src/features/charts/pages/ChartsPage.tsx:1710`
  - `resolveOutpatientFlags(claimQuery.data, orcaSummaryQuery.data, appointmentMeta, flags)`
- `web-client/src/features/charts/pages/ChartsPage.tsx:1713`
  - `missingMaster = resolvedFlags.missingMaster ?? flags.missingMaster`
- `web-client/src/features/charts/pages/ChartsPage.tsx:2543`
  - `sidePanelMeta.missingMaster` として各編集UIへ伝播

3) フォールバック元（missingMasterの既定値）
- `web-client/src/features/charts/authService.tsx:31`
  - `DEFAULT_FLAGS.missingMaster = true`
- `web-client/src/features/outpatient/flags.ts:17`
  - `resolveOutpatientFlags` は「最初の defined 値」を採用
- `web-client/src/features/reception/api.ts:569`
  - `fetchClaimFlags` は現状 disabled payload を返し、`missingMaster` を確定供給しない

## 3. 根因
- 実害は `OrderDockPanel.canEdit` ガードで、`missingMaster=true` の間は quick-add/編集導線が止まり、結果として `orderMasterSearchApi` 起点の `/orca/master/*` が発火しない点。
- 先行失敗証跡（sub_5）はまさにこの状態に一致（UIが `master未同期` で master API 0件）。

## 4. 最小差分の修正候補（1案）
- 目的: `dataSourceTransition=server` なのに `missingMaster` が未確定な瞬間に、`DEFAULT_FLAGS(true)` へ不必要にフォールバックして編集導線を閉じる挙動を避ける。
- 候補（`ChartsPage.tsx` の局所修正）:

```ts
// before
const missingMaster = resolvedFlags.missingMaster ?? flags.missingMaster;

// after (candidate)
const inferredMissingMaster =
  resolvedFlags.missingMaster ??
  (resolvedFlags.dataSourceTransition === 'server' ? false : undefined);
const missingMaster = inferredMissingMaster ?? flags.missingMaster;
```

- 影響範囲: `web-client/src/features/charts/pages/ChartsPage.tsx` の `mergedFlags` 箇所のみ。
- 意図: server到達時の unknown を pessimistic(true) へ落とさず、false推定で不要ガードを回避。

## 5. 保存済み成果物
- `artifacts/verification/20260217T235005Z-cmd_20260218_01_sub_7-guard-triage/repro-sub7.mjs`
- `artifacts/verification/20260217T235005Z-cmd_20260218_01_sub_7-guard-triage/summary.json`
- `artifacts/verification/20260217T235005Z-cmd_20260218_01_sub_7-guard-triage/summary.md`
- `artifacts/verification/20260217T235005Z-cmd_20260218_01_sub_7-guard-triage/repro-sub7-control.mjs`
- `artifacts/verification/20260217T235005Z-cmd_20260218_01_sub_7-guard-triage/control.json`
- `artifacts/verification/20260217T235005Z-cmd_20260218_01_sub_7-guard-triage/screenshots/sub7_guard_probe.png`
