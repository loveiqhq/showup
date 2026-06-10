import { ServiceUnavailableException } from '@nestjs/common';
import { OAuth2Client } from 'google-auth-library';

import { GoogleVerifier } from './google-verifier';

jest.mock('google-auth-library');

const config = (ids: string[]) => ({ get: () => ids }) as any;

describe('GoogleVerifier', () => {
  const verifyIdToken = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
    (OAuth2Client as unknown as jest.Mock).mockImplementation(() => ({
      verifyIdToken,
    }));
  });

  it('verifies against configured audiences and normalizes the identity', async () => {
    verifyIdToken.mockResolvedValue({
      getPayload: () => ({
        sub: 'g-1',
        email: 'a@example.com',
        email_verified: true,
        name: 'Ada',
      }),
    });

    const identity = await new GoogleVerifier(config(['cid'])).verify('tok');

    expect(verifyIdToken).toHaveBeenCalledWith({
      idToken: 'tok',
      audience: ['cid'],
    });
    expect(identity).toEqual({
      provider: 'google',
      providerId: 'g-1',
      email: 'a@example.com',
      emailVerified: true,
      name: 'Ada',
    });
  });

  it('throws when no client IDs are configured', async () => {
    await expect(
      new GoogleVerifier(config([])).verify('tok'),
    ).rejects.toBeInstanceOf(ServiceUnavailableException);
  });
});
