import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';

import { OrcaQueueCard } from '../delivery/OrcaQueueCard';

describe('OrcaQueueCard', () => {
  it('フィルタと破棄導線が動作する', () => {
    const onRetry = vi.fn();
    const onDiscardRequest = vi.fn();

    render(
      <OrcaQueueCard
        entries={[
          {
            patientId: 'P-001',
            status: 'pending',
            retryable: true,
            lastDispatchAt: '2026-02-16T11:00:00Z',
            headers: ['x-a=1'],
          },
          {
            patientId: 'P-002',
            status: 'failed',
            retryable: false,
            lastDispatchAt: '2026-02-16T10:00:00Z',
            error: 'dispatch_failed',
          },
        ]}
        isSystemAdmin
        pending={false}
        warningThresholdMs={30 * 60 * 1000}
        onRetry={onRetry}
        onDiscardRequest={onDiscardRequest}
      />, 
    );

    expect(screen.getByText('pending 1件')).toBeInTheDocument();
    expect(screen.getByText('failed 1件')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('statusフィルタ'), { target: { value: 'failed' } });
    expect(screen.getByText('P-002')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '破棄' }));
    expect(onDiscardRequest).toHaveBeenCalledTimes(1);
    expect(onDiscardRequest.mock.calls[0]?.[0]?.patientId).toBe('P-002');
  });
});
