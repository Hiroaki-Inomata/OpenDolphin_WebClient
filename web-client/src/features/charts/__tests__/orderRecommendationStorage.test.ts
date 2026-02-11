import { afterEach, describe, expect, it } from 'vitest';

import {
  clearOrderRecommendationStorage,
  listOrderRecommendations,
  recordOrderRecommendationUsage,
} from '../orderRecommendationStorage';

afterEach(() => {
  clearOrderRecommendationStorage();
});

describe('orderRecommendationStorage', () => {
  it('患者傾向を優先し、不足分を施設傾向で補う', () => {
    const facilityId = 'facility-A';

    recordOrderRecommendationUsage({
      facilityId,
      patientId: 'P-1',
      entity: 'medOrder',
      template: {
        bundleName: '降圧薬A',
        admin: '1日1回 朝食後',
        bundleNumber: '14',
        adminMemo: '',
        memo: '',
        prescriptionLocation: 'out',
        prescriptionTiming: 'regular',
        items: [{ code: 'A100', name: 'アムロジピン', quantity: '1', unit: '錠' }],
        materialItems: [],
        commentItems: [],
        bodyPart: null,
      },
    });

    recordOrderRecommendationUsage({
      facilityId,
      patientId: 'P-2',
      entity: 'medOrder',
      template: {
        bundleName: '降圧薬B',
        admin: '1日2回 朝夕食後',
        bundleNumber: '7',
        adminMemo: '',
        memo: '',
        prescriptionLocation: 'out',
        prescriptionTiming: 'regular',
        items: [{ code: 'B200', name: 'テルミサルタン', quantity: '1', unit: '錠' }],
        materialItems: [],
        commentItems: [],
        bodyPart: null,
      },
    });

    const result = listOrderRecommendations({
      facilityId,
      patientId: 'P-1',
      entity: 'medOrder',
      limit: 5,
    });

    expect(result).toHaveLength(2);
    expect(result[0].source).toBe('patient');
    expect(result[0].template.bundleName).toBe('降圧薬A');
    expect(result[1].source).toBe('facility');
    expect(result[1].template.bundleName).toBe('降圧薬B');
  });

  it('同一テンプレートは使用回数を加算する', () => {
    const facilityId = 'facility-A';
    const template = {
      bundleName: '整形セット',
      admin: '適宜',
      bundleNumber: '1',
      adminMemo: '',
      memo: '',
      items: [{ code: 'T100', name: '創傷処置', quantity: '1', unit: '回' }],
      materialItems: [],
      commentItems: [{ code: '0085001', name: '消毒実施' }],
      bodyPart: null,
    };

    recordOrderRecommendationUsage({ facilityId, patientId: 'P-1', entity: 'treatmentOrder', template });
    recordOrderRecommendationUsage({ facilityId, patientId: 'P-1', entity: 'treatmentOrder', template });

    const result = listOrderRecommendations({
      facilityId,
      patientId: 'P-1',
      entity: 'treatmentOrder',
      limit: 3,
    });

    expect(result).toHaveLength(1);
    expect(result[0].count).toBe(2);
    expect(result[0].source).toBe('patient');
  });
});
