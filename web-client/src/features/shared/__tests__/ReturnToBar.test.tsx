import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  guardedNavigate: vi.fn(),
  openReception: vi.fn(),
  openPatients: vi.fn(),
  openCharts: vi.fn(),
}));

vi.mock('../../../routes/NavigationGuardProvider', () => ({
  useNavigationGuard: () => ({
    guardedNavigate: mocks.guardedNavigate,
    registerDirty: vi.fn(),
    isDirty: false,
    dirtySources: [],
  }),
}));

vi.mock('../../../routes/useAppNavigation', () => ({
  useAppNavigation: () => ({
    currentScreen: 'charts',
    openReception: mocks.openReception,
    openPatients: mocks.openPatients,
    openCharts: mocks.openCharts,
  }),
}));

vi.mock('../../../routes/appNavigation', () => ({
  isSafeReturnTo: () => false,
}));

import { ReturnToBar } from '../ReturnToBar';

describe('ReturnToBar', () => {
  it('既定ではショートカットリンクを表示しない', () => {
    render(<ReturnToBar scope={{ facilityId: '0001', userId: 'doctor01' }} fallbackUrl="/f/0001/reception" />);

    expect(screen.queryByRole('group', { name: '主要画面へ' })).not.toBeInTheDocument();
  });

  it('showShortcuts=true の場合はショートカットリンクを表示する', () => {
    render(
      <ReturnToBar
        scope={{ facilityId: '0001', userId: 'doctor01' }}
        fallbackUrl="/f/0001/reception"
        showShortcuts
      />,
    );

    expect(screen.getByRole('group', { name: '主要画面へ' })).toBeInTheDocument();
  });
});
