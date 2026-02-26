import type { ReactNode } from 'react';
import { describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
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
    expect(drawer?.querySelector('.soap-note__right-drawer-panel[data-active="true"]')).toBeNull();

    await user.click(document.body.querySelector('button[aria-label="注射を開く"]') as HTMLButtonElement);

    await waitFor(() => {
      expect(drawer?.getAttribute('data-open')).toBe('true');
    });
    expect(drawer?.querySelector('.soap-note__right-drawer-panel[data-active="true"]')).not.toBeNull();
    expect(drawer).toHaveTextContent('注射');
    expect(drawer).toHaveTextContent('注射セットA');
  });

  it('非モーダル右ドロワー開中でも背景のSOAP入力を操作できる', async () => {
    const user = userEvent.setup();
    const bundles: OrderBundle[] = [
      {
        entity: 'medOrder',
        bundleName: '降圧薬RP',
        started: '2026-02-26T10:00:00+09:00',
        documentId: 101,
        moduleId: 11,
        items: [{ name: 'アムロジピン', quantity: '1', unit: '錠' }],
      },
    ];

    renderWithQueryClient(
      <SoapNotePanel
        history={[]}
        meta={{
          runId: 'RUN-RIGHT-DOCK-NON-MODAL',
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

    await user.click(screen.getByRole('button', { name: '処方を開く' }));
    await waitFor(() => {
      expect(drawer?.getAttribute('data-open')).toBe('true');
    });
    expect(drawer).toHaveTextContent('処方（RP集合）');

    const subjectiveInput = screen.getByPlaceholderText('Subjective を記載してください。') as HTMLTextAreaElement;
    await user.type(subjectiveInput, '背景操作OK');

    expect(subjectiveInput.value).toContain('背景操作OK');
    expect(drawer?.getAttribute('data-open')).toBe('true');
  });

  it('中列サマリの処方行クリックで右ドロワーを開き処方編集へ遷移する', async () => {
    const user = userEvent.setup();
    const bundles: OrderBundle[] = [
      {
        entity: 'medOrder',
        bundleName: '糖尿病薬RP',
        started: '2026-02-26T09:00:00+09:00',
        documentId: 201,
        moduleId: 21,
        items: [{ name: 'メトホルミン', quantity: '2', unit: '錠' }],
      },
    ];

    renderWithQueryClient(
      <SoapNotePanel
        history={[]}
        meta={{
          runId: 'RUN-RIGHT-DOCK-SUMMARY',
          patientId: 'P-002',
          appointmentId: 'APT-002',
          receptionId: 'RCP-002',
          visitDate: '2026-02-26',
        }}
        author={{ role: 'doctor', displayName: 'Dr. Dock', userId: 'doctor02' }}
        orderBundles={bundles}
      />,
    );

    const drawer = document.body.querySelector('.soap-note__right-drawer');
    expect(drawer?.getAttribute('data-open')).toBe('false');

    await user.click(screen.getByRole('button', { name: '糖尿病薬RPを編集' }));

    await waitFor(() => {
      expect(drawer?.getAttribute('data-open')).toBe('true');
    });
    expect(drawer).toHaveTextContent('処方（RP集合）');
    expect(drawer).toHaveTextContent('糖尿病薬RP');
  });
});
