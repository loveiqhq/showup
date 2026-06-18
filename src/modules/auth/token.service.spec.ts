import { createHash } from 'crypto';

import { UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';

import { User, UserStatus } from '../users/entities/user.entity';
import { TokenService } from './token.service';

interface Row {
  id?: string;
  userId: string;
  tokenHash: string;
  deviceId: string | null;
  userAgent: string | null;
  expiresAt: Date;
  revokedAt: Date | null;
  rotatedToId: string | null;
  createdAt?: Date;
}

class FakeRefreshRepo {
  rows: Row[] = [];
  private seq = 1;

  create(p: Partial<Row>): Row {
    return {
      deviceId: null,
      userAgent: null,
      revokedAt: null,
      rotatedToId: null,
      ...p,
    } as Row;
  }
  save(e: Row): Promise<Row> {
    if (!e.id) {
      e.id = String(this.seq++);
      e.createdAt = new Date();
      this.rows.push(e);
    }
    return Promise.resolve(e);
  }
  findOne({ where }: any): Promise<Row | null> {
    return Promise.resolve(
      this.rows.find((r) => r.tokenHash === where.tokenHash) ?? null,
    );
  }
  update(criteria: any, patch: Partial<Row>): Promise<{ affected: number }> {
    for (const r of this.rows) {
      if (r.userId === criteria.userId && r.revokedAt == null) {
        Object.assign(r, patch);
      }
    }
    return Promise.resolve({ affected: 0 });
  }
}

const sha256 = (s: string) => createHash('sha256').update(s).digest('hex');
const config = () =>
  ({
    get: (k: string) =>
      ({ 'auth.accessTtl': '15m', 'auth.refreshTtl': '30d' })[k],
  }) as any;

describe('TokenService', () => {
  let repo: FakeRefreshRepo;
  let jwt: JwtService;
  let user: User;
  let svc: TokenService;

  beforeEach(() => {
    repo = new FakeRefreshRepo();
    jwt = new JwtService({
      secret: 'test-secret',
      signOptions: { expiresIn: 900 },
    });
    user = { id: 'u1', status: UserStatus.Active } as User;
    const users = {
      findById: (id: string) => Promise.resolve(id === user.id ? user : null),
    };
    svc = new TokenService(repo as any, jwt, config(), users as any);
  });

  it('issues a verifiable access token and stores the refresh hash', async () => {
    const tokens = await svc.issueTokens(user);
    expect(tokens.expiresIn).toBe(900);
    expect(jwt.verify<{ sub: string }>(tokens.accessToken).sub).toBe('u1');
    expect(repo.rows).toHaveLength(1);
    expect(repo.rows[0].tokenHash).toBe(sha256(tokens.refreshToken));
  });

  it('stamps an auth time in the access token and preserves it across rotation', async () => {
    const first = await svc.issueTokens(user);
    const p1 = jwt.verify<{ authTime: number }>(first.accessToken);
    expect(typeof p1.authTime).toBe('number');

    const { tokens } = await svc.rotate(first.refreshToken);
    const p2 = jwt.verify<{ authTime: number }>(tokens.accessToken);
    // Refreshing is NOT re-authentication, so the auth time must carry over unchanged.
    expect(p2.authTime).toBe(p1.authTime);
  });

  it('rotates: revokes the old token and issues a new one', async () => {
    const first = await svc.issueTokens(user);
    const { tokens } = await svc.rotate(first.refreshToken);
    expect(tokens.refreshToken).not.toBe(first.refreshToken);
    const old = repo.rows.find(
      (r) => r.tokenHash === sha256(first.refreshToken),
    );
    expect(old?.revokedAt).toBeInstanceOf(Date);
    expect(old?.rotatedToId).toBeTruthy();
  });

  it('detects reuse of a rotated token and revokes the whole family', async () => {
    const first = await svc.issueTokens(user);
    const { tokens: second } = await svc.rotate(first.refreshToken);
    await expect(svc.rotate(first.refreshToken)).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
    // The successor token is also revoked.
    const successor = repo.rows.find(
      (r) => r.tokenHash === sha256(second.refreshToken),
    );
    expect(successor?.revokedAt).toBeInstanceOf(Date);
  });

  it('revoke() invalidates a refresh token', async () => {
    const { refreshToken } = await svc.issueTokens(user);
    await svc.revoke(refreshToken);
    const row = repo.rows.find((r) => r.tokenHash === sha256(refreshToken));
    expect(row?.revokedAt).toBeInstanceOf(Date);
  });

  it('rejects an expired refresh token', async () => {
    const { refreshToken } = await svc.issueTokens(user);
    repo.rows[0].expiresAt = new Date(Date.now() - 1000);
    await expect(svc.rotate(refreshToken)).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
  });

  it('rejects rotation for a suspended user', async () => {
    const { refreshToken } = await svc.issueTokens(user);
    user.status = UserStatus.Suspended;
    await expect(svc.rotate(refreshToken)).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
  });
});
