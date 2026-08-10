import { HttpException, UnauthorizedException } from '@nestjs/common';

import { EmailOtpService } from './email-otp.service';

interface Row {
  id?: string;
  userId: string;
  email: string;
  codeHash: string;
  expiresAt: Date;
  attempts: number;
  consumedAt: Date | null;
  createdAt?: Date;
}

class FakeVerifRepo {
  rows: Row[] = [];
  private seq = 1;

  private matches(r: Row, c: any): boolean {
    if (c.id !== undefined) return r.id === c.id;
    if (c.userId !== undefined && r.userId !== c.userId) return false;
    if ('consumedAt' in c && r.consumedAt != null) return false;
    return true;
  }

  create(p: Partial<Row>): Row {
    return { attempts: 0, consumedAt: null, ...p } as Row;
  }
  save(e: Row): Promise<Row> {
    if (!e.id) {
      e.id = String(this.seq++);
      e.createdAt = e.createdAt ?? new Date();
      this.rows.push(e);
    }
    return Promise.resolve(e);
  }
  findOne({ where, order }: any): Promise<Row | null> {
    let res = this.rows.filter((r) => this.matches(r, where));
    if (order?.createdAt === 'DESC') {
      res = [...res].sort(
        (a, b) => (b.createdAt!.getTime() ?? 0) - (a.createdAt!.getTime() ?? 0),
      );
    }
    return Promise.resolve(res[0] ?? null);
  }
  delete(c: any): Promise<{ affected: number }> {
    this.rows = this.rows.filter((r) => !this.matches(r, c));
    return Promise.resolve({ affected: 0 });
  }
}

interface UserRow {
  id: string;
  email: string | null;
  emailVerifiedAt: Date | null;
}

class FakeUsersRepo {
  constructor(public rows: UserRow[] = []) {}
  findOne({ where }: any): Promise<UserRow | null> {
    return Promise.resolve(
      this.rows.find((u) => u.email === where.email) ?? null,
    );
  }
  update(
    criteria: any,
    patch: Partial<UserRow>,
  ): Promise<{ affected: number }> {
    const row = this.rows.find((u) => u.id === criteria.id);
    if (row) Object.assign(row, patch);
    return Promise.resolve({ affected: row ? 1 : 0 });
  }
}

const config = (over: Record<string, unknown> = {}) =>
  ({
    get: (k: string) =>
      ({
        'auth.otpResendCooldownSeconds': 60,
        'auth.otpTtlSeconds': 300,
        'auth.otpMaxAttempts': 3,
        'auth.exposeOtp': true,
        'auth.jwtSecret': 'test-secret-1234567890',
        ...over,
      })[k],
  }) as any;

const USER_ID = 'user-1';
const EMAIL = 'leo@example.com';

describe('EmailOtpService', () => {
  let repo: FakeVerifRepo;
  let users: FakeUsersRepo;
  let email: { send: jest.Mock };

  const make = (over: Record<string, unknown> = {}) =>
    new EmailOtpService(repo as any, users as any, config(over), email);

  beforeEach(() => {
    repo = new FakeVerifRepo();
    users = new FakeUsersRepo([
      { id: USER_ID, email: null, emailVerifiedAt: null },
    ]);
    email = { send: jest.fn().mockResolvedValue({ success: true }) };
  });

  it('sends a 6-digit code, verifies it, and marks the email verified', async () => {
    const svc = make();
    const res = await svc.request(USER_ID, EMAIL);
    expect(res.devCode).toMatch(/^\d{6}$/);
    expect(email.send).toHaveBeenCalledWith(
      expect.objectContaining({ to: EMAIL }),
    );

    await expect(svc.verify(USER_ID, res.devCode!)).resolves.toBeUndefined();
    const user = users.rows.find((u) => u.id === USER_ID)!;
    expect(user.email).toBe(EMAIL);
    expect(user.emailVerifiedAt).toBeInstanceOf(Date);
  });

  it('rejects an email already used by another account', async () => {
    users.rows.push({ id: 'other', email: EMAIL, emailVerifiedAt: new Date() });
    const svc = make();
    await expect(svc.request(USER_ID, EMAIL)).rejects.toThrow(
      /already in use/i,
    );
  });

  it('rejects a consumed code (single use)', async () => {
    const svc = make();
    const { devCode } = await svc.request(USER_ID, EMAIL);
    await svc.verify(USER_ID, devCode!);
    await expect(svc.verify(USER_ID, devCode!)).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
  });

  it('locks after max attempts', async () => {
    const svc = make();
    const { devCode } = await svc.request(USER_ID, EMAIL);
    const wrong = devCode === '000000' ? '111111' : '000000';
    for (let i = 0; i < 3; i++) {
      await expect(svc.verify(USER_ID, wrong)).rejects.toThrow(/invalid/i);
    }
    await expect(svc.verify(USER_ID, devCode!)).rejects.toThrow(/too many/i);
  });

  it('rejects an expired code', async () => {
    const svc = make();
    const { devCode } = await svc.request(USER_ID, EMAIL);
    repo.rows[0].expiresAt = new Date(Date.now() - 1000);
    await expect(svc.verify(USER_ID, devCode!)).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
  });

  it('enforces the resend cooldown', async () => {
    const svc = make();
    await svc.request(USER_ID, EMAIL);
    await expect(svc.request(USER_ID, EMAIL)).rejects.toBeInstanceOf(
      HttpException,
    );
  });
});
