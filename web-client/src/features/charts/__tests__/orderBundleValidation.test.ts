import { describe, expect, it } from 'vitest';

import { validateBundleForm } from '../OrderBundleEditPanel';

type BundleFormState = Parameters<typeof validateBundleForm>[0]['form'];

const baseForm: BundleFormState = {
  bundleName: '',
  admin: '',
  bundleNumber: '1',
  adminMemo: '',
  memo: '',
  startDate: '2025-12-29',
  prescriptionLocation: 'out',
  prescriptionTiming: 'regular',
  items: [{ name: '', quantity: '', unit: '', memo: '' }],
  materialItems: [],
  commentItems: [],
  bodyPart: null,
};

describe('validateBundleForm', () => {
  it('medOrder: 薬剤/項目・用法を必須として判定する', () => {
    const issues = validateBundleForm({ form: baseForm, entity: 'medOrder', bundleLabel: 'RP名' });
    expect(issues.map((issue) => issue.key)).toEqual(['missing_items', 'missing_usage']);
  });

  it('medOrder: 必須条件を満たす場合はエラーなし', () => {
    const issues = validateBundleForm({
      form: {
        ...baseForm,
        bundleName: '降圧薬RP',
        admin: '1日1回',
        items: [{ name: 'アムロジピン', quantity: '1', unit: '錠', memo: '' }],
      },
      entity: 'medOrder',
      bundleLabel: 'RP名',
    });
    expect(issues).toHaveLength(0);
  });

  it('medOrder: 用法が未入力の場合にエラー', () => {
    const issues = validateBundleForm({
      form: {
        ...baseForm,
        bundleName: '降圧薬RP',
        admin: '',
        items: [{ name: 'アムロジピン', quantity: '1', unit: '錠', memo: '' }],
      },
      entity: 'medOrder',
      bundleLabel: 'RP名',
    });
    expect(issues.map((issue) => issue.key)).toEqual(['missing_usage']);
  });

  it('medOrder: RP名未入力でも項目/用法があればエラーにしない', () => {
    const issues = validateBundleForm({
      form: {
        ...baseForm,
        bundleName: '',
        admin: '1日1回',
        items: [{ name: 'アムロジピン', quantity: '1', unit: '錠', memo: '' }],
      },
      entity: 'medOrder',
      bundleLabel: 'RP名',
    });
    expect(issues.map((issue) => issue.key)).toEqual([]);
  });

  it('medOrder: 内服/外用で用法上限日数を超過するとエラー', () => {
    const issues = validateBundleForm({
      form: {
        ...baseForm,
        admin: '1日1回',
        bundleNumber: '15',
        items: [{ name: 'アムロジピン', quantity: '1', unit: '錠', memo: '' }],
        prescriptionTiming: 'regular',
      },
      entity: 'medOrder',
      bundleLabel: 'RP名',
      usageDaysLimit: 14,
    });
    expect(issues.map((issue) => issue.key)).toEqual(['usage_days_limit_exceeded']);
  });

  it('medOrder: 頓用/臨時は用法上限日数の判定対象外', () => {
    const issues = validateBundleForm({
      form: {
        ...baseForm,
        admin: '必要時',
        bundleNumber: '15',
        items: [{ name: 'ロキソニン', quantity: '1', unit: '錠', memo: '' }],
        prescriptionTiming: 'tonyo',
      },
      entity: 'medOrder',
      bundleLabel: 'RP名',
      usageDaysLimit: 7,
    });
    expect(issues).toHaveLength(0);
  });

  it('generalOrder: 項目が必須で、用法は必須にしない', () => {
    const issues = validateBundleForm({
      form: { ...baseForm, bundleName: '処置オーダー', admin: '' },
      entity: 'generalOrder',
      bundleLabel: 'オーダー名',
    });
    expect(issues.map((issue) => issue.key)).toEqual(['missing_items']);
  });

  it.each(['treatmentOrder', 'testOrder', 'laboTest'])('BaseEditor系 %s は項目必須', (entity) => {
    const issues = validateBundleForm({
      form: { ...baseForm, bundleName: 'BaseEditor', admin: '' },
      entity,
      bundleLabel: 'オーダー名',
    });
    expect(issues.map((issue) => issue.key)).toEqual(['missing_items']);
  });

  it('generalOrder: オーダー名が未入力でも項目があればエラーにしない', () => {
    const issues = validateBundleForm({
      form: {
        ...baseForm,
        bundleName: '',
        items: [{ name: '処置A', quantity: '1', unit: '回', memo: '' }],
      },
      entity: 'generalOrder',
      bundleLabel: 'オーダー名',
    });
    expect(issues.map((issue) => issue.key)).toEqual([]);
  });

  it('injectionOrder: 手技料なしフラグがあっても必須条件を満たせばエラーなし', () => {
    const issues = validateBundleForm({
      form: {
        ...baseForm,
        memo: '手技料なし',
        items: [{ name: 'ビタミン注射', quantity: '1', unit: '回', memo: '' }],
      },
      entity: 'injectionOrder',
      bundleLabel: '注射オーダー名',
    });
    expect(issues).toHaveLength(0);
  });

  it('radiologyOrder: 部位が未入力の場合にエラー', () => {
    const issues = validateBundleForm({
      form: {
        ...baseForm,
        bundleName: '胸部撮影',
        items: [{ name: '胸部X線', quantity: '1', unit: '回', memo: '' }],
        bodyPart: null,
      },
      entity: 'radiologyOrder',
      bundleLabel: '放射線オーダー名',
    });
    expect(issues.map((issue) => issue.key)).toEqual(['missing_body_part']);
  });

  it('radiologyOrder: 部位が入力済みならエラーなし', () => {
    const issues = validateBundleForm({
      form: {
        ...baseForm,
        bundleName: '胸部撮影',
        items: [{ name: '胸部X線', quantity: '1', unit: '回', memo: '' }],
        bodyPart: { code: '002000', name: '胸部', quantity: '', unit: '', memo: '' },
      },
      entity: 'radiologyOrder',
      bundleLabel: '放射線オーダー名',
    });
    expect(issues).toHaveLength(0);
  });

  it('commentItems: コメントコードか内容が欠ける場合はエラー', () => {
    const issues = validateBundleForm({
      form: {
        ...baseForm,
        bundleName: '処置オーダー',
        items: [{ name: '処置A', quantity: '1', unit: '回', memo: '' }],
        commentItems: [{ code: '0081', name: '', quantity: '', unit: '', memo: '' }],
      },
      entity: 'generalOrder',
      bundleLabel: 'オーダー名',
    });
    expect(issues.map((issue) => issue.key)).toEqual(['invalid_comment_item']);
  });

  it('commentItems: 不正なコメントコードはエラー', () => {
    const issues = validateBundleForm({
      form: {
        ...baseForm,
        bundleName: '処置オーダー',
        items: [{ name: '処置A', quantity: '1', unit: '回', memo: '' }],
        commentItems: [{ code: '123', name: '注意事項', quantity: '', unit: '', memo: '' }],
      },
      entity: 'generalOrder',
      bundleLabel: 'オーダー名',
    });
    expect(issues.map((issue) => issue.key)).toEqual(['invalid_comment_code']);
  });

  it('commentItems: 行を削除するとエラーが解消される', () => {
    const issues = validateBundleForm({
      form: {
        ...baseForm,
        bundleName: '処置オーダー',
        items: [{ name: '処置A', quantity: '1', unit: '回', memo: '' }],
        commentItems: [],
      },
      entity: 'generalOrder',
      bundleLabel: 'オーダー名',
    });
    expect(issues).toHaveLength(0);
  });

  it('materialItems: 材料名が空でもエラーにしない（材料は項目行へ統合）', () => {
    const issues = validateBundleForm({
      form: {
        ...baseForm,
        bundleName: '処置オーダー',
        items: [{ name: '処置A', quantity: '1', unit: '回', memo: '' }],
        materialItems: [{ name: '', quantity: '1', unit: '枚', memo: '' }],
      },
      entity: 'generalOrder',
      bundleLabel: 'オーダー名',
    });
    expect(issues.map((issue) => issue.key)).toEqual([]);
  });

  it('materialItems: 行を削除するとエラーが解消される', () => {
    const issues = validateBundleForm({
      form: {
        ...baseForm,
        bundleName: '処置オーダー',
        items: [{ name: '処置A', quantity: '1', unit: '回', memo: '' }],
        materialItems: [],
      },
      entity: 'generalOrder',
      bundleLabel: 'オーダー名',
    });
    expect(issues).toHaveLength(0);
  });
});
