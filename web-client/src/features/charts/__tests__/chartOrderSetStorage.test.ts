import { afterEach, describe, expect, it } from 'vitest';

import {
  clearChartOrderSetStorage,
  deleteChartOrderSet,
  getChartOrderSet,
  listChartOrderSets,
  saveChartOrderSet,
} from '../chartOrderSetStorage';

afterEach(() => {
  clearChartOrderSetStorage();
});

describe('chartOrderSetStorage', () => {
  it('オーダーセットを保存して一覧/詳細取得できる', () => {
    const saved = saveChartOrderSet({
      facilityId: 'facility-A',
      userId: 'doctor-1',
      name: '定期フォローセット',
      snapshot: {
        sourcePatientId: 'P-1',
        sourceVisitDate: '2026-02-11',
        capturedAt: '2026-02-11T11:00:00.000Z',
        diagnoses: [{ diagnosisName: '高血圧症', diagnosisCode: 'I10' }],
        soapDraft: {
          free: '本日メモ',
          subjective: '主訴あり',
          objective: '',
          assessment: '',
          plan: '処方継続',
        },
        soapHistory: [],
        orderBundles: [
          {
            entity: 'medOrder',
            bundleName: '降圧薬',
            bundleNumber: '14',
            admin: '1日1回 朝',
            items: [{ code: 'A100', name: 'アムロジピン', quantity: '1', unit: '錠' }],
          },
        ],
        imageAttachments: [{ id: 10, title: '胸部X線', fileName: 'cxr.png' }],
      },
    });

    const list = listChartOrderSets('facility-A');
    expect(list).toHaveLength(1);
    expect(list[0].id).toBe(saved.id);
    expect(list[0].name).toBe('定期フォローセット');

    const detail = getChartOrderSet({ facilityId: 'facility-A', id: saved.id });
    expect(detail).not.toBeNull();
    expect(detail?.snapshot.diagnoses[0]?.diagnosisName).toBe('高血圧症');
    expect(detail?.snapshot.orderBundles[0]?.entity).toBe('medOrder');
  });

  it('オーダーセットを削除できる', () => {
    const saved = saveChartOrderSet({
      facilityId: 'facility-A',
      name: '削除テスト',
      snapshot: {
        sourcePatientId: 'P-1',
        sourceVisitDate: '2026-02-11',
        capturedAt: '2026-02-11T11:00:00.000Z',
        diagnoses: [],
        soapDraft: { free: '', subjective: '', objective: '', assessment: '', plan: '' },
        soapHistory: [],
        orderBundles: [{ entity: 'testOrder', bundleName: '採血', items: [{ name: 'CBC' }] }],
        imageAttachments: [],
      },
    });

    expect(deleteChartOrderSet({ facilityId: 'facility-A', id: saved.id })).toBe(true);
    expect(listChartOrderSets('facility-A')).toHaveLength(0);
  });
});
