import { describe, expect, it } from 'vitest';

import { buildOrcaReportRequestXml } from './orcaReportApi';

describe('buildOrcaReportRequestXml', () => {
  it('patientId の動的値を XML エスケープする', () => {
    const xml = buildOrcaReportRequestXml('prescription', {
      patientId: '1</Patient_ID><X>pwn</X>',
    });

    expect(xml).not.toContain('<X>');
    expect(xml).toContain('&lt;/Patient_ID&gt;&lt;X&gt;pwn&lt;/X&gt;');
  });
});
