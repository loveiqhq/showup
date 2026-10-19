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

  /**
   * THE THREE REFUSALS MUST BE TELLABLE APART, and this is the test that would have caught it.
   *
   * Every one of them answered the same sentence -- "Invalid or expired code" -- so the app could
   * not tell a mistyped code from one that had simply aged out, and rendered both as "That code
   * doesn't match. Check your inbox." A user holding a CORRECT but expired code was told to look
   * in their inbox for the code they had just typed. Reported twice from a device.
   *
   * Asserted on `error`, which is the field the client matches on. The human `message` is
   * deliberately unchanged and deliberately NOT asserted here: it is the same for two of the
   * three, which is exactly why it cannot be the discriminator.
   */
  it('says WHICH refusal it is, so the client can say the right thing', async () => {
    const reasonOf = async (fn: () => Promise<unknown>) => {
      try {
        await fn();
        throw new Error('expected a refusal');
      } catch (e) {
        return (e as UnauthorizedException).getResponse() as { error?: string };
      }
    };

    // A USER EACH. The repo is shared across `make()` inside one test, and the resend cooldown
    // is per user -- so reusing one id would refuse the second request rather than the code, and
    // the test would be measuring the cooldown it did not mean to exercise.
    const svc = make();

    // Wrong code.
    await svc.request('u-mismatch', EMAIL);
    expect(
      (await reasonOf(() => svc.verify('u-mismatch', '000000'))).error,
    ).toBe('otp_mismatch');

    // Aged out.
    const expired = await svc.request('u-expired', 'expired@example.com');
    const expiredRow = repo.rows.find((r) => r.userId === 'u-expired');
    expiredRow!.expiresAt = new Date(Date.now() - 1000);
    expect(
      (await reasonOf(() => svc.verify('u-expired', expired.devCode!))).error,
    ).toBe('otp_expired');

    // No challenge at all -- a restart, or a code already consumed. Indistinguishable from
    // expired to the user, and treated as such: both need a new code.
    expect((await reasonOf(() => svc.verify('u-none', '000000'))).error).toBe(
      'otp_expired',
    );

    // The cap.
    const capped = await svc.request('u-capped', 'capped@example.com');
    for (let i = 0; i < 5; i += 1) {
      await reasonOf(() => svc.verify('u-capped', '000000'));
    }
    expect(
      (await reasonOf(() => svc.verify('u-capped', capped.devCode!))).error,
    ).toBe('otp_too_many');
  });

  it('enforces the resend cooldown', async () => {
    const svc = make();
    await svc.request(USER_ID, EMAIL);
    await expect(svc.request(USER_ID, EMAIL)).rejects.toBeInstanceOf(
      HttpException,
    );
  });
});
