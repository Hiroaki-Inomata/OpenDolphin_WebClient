import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';

import type { TouchAdmPhrResponse } from '../touchAdmPhrApi';
import { TouchAdmPhrPanel } from '../TouchAdmPhrPanel';
import { clearAuditEventLog, getAuditEventLog } from '../../../libs/audit/auditLogger';

const { mockRequest } = vi.hoisted(() => ({
  mockRequest: vi.fn<(req: any) => Promise<TouchAdmPhrResponse>>(),
}));

vi.mock('../touchAdmPhrApi', async () => {
  const actual = await vi.importActual<typeof import('../touchAdmPhrApi')>('../touchAdmPhrApi');
  return {
    ...actual,
    requestTouchAdmPhr: mockRequest,
  };
});

vi.mock('../../../libs/audit/auditLogger', () => {
  const auditLog: any[] = [];
  return {
    clearAuditEventLog: () => {
      auditLog.length = 0;
    },
    getAuditEventLog: () => [...auditLog],
    logAuditEvent: (entry: any) => {
      const record = { ...entry, timestamp: new Date().toISOString() };
      auditLog.push(record);
      return record;
    },
    logUiState: () => ({ timestamp: new Date().toISOString() }),
  };
});

afterEach(() => {
  cleanup();
  clearAuditEventLog();
  mockRequest.mockReset();
});

describe('TouchAdmPhrPanel', () => {
  it('サーバー側無効化メッセージを表示し、送信ボタンは無効のまま', () => {
    render(
      <TouchAdmPhrPanel
        runId="RUN-TEST"
        role="system_admin"
        actorId="facility:user"
        environmentLabel="dev"
        isSystemAdmin
        facilityId="FAC-1"
        userId="USER-1"
      />,
    );

    expect(screen.getAllByText(/server-modernized 側で無効化/).length).toBeGreaterThan(0);

    const button = screen.getByRole('button', { name: '無効化済み' });
    expect(button).toBeDisabled();

    fireEvent.click(button);

    expect(mockRequest).not.toHaveBeenCalled();
    expect(getAuditEventLog()).toHaveLength(0);
  });

  it('system_admin 以外は権限ガード文言を表示する', () => {
    render(
      <TouchAdmPhrPanel
        runId="RUN-TEST"
        role="doctor"
        actorId="facility:user"
        environmentLabel="dev"
        isSystemAdmin={false}
        facilityId="FAC-1"
        userId="USER-1"
      />,
    );

    expect(screen.getByText(/権限がないため Touch\/ADM\/PHR の疎通確認は利用できません/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '無効化済み' })).toBeDisabled();
    expect(mockRequest).not.toHaveBeenCalled();
  });
});
