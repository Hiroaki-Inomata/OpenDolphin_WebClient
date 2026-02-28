import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../../libs/http/httpClient', () => ({
  httpFetch: vi.fn(),
}));

vi.mock('../../../libs/observability/observability', () => ({
  generateRunId: vi.fn(() => 'RUN-GEN'),
  getObservabilityMeta: vi.fn(() => ({ runId: 'RUN-META' })),
  updateObservabilityMeta: vi.fn(),
}));

vi.mock('../../outpatient/orcaPatientImportApi', () => ({
  importPatientsFromOrca: vi.fn(),
}));

import { httpFetch } from '../../../libs/http/httpClient';
import { importPatientsFromOrca } from '../../outpatient/orcaPatientImportApi';
import {
  fetchOrderBundles,
  fetchOrderBundlesWithPatientImportRecovery,
  mutateOrderBundles,
} from '../orderBundleApi';

describe('orderBundleApi bodyPart contract', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fetch は bodyPart 専用フィールドを欠落させずに返す', async () => {
    vi.mocked(httpFetch).mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          runId: 'RUN-FETCH',
          patientId: '000001',
          bundles: [
            {
              entity: 'radiologyOrder',
              bundleName: '胸部CT',
              items: [{ code: '700001', name: '胸部CT' }],
              bodyPart: { code: 'BP001', name: '胸部', quantity: '1', unit: '部位', memo: '専用' },
            },
          ],
        }),
        {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    );

    const result = await fetchOrderBundles({ patientId: '000001' });

    expect(result.ok).toBe(true);
    expect((result.bundles[0] as any).bodyPart).toEqual(
      expect.objectContaining({
        code: 'BP001',
        name: '胸部',
      }),
    );
  });

  it('mutation は bodyPart 専用フィールドを payload に含めて送信する', async () => {
    vi.mocked(httpFetch).mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          runId: 'RUN-MUT',
          createdDocumentIds: [101],
        }),
        {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    );

    await mutateOrderBundles({
      patientId: '000001',
      operations: [
        {
          operation: 'create',
          entity: 'radiologyOrder',
          bundleName: '胸部CT',
          items: [{ code: '700001', name: '胸部CT' }],
          bodyPart: { code: 'BP001', name: '胸部', quantity: '1', unit: '部位', memo: '専用' },
        } as any,
      ],
    });

    const request = vi.mocked(httpFetch).mock.calls[0]?.[1];
    const body = JSON.parse(String((request as RequestInit | undefined)?.body ?? '{}')) as Record<string, any>;

    expect(body.operations[0]).toEqual(
      expect.objectContaining({
        bodyPart: expect.objectContaining({
          code: 'BP001',
          name: '胸部',
        }),
      }),
    );
  });

  it('患者取込リカバリ後の再取得でも bodyPart 専用フィールドを保持する', async () => {
    vi.mocked(httpFetch)
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            code: 'patient_not_found',
            runId: 'RUN-404',
          }),
          {
            status: 404,
            headers: { 'Content-Type': 'application/json' },
          },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            runId: 'RUN-200',
            patientId: '000001',
            bundles: [
              {
                entity: 'radiologyOrder',
                bundleName: '腰椎MRI',
                items: [{ code: '700100', name: '腰椎MRI' }],
                bodyPart: { code: 'BP090', name: '腰部' },
              },
            ],
          }),
          {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          },
        ),
      );
    vi.mocked(importPatientsFromOrca).mockResolvedValueOnce({
      ok: true,
      runId: 'RUN-IMPORT',
      status: 200,
      payload: {},
    });

    const result = await fetchOrderBundlesWithPatientImportRecovery({ patientId: '000001', from: '2026-02-27' });

    expect(result.ok).toBe(true);
    expect(result.patientImportAttempted).toBe(true);
    expect((result.bundles[0] as any).bodyPart).toEqual(expect.objectContaining({ name: '腰部' }));
  });
});
