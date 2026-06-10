import { HttpException, UnauthorizedException } from '@nestjs/common';

import { OtpService } from './otp.service';

interface Row {
  id?: string;
  phone: string;
  codeHash: string;
  expiresAt: Date;
  attempts: number;
  consumedAt: Date | null;
  createdAt?: Date;
}

class FakeRepo {
  rows: Row[] = [];
  private seq = 1;

  private matches(r: Row, c: any): boolean {
    if (c.id !== undefined) return r.id === c.id;
    if (c.phone !== undefined && r.phone !== c.phone) return false;
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

const config = (over: Record<string, unknown> = {}) =>
  ({
    get: (k: string) =>
      ({
        'auth.otpResendCooldownSeconds': 60,
        'auth.otpLength': 4,
        'auth.otpTtlSeconds': 300,
        'auth.otpMaxAttempts': 3,
        'auth.exposeOtp': true,
        'auth.jwtSecret': 'test-secret-1234567890',
        ...over,
      })[k],
  }) as any;

const PHONE = '+491701234567';

describe('OtpService', () => {
  let repo: FakeRepo;
  let sms: { sendVerificationCode: jest.Mock };

  const make = (over: Record<string, unknown> = {}) =>
    new OtpService(repo as any, config(over), sms);

  beforeEach(() => {
    repo = new FakeRepo();
    sms = { sendVerificationCode: jest.fn().mockResolvedValue(undefined) };
  });

  it('issues a code, sends it, and verifies it', async () => {
    const svc = make();
    const res = await svc.request(PHONE);
    expect(res.devCode).toMatch(/^\d{4}$/);
    expect(sms.sendVerificationCode).toHaveBeenCalledWith(PHONE, res.devCode);
    await expect(svc.verify(PHONE, res.devCode!)).resolves.toBeUndefined();
  });

  it('rejects a consumed code (single use)', async () => {
    const svc = make();
    const { devCode } = await svc.request(PHONE);
    await svc.verify(PHONE, devCode!);
    await expect(svc.verify(PHONE, devCode!)).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
  });

  it('locks after max attempts', async () => {
    const svc = make();
    const { devCode } = await svc.request(PHONE);
    const wrong = devCode === '0000' ? '1111' : '0000';
    for (let i = 0; i < 3; i++) {
      await expect(svc.verify(PHONE, wrong)).rejects.toThrow(/Invalid/i);
    }
    // 4th attempt — even with the correct code — is locked out.
    await expect(svc.verify(PHONE, devCode!)).rejects.toThrow(/too many/i);
  });

  it('rejects an expired code', async () => {
    const svc = make();
    const { devCode } = await svc.request(PHONE);
    repo.rows[0].expiresAt = new Date(Date.now() - 1000);
    await expect(svc.verify(PHONE, devCode!)).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
  });

  it('enforces the resend cooldown', async () => {
    const svc = make();
    await svc.request(PHONE);
    await expect(svc.request(PHONE)).rejects.toBeInstanceOf(HttpException);
  });
});
