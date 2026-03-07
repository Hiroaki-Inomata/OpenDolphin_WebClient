/* @vitest-environment node */
import { afterEach, describe, expect, it, vi } from 'vitest';

import { httpFetch } from '../../libs/http/httpClient';
import { fetchStampDetail, fetchStampTree } from './stampApi';

vi.mock('../../libs/http/httpClient', () => ({
  httpFetch: vi.fn(),
}));

afterEach(() => {
  vi.clearAllMocks();
});

describe('stampApi', () => {
  it('fetchStampTree は /stamp/tree/{userPk} から一覧を取得する', async () => {
    vi.mocked(httpFetch).mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          stampTreeList: [
            {
              treeName: '個人',
              entity: 'medOrder',
              stampList: [{ name: '降圧セット', entity: 'medOrder', stampId: 'STAMP-1' }],
            },
          ],
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );

    const result = await fetchStampTree(101);

    expect(httpFetch).toHaveBeenCalledWith(
      '/stamp/tree/101',
      expect.objectContaining({
        method: 'GET',
        headers: { Accept: 'application/json' },
      }),
    );
    expect(result.ok).toBe(true);
    expect(result.trees[0]?.stampList[0]?.stampId).toBe('STAMP-1');
  });

  it('fetchStampDetail は /stamp/id/{stampId} から詳細を取得する', async () => {
    vi.mocked(httpFetch).mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          orderName: '降圧セット',
          admin: '1日1回 朝',
          bundleNumber: '1',
          claimItem: [{ name: 'アムロジピン', number: '1', unit: '錠' }],
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );

    const result = await fetchStampDetail('STAMP-1');

    expect(httpFetch).toHaveBeenCalledWith(
      '/stamp/id/STAMP-1',
      expect.objectContaining({
        method: 'GET',
        headers: { Accept: 'application/json' },
      }),
    );
    expect(result.ok).toBe(true);
    expect(result.stamp?.orderName).toBe('降圧セット');
  });

  it('403 は権限エラーメッセージを返す', async () => {
    vi.mocked(httpFetch).mockResolvedValueOnce(new Response('{}', { status: 403, headers: { 'Content-Type': 'application/json' } }));

    const result = await fetchStampTree(101);

    expect(result.ok).toBe(false);
    expect(result.message).toContain('権限');
  });

  it('404 は未存在メッセージを返す', async () => {
    vi.mocked(httpFetch).mockResolvedValueOnce(new Response('{}', { status: 404, headers: { 'Content-Type': 'application/json' } }));

    const result = await fetchStampDetail('missing');

    expect(result.ok).toBe(false);
    expect(result.message).toContain('見つかりません');
  });
});
