import { generateKeyPairSync } from 'crypto';

import { ServiceUnavailableException } from '@nestjs/common';

// Mock jsonwebtoken (CJS) so we test claim normalization, not signature crypto.
jest.mock('jsonwebtoken', () => ({ decode: jest.fn(), verify: jest.fn() }));

import * as jwt from 'jsonwebtoken';

import { AppleVerifier } from './apple-verifier';

// A real RSA public JWK so createPublicKey() succeeds during key resolution.
const { publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const jwk = { ...(publicKey.export({ format: 'jwk' }) as object), kid: 'k1' };

const config = (ids: string[]) =>
  ({
    get: (key: string) =>
      key === 'social.appleIssuer' ? 'https://appleid.apple.com' : ids,
  }) as any;

describe('AppleVerifier', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (jwt.decode as jest.Mock).mockReturnValue({ header: { kid: 'k1' } });
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ keys: [jwk] }),
    }) as any;
  });

  it('verifies and normalizes a verified Apple identity', async () => {
    (jwt.verify as jest.Mock).mockReturnValue({
      sub: 'a-1',
      email: 'x@example.com',
      email_verified: true,
    });

    const identity = await new AppleVerifier(config(['aud'])).verify('tok');

    expect(identity).toEqual({
      provider: 'apple',
      providerId: 'a-1',
      email: 'x@example.com',
      emailVerified: true,
      name: null,
    });
  });

  it('treats email_verified="true" (string) as verified', async () => {
    (jwt.verify as jest.Mock).mockReturnValue({
      sub: 'a-2',
      email: 'z@example.com',
      email_verified: 'true',
    });
    const identity = await new AppleVerifier(config(['aud'])).verify('tok');
    expect(identity.emailVerified).toBe(true);
  });

  it('throws when no client IDs are configured', async () => {
    await expect(
      new AppleVerifier(config([])).verify('tok'),
    ).rejects.toBeInstanceOf(ServiceUnavailableException);
  });
});
