import { describe, expect, it } from 'vitest';

import { escapeXml } from './xmlUtils';

describe('escapeXml', () => {
  it('XML予約5文字をエスケープする', () => {
    expect(escapeXml(`<>&"'`)).toBe('&lt;&gt;&amp;&quot;&apos;');
  });
});
