import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { AccessManagementPanel } from '../AccessManagementPanel';
import type { AccessManagedUser, AccessUsersResponse } from '../accessManagementApi';

const {
  mockFetchAccessUsers,
  mockCreateAccessUser,
  mockUpdateAccessUser,
  mockResetAccessUserPassword,
  mockLogAuditEvent,
} = vi.hoisted(() => ({
  mockFetchAccessUsers: vi.fn<() => Promise<AccessUsersResponse>>(),
  mockCreateAccessUser: vi.fn(),
  mockUpdateAccessUser: vi.fn(),
  mockResetAccessUserPassword: vi.fn(),
  mockLogAuditEvent: vi.fn(),
}));

vi.mock('../accessManagementApi', () => ({
  fetchAccessUsers: mockFetchAccessUsers,
  createAccessUser: mockCreateAccessUser,
  updateAccessUser: mockUpdateAccessUser,
  resetAccessUserPassword: mockResetAccessUserPassword,
}));

vi.mock('../../../AppRouter', () => ({
  useSession: () => ({ facilityId: 'FAC-TEST', userId: 'system-admin', role: 'system_admin' }),
}));

vi.mock('../../../libs/audit/auditLogger', () => ({
  logAuditEvent: mockLogAuditEvent,
}));

const TARGET_USER: AccessManagedUser = {
  userPk: 101,
  userId: 'FAC-TEST:doctor01',
  loginId: 'doctor01',
  displayName: '山田 太郎',
  roles: ['doctor', 'user'],
  factor2Auth: 'totp',
  orcaLink: {
    linked: true,
    orcaUserId: 'ORCA001',
    updatedAt: '2026-02-12T20:00:00Z',
  },
};

const renderPanel = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
      mutations: {
        retry: false,
      },
    },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AccessManagementPanel runId="RUN-PASSWORD-RESET" role="system_admin" mode="full" />
    </QueryClientProvider>,
  );
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('AccessManagementPanel password reset', () => {
  it('一時パスワード未入力では送信しない', async () => {
    mockFetchAccessUsers.mockResolvedValue({
      runId: 'RUN-LIST',
      facilityId: 'FAC-TEST',
      users: [TARGET_USER],
    });

    renderPanel();

    fireEvent.click(await screen.findByRole('button', { name: 'パスワードリセット' }));
    const dialog = await screen.findByRole('dialog', { name: 'パスワードリセット' });

    fireEvent.change(within(dialog).getByLabelText('管理者 Authenticator（TOTP）コード'), {
      target: { value: '123456' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: 'パスワードリセット' }));

    expect(await screen.findByText('一時パスワードを入力してください。')).toBeInTheDocument();
    expect(mockResetAccessUserPassword).not.toHaveBeenCalled();
    expect(within(dialog).queryByRole('button', { name: 'コピー' })).not.toBeInTheDocument();
  });

  it('temporaryPassword と TOTP を送信し、監査ログに resetTarget.userPk を使う', async () => {
    mockFetchAccessUsers.mockResolvedValue({
      runId: 'RUN-LIST',
      facilityId: 'FAC-TEST',
      users: [TARGET_USER],
    });
    mockResetAccessUserPassword.mockResolvedValue(undefined);

    renderPanel();

    fireEvent.click(await screen.findByRole('button', { name: 'パスワードリセット' }));
    const dialog = await screen.findByRole('dialog', { name: 'パスワードリセット' });

    fireEvent.change(within(dialog).getByLabelText('一時パスワード'), {
      target: { value: 'TempPass#2026' },
    });
    fireEvent.change(within(dialog).getByLabelText('管理者 Authenticator（TOTP）コード'), {
      target: { value: '654321' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: 'パスワードリセット' }));

    await waitFor(() => {
      expect(mockResetAccessUserPassword).toHaveBeenCalledWith(101, {
        totpCode: '654321',
        temporaryPassword: 'TempPass#2026',
      });
    });

    await waitFor(() => {
      expect(mockLogAuditEvent).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: expect.objectContaining({
            operation: 'password-reset',
            targetUserPk: 101,
          }),
        }),
      );
    });

    expect(screen.queryByText(/一時パスワード:/)).not.toBeInTheDocument();
  });
});
