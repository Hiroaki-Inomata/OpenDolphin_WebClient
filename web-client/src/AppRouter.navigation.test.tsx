import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { AppRouter } from './AppRouter';

const AUTH_KEY = 'opendolphin:web-client:auth';

const setSession = (role: string) => {
  sessionStorage.setItem(
    AUTH_KEY,
    JSON.stringify({
      facilityId: '0001',
      userId: 'user01',
      role,
      runId: 'RUN-NAV',
      displayName: 'user01',
    }),
  );
};

const prepareSession = (role: string) => {
  localStorage.clear();
  sessionStorage.clear();
  setSession(role);
};

describe('AppRouter navigation guard', () => {
  beforeEach(() => {
    window.history.pushState({}, '', '/f/0001/reception');
  });

  afterEach(() => {
    cleanup();
    sessionStorage.clear();
    localStorage.clear();
  });

  it('non system_admin は管理画面ボタン/ORCAステータスが表示されず、受付/患者管理へ遷移できる', async () => {
    prepareSession('doctor');
    const user = userEvent.setup();
    const queryClient = new QueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <AppRouter />
      </QueryClientProvider>,
    );

    expect(screen.queryByLabelText('画面ナビゲーション')).not.toBeInTheDocument();
    expect(screen.queryByText(/^ORCA:/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '管理画面を開く' })).not.toBeInTheDocument();

    const patientsButton = await screen.findByRole('button', { name: '患者管理' });
    const receptionButton = await screen.findByRole('button', { name: '受付' });

    await user.click(patientsButton);
    expect(window.location.pathname).toBe('/f/0001/patients');

    await user.click(receptionButton);
    expect(window.location.pathname).toBe('/f/0001/reception');
  });

  it('system_admin は管理画面ボタンが表示され遷移できる', async () => {
    prepareSession('system_admin');
    const user = userEvent.setup();
    const queryClient = new QueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <AppRouter />
      </QueryClientProvider>,
    );

    expect(screen.queryByLabelText('画面ナビゲーション')).not.toBeInTheDocument();
    expect(screen.getByText(/^ORCA:/)).toBeInTheDocument();

    const adminButton = await screen.findByRole('button', { name: '管理画面を開く' });
    await user.click(adminButton);

    expect(window.location.pathname).toBe('/f/0001/administration');
  });

  it('Administration のタブ/サブナビ切替が即時反映される', async () => {
    prepareSession('system_admin');
    const user = userEvent.setup();
    const queryClient = new QueryClient();
    window.history.pushState({}, '', '/f/0001/administration?section=dashboard');

    render(
      <QueryClientProvider client={queryClient}>
        <AppRouter />
      </QueryClientProvider>,
    );

    expect(await screen.findByRole('navigation', { name: '設定配信サブナビ' })).toBeInTheDocument();

    const usersTab = screen.getByRole('tab', { name: 'ORCAユーザー連携・権限' });
    await user.click(usersTab);
    expect(window.location.search).toContain('tab=orca-users');
    expect(await screen.findByRole('heading', { name: 'ORCAユーザー連携（職員マスタ）' })).toBeInTheDocument();

    const masterTab = screen.getByRole('tab', { name: 'マスタ更新' });
    await user.click(masterTab);
    expect(window.location.search).toContain('tab=master-updates');
    expect(await screen.findByRole('heading', { name: 'マスタ更新ダッシュボード' })).toBeInTheDocument();

    const deliveryTab = screen.getByRole('tab', { name: '設定配信' });
    await user.click(deliveryTab);
    expect(window.location.search).toContain('section=dashboard');
    expect(await screen.findByRole('navigation', { name: '設定配信サブナビ' })).toBeInTheDocument();
  });

  it('system_admin 以外の直アクセスは Administration を遮断する', async () => {
    prepareSession('doctor');
    const queryClient = new QueryClient();
    window.history.pushState({}, '', '/f/0001/administration');

    render(
      <QueryClientProvider client={queryClient}>
        <AppRouter />
      </QueryClientProvider>,
    );

    expect(await screen.findByText('Administration は system_admin 専用のためアクセスできません。')).toBeInTheDocument();
    expect(window.location.pathname).toBe('/f/0001/administration');
  });
});
