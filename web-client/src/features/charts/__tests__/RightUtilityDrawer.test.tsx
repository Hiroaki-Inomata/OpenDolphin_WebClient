import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

import { RightUtilityDrawer } from '../RightUtilityDrawer';

describe('RightUtilityDrawer', () => {
  it('ORCAタブで orcaPanel を表示する', () => {
    render(
      <RightUtilityDrawer
        open
        activeTool="orca"
        meta={{}}
        onClose={vi.fn()}
        onToolSelect={vi.fn()}
        orcaPanel={<div data-testid="orca-panel">ORCAパネル内容</div>}
      />,
    );

    expect(screen.getByRole('tab', { name: 'ORCAタブへ切替' })).toBeInTheDocument();
    expect(document.querySelector('.soap-note__right-drawer')?.getAttribute('data-tool')).toBe('orca');
    expect(document.querySelector('.soap-note__right-drawer-header strong')).toHaveTextContent('ORCA');
    expect(screen.getByTestId('orca-panel')).toBeInTheDocument();
  });
});
