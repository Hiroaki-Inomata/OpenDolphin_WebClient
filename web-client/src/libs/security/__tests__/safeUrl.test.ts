import { describe, expect, it } from 'vitest';

import { safeSameOriginHttpUrl } from '../safeUrl';

describe('safeSameOriginHttpUrl', () => {
  const baseUrl = 'https://example.local/openDolphin/index.html?tab=1#top';

  it('returns pathname+search+hash for same-origin absolute URL', () => {
    expect(
      safeSameOriginHttpUrl('https://example.local/patients/1/images?size=small#thumb', {
        baseUrl,
      }),
    ).toBe('/patients/1/images?size=small#thumb');
  });

  it('resolves relative URL against current origin', () => {
    expect(safeSameOriginHttpUrl('/patients/2/images?download=1', { baseUrl })).toBe('/patients/2/images?download=1');
  });

  it('rejects external origin and dangerous schemes', () => {
    expect(safeSameOriginHttpUrl('https://attacker.example/images/1', { baseUrl })).toBeUndefined();
    expect(safeSameOriginHttpUrl('//attacker.example/images/1', { baseUrl })).toBeUndefined();
    expect(safeSameOriginHttpUrl('javascript:alert(1)', { baseUrl })).toBeUndefined();
    expect(safeSameOriginHttpUrl('data:text/html,hi', { baseUrl })).toBeUndefined();
    expect(safeSameOriginHttpUrl('blob:https://example.local/id', { baseUrl })).toBeUndefined();
  });

  it('rejects invalid or empty URL inputs', () => {
    expect(safeSameOriginHttpUrl(undefined, { baseUrl })).toBeUndefined();
    expect(safeSameOriginHttpUrl('', { baseUrl })).toBeUndefined();
    expect(safeSameOriginHttpUrl('  ', { baseUrl })).toBeUndefined();
    expect(safeSameOriginHttpUrl('http://[invalid-url', { baseUrl })).toBeUndefined();
  });
});
