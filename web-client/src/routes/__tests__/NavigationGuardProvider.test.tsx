import { useEffect } from 'react';
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';

import { NavigationGuardProvider, useNavigationGuard } from '../NavigationGuardProvider';

function GuardHarness() {
  const location = useLocation();
  const { registerDirty, guardedNavigate } = useNavigationGuard();
  const chartsScreenId =
    location.state && typeof location.state === 'object' && !Array.isArray(location.state)
      ? ((location.state as { chartsScreenId?: string }).chartsScreenId ?? '')
      : '';

  useEffect(() => {
    registerDirty('charts.soap', true, 'SOAPドラフトが未保存');
    return () => registerDirty('charts.soap', false);
  }, [registerDirty]);

  return (
    <div>
      <p data-testid="location">{`${location.pathname}${location.search}`}</p>
      <p data-testid="charts-screen-id">{chartsScreenId}</p>
      <button
        type="button"
        onClick={() =>
          guardedNavigate('/f/0001/charts', {
            state: { chartsScreenId: 'screen-2' },
          })
        }
      >
        charts-context-change
      </button>
      <button
        type="button"
        onClick={() =>
          guardedNavigate('/f/0001/charts?msw=1', {
            state: { chartsScreenId: 'screen-1' },
          })
        }
      >
        charts-external-change
      </button>
    </div>
  );
}

describe('NavigationGuardProvider', () => {
  it('dirty 状態で chartsScreenId が変わる遷移はブロックされる', async () => {
    const user = userEvent.setup();

    render(
      <MemoryRouter
        initialEntries={[
          {
            pathname: '/f/0001/charts',
            search: '',
            state: { chartsScreenId: 'screen-1' },
          },
        ]}
      >
        <NavigationGuardProvider>
          <GuardHarness />
        </NavigationGuardProvider>
      </MemoryRouter>,
    );

    expect(screen.getByTestId('location')).toHaveTextContent('/f/0001/charts');
    expect(screen.getByTestId('charts-screen-id')).toHaveTextContent('screen-1');

    await user.click(screen.getByRole('button', { name: 'charts-context-change' }));

    expect(screen.getByRole('alertdialog', { name: '未保存の変更があります' })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/f/0001/charts');
    expect(screen.getByTestId('charts-screen-id')).toHaveTextContent('screen-1');
  });

  it('dirty 状態でも charts の外部パラメータ更新は同一画面として許可する', async () => {
    const user = userEvent.setup();

    render(
      <MemoryRouter
        initialEntries={[
          {
            pathname: '/f/0001/charts',
            search: '',
            state: { chartsScreenId: 'screen-1' },
          },
        ]}
      >
        <NavigationGuardProvider>
          <GuardHarness />
        </NavigationGuardProvider>
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('button', { name: 'charts-external-change' }));

    expect(screen.queryByRole('alertdialog', { name: '未保存の変更があります' })).not.toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/f/0001/charts?msw=1');
    expect(screen.getByTestId('charts-screen-id')).toHaveTextContent('screen-1');
  });
});
