import type { ReactNode } from 'react';
import { describe, expect, it } from 'vitest';
import { render, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { SoapNotePanel } from '../SoapNotePanel';
import type { OrderBundle } from '../orderBundleApi';

const renderWithQueryClient = (ui: ReactNode) => {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
};

describe('SoapNotePanel right dock drawer', () => {
  it('右ドック押下でドロワーが開き対象カテゴリを表示する', async () => {
    const user = userEvent.setup();
    const bundles: OrderBundle[] = [
      {
        entity: 'injectionOrder',
        bundleName: '注射セットA',
        started: '2026-02-26T10:00:00+09:00',
        documentId: 100,
        moduleId: 10,
        items: [{ name: '生食 100mL', quantity: '1', unit: '本' }],
      },
    ];

    renderWithQueryClient(
      <SoapNotePanel
        history={[]}
        meta={{
          runId: 'RUN-RIGHT-DOCK',
          patientId: 'P-001',
          appointmentId: 'APT-001',
          receptionId: 'RCP-001',
          visitDate: '2026-02-26',
        }}
        author={{ role: 'doctor', displayName: 'Dr. Dock', userId: 'doctor01' }}
        orderBundles={bundles}
      />,
    );

    const drawer = document.body.querySelector('.soap-note__right-drawer');
    expect(drawer).not.toBeNull();
    expect(drawer?.getAttribute('data-open')).toBe('false');

    await user.click(document.body.querySelector('button[aria-label="注射を開く"]') as HTMLButtonElement);

    await waitFor(() => {
      expect(drawer?.getAttribute('data-open')).toBe('true');
    });
    expect(drawer).toHaveTextContent('注射');
    expect(drawer).toHaveTextContent('注射セットA');
  });
});
