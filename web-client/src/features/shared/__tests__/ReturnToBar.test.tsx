import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { ReturnToBar } from '../ReturnToBar';

describe('ReturnToBar', () => {
  it('常に非表示で描画されない', () => {
    render(<ReturnToBar scope={{ facilityId: '0001', userId: 'doctor01' }} fallbackUrl="/f/0001/reception" />);

    expect(screen.queryByRole('region', { name: '戻り導線' })).not.toBeInTheDocument();
  });

  it('showShortcuts=true でも表示されない', () => {
    render(
      <ReturnToBar
        scope={{ facilityId: '0001', userId: 'doctor01' }}
        fallbackUrl="/f/0001/reception"
        showShortcuts
      />,
    );

    expect(screen.queryByRole('region', { name: '戻り導線' })).not.toBeInTheDocument();
  });
});
